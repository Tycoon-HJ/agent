package org.hai.work.service;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.agent.dto.AgentRequest;
import org.hai.work.agent.dto.AgentResponse;
import org.hai.work.orchestrator.Orchestrator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时任务管理器
 * <p>
 * 负责定时任务的创建、取消、查询。
 * 任务触发时通过 RouterAgent 执行用户指定的任务描述，
 * 走正常的 Agent 路由链路（可使用所有已注册的 Agent 能力）。
 */
@Slf4j
@Service
public class ScheduledTaskManager {

    private final TaskScheduler taskScheduler;
    private final Orchestrator orchestrator;
    private final NotificationService notificationService;

    /**
     * 活跃任务存储（taskId → ScheduledFuture）
     */
    private final Map<String, ScheduledFuture<?>> activeTasks = new ConcurrentHashMap<>();

    /**
     * 任务元信息存储（taskId → TaskInfo）
     */
    private final Map<String, TaskInfo> taskInfoMap = new ConcurrentHashMap<>();

    public ScheduledTaskManager(@Qualifier("taskScheduler") TaskScheduler taskScheduler,
                                @Lazy Orchestrator orchestrator,
                                NotificationService notificationService) {
        this.taskScheduler = taskScheduler;
        this.orchestrator = orchestrator;
        this.notificationService = notificationService;
        log.info("ScheduledTaskManager 初始化完成");
    }

    /**
     * 任务元信息
     */
    public record TaskInfo(String taskId, String description, String scheduleType,
                           String scheduleValue, String createdAt, String status,
                           String userId) {

        @Override
        public String toString() {
            return """
                    - 任务ID: %s
                      描述: %s
                      调度类型: %s
                      调度值: %s
                      创建时间: %s
                      状态: %s""".formatted(taskId, description, scheduleType, scheduleValue, createdAt, status);
        }
    }

    /**
     * 创建一次性延迟任务
     *
     * @param taskId          任务ID（由 LLM 生成，需唯一）
     * @param delaySeconds    延迟秒数
     * @param taskDescription 任务描述（将交给 RouterAgent 执行）
     * @param sessionId       会话ID（任务执行时使用）
     * @param userId          用户ID（用于推送通知）
     * @return 操作结果描述
     */
    public String scheduleOnce(String taskId, long delaySeconds, String taskDescription, String sessionId, String userId) {
        if (activeTasks.containsKey(taskId)) {
            return "任务ID [" + taskId + "] 已存在，请使用不同的任务ID。";
        }

        Instant executionTime = Instant.now().plusSeconds(delaySeconds);
        String executionTimeStr = LocalDateTime.ofInstant(executionTime, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        Runnable task = () -> executeTask(taskId, taskDescription, sessionId, userId);
        ScheduledFuture<?> future = taskScheduler.schedule(task, executionTime);

        activeTasks.put(taskId, future);
        taskInfoMap.put(taskId, new TaskInfo(taskId, taskDescription, "once",
                "延迟 " + delaySeconds + " 秒", LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), "active", userId));

        log.info("创建一次性任务: taskId={}, delay={}s, 执行时间={}, userId={}", taskId, delaySeconds, executionTimeStr, userId);
        return "✅ 定时任务创建成功！\n- 任务ID: %s\n- 任务描述: %s\n- 预计执行时间: %s\n\n如需取消，请说取消任务 %s"
                .formatted(taskId, taskDescription, executionTimeStr, taskId);
    }

    /**
     * 创建 Cron 周期任务
     *
     * @param taskId          任务ID
     * @param cronExpression  Cron 表达式（如 "0 0 19 * * ?"）
     * @param taskDescription 任务描述
     * @param sessionId       会话ID
     * @param userId          用户ID（用于推送通知）
     * @return 操作结果描述
     */
    public String scheduleCron(String taskId, String cronExpression, String taskDescription, String sessionId, String userId) {
        if (activeTasks.containsKey(taskId)) {
            return "任务ID [" + taskId + "] 已存在，请使用不同的任务ID。";
        }

        try {
            Runnable task = () -> executeTask(taskId, taskDescription, sessionId, userId);
            ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(cronExpression));

            activeTasks.put(taskId, future);
            taskInfoMap.put(taskId, new TaskInfo(taskId, taskDescription, "cron",
                    cronExpression, LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), "active", userId));

            log.info("创建 Cron 任务: taskId={}, cron={}, userId={}", taskId, cronExpression, userId);
            return "✅ 周期定时任务创建成功！\n- 任务ID: %s\n- 任务描述: %s\n- Cron 表达式: %s\n\n如需取消，请说取消任务 %s"
                    .formatted(taskId, taskDescription, cronExpression, taskId);
        } catch (IllegalArgumentException e) {
            log.error("无效的 Cron 表达式: {}", cronExpression, e);
            return "❌ 无效的 Cron 表达式: " + cronExpression + "，请检查格式。";
        }
    }

    /**
     * 取消指定任务
     *
     * @param taskId 任务ID
     * @return 操作结果描述
     */
    public String cancelTask(String taskId) {
        ScheduledFuture<?> future = activeTasks.get(taskId);
        if (future == null) {
            return "❌ 未找到任务ID [" + taskId + "]，请检查任务ID是否正确。可用的任务ID: "
                    + activeTasks.keySet();
        }

        future.cancel(false);
        activeTasks.remove(taskId);
        taskInfoMap.computeIfPresent(taskId, (k, v) -> new TaskInfo(
                v.taskId(), v.description(), v.scheduleType(),
                v.scheduleValue(), v.createdAt(), "cancelled", v.userId));

        log.info("取消任务: taskId={}", taskId);
        return "✅ 任务 [" + taskId + "] 已成功取消。";
    }

    /**
     * 列出所有活跃任务
     *
     * @return 任务列表描述
     */
    public String listTasks() {
        if (taskInfoMap.isEmpty()) {
            return "📋 当前没有任何定时任务。";
        }

        StringBuilder sb = new StringBuilder("📋 定时任务列表：\n");
        for (TaskInfo info : taskInfoMap.values()) {
            sb.append(info.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 执行定时任务
     * <p>
     * 任务触发时调用 RouterAgent 执行用户指定的任务描述，
     * 走正常的 Agent 路由链路。执行完毕后通过 NotificationService 推送结果给前端。
     *
     * @param taskId          任务ID
     * @param taskDescription 任务描述
     * @param sessionId       会话ID
     * @param userId          用户ID（用于推送通知）
     */
    private void executeTask(String taskId, String taskDescription, String sessionId, String userId) {
        log.info("========== 定时任务触发: taskId={}, userId={} ==========", taskId, userId);
        log.info("任务描述: {}", taskDescription);

        try {
            AgentRequest request = new AgentRequest();
            request.setInput(taskDescription);
            request.setSessionId("scheduled-" + taskId);
            request.setUserId(userId);

            AgentResponse response = orchestrator.executeSimple(request);
            String answer = response.getAnswer();
            log.info("定时任务执行完成: taskId={}, 结果: {}", taskId, answer);

            // 推送结果给前端
            notificationService.sendNotification(userId, taskId,
                    answer != null ? answer : "任务执行完成，但未返回结果。");

            // 一次性任务执行后自动清理
            ScheduledFuture<?> future = activeTasks.get(taskId);
            if (future != null && future.isDone()) {
                activeTasks.remove(taskId);
                taskInfoMap.computeIfPresent(taskId, (k, v) -> new TaskInfo(
                        v.taskId(), v.description(), v.scheduleType(),
                        v.scheduleValue(), v.createdAt(), "completed", v.userId()));
                log.info("一次性任务已自动清理: taskId={}", taskId);
            }
        } catch (Exception e) {
            log.error("定时任务执行异常: taskId={}", taskId, e);
            // 异常时也推送通知
            notificationService.sendNotification(userId, taskId,
                    "❌ 定时任务执行异常: " + e.getMessage());
        }

        log.info("========== 定时任务结束: taskId={} ==========", taskId);
    }
}
