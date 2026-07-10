package org.hai.work.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hai.work.service.ScheduledTaskManager;
import org.hai.work.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * 定时任务工具
 * <p>
 * LLM 通过此工具管理定时任务，支持三种操作：
 * - create: 创建定时任务（支持一次性延迟执行和 Cron 周期执行）
 * - cancel: 取消指定定时任务
 * - list:   列出所有活跃的定时任务
 * <p>
 * 工作流程：
 * 1. 用户说"今晚7点帮我查日志" → ScheduleAgent 识别意图
 * 2. ScheduleAgent 调用 schedule_task 工具，参数为 {action:"create", ...}
 * 3. 本工具解析参数，委托 ScheduledTaskManager 创建任务
 * 4. 任务触发时，ScheduledTaskManager 调用 RouterAgent 执行任务
 */
@Slf4j
@Component
public class ScheduleTool implements Tool {

    private final ScheduledTaskManager taskManager;
    private final ObjectMapper objectMapper;

    public ScheduleTool(ScheduledTaskManager taskManager, ObjectMapper objectMapper) {
        this.taskManager = taskManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "schedule_task";
    }

    @Override
    public String description() {
        return """
                管理定时任务。支持三种操作：
                - create: 创建定时任务。参数：taskId(唯一ID), taskDescription(任务描述), sessionId(会话ID), 以及以下二选一：
                  - delaySeconds: 延迟执行的秒数（一次性任务）
                  - cron: Cron 表达式（周期任务，如 "0 0 19 * * ?" 表示每天19:00）
                - cancel: 取消定时任务。参数：taskId
                - list: 列出所有定时任务。无额外参数
                """;
    }

    @Override
    public String inputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "action": {
                      "type": "string",
                      "enum": ["create", "cancel", "list"],
                      "description": "操作类型：create=创建任务, cancel=取消任务, list=列出任务"
                    },
                    "taskId": {
                      "type": "string",
                      "description": "任务唯一标识ID，如 task-001"
                    },
                    "taskDescription": {
                      "type": "string",
                      "description": "任务描述，任务触发时将执行此内容"
                    },
                    "delaySeconds": {
                      "type": "integer",
                      "description": "延迟执行的秒数（一次性任务时使用）"
                    },
                    "cron": {
                      "type": "string",
                      "description": "Cron 表达式（周期任务时使用，如 '0 0 19 * * ?'）"
                    },
                    "sessionId": {
                      "type": "string",
                      "description": "会话ID，用于任务执行时的上下文"
                    },
                    "userId": {
                      "type": "string",
                      "description": "用户ID，用于任务完成后推送通知给该用户"
                    }
                  },
                  "required": ["action"]
                }
                """;
    }

    @Override
    public String execute(String args) {
        log.info("ScheduleTool 执行，参数: {}", args);

        try {
            JsonNode root = objectMapper.readTree(args);
            String action = root.get("action").asText();

            return switch (action) {
                case "create" -> handleCreate(root);
                case "cancel" -> handleCancel(root);
                case "list" -> taskManager.listTasks();
                default -> "❌ 未知操作: " + action + "，支持的操作: create, cancel, list";
            };
        } catch (Exception e) {
            log.error("ScheduleTool 执行异常", e);
            return "❌ 参数解析失败: " + e.getMessage();
        }
    }

    /**
     * 处理创建任务请求
     */
    private String handleCreate(JsonNode root) {
        String taskId = getTextOrDefault(root, "taskId", "task-" + System.currentTimeMillis());
        String taskDescription = getTextOrDefault(root, "taskDescription", "");
        String sessionId = getTextOrDefault(root, "sessionId", "default");
        String userId = getTextOrDefault(root, "userId", "default-user");

        if (taskDescription.isBlank()) {
            return "❌ 缺少任务描述(taskDescription)，请提供要执行的任务内容。";
        }

        // 优先使用 Cron 表达式
        if (root.has("cron") && !root.get("cron").asText().isBlank()) {
            String cron = root.get("cron").asText();
            return taskManager.scheduleCron(taskId, cron, taskDescription, sessionId, userId);
        }

        // 其次使用延迟执行
        if (root.has("delaySeconds")) {
            long delaySeconds = root.get("delaySeconds").asLong();
            if (delaySeconds <= 0) {
                return "❌ 延迟秒数必须大于0。";
            }
            return taskManager.scheduleOnce(taskId, delaySeconds, taskDescription, sessionId, userId);
        }

        return "❌ 缺少调度参数，请提供 cron（周期任务）或 delaySeconds（一次性任务）。";
    }

    /**
     * 处理取消任务请求
     */
    private String handleCancel(JsonNode root) {
        String taskId = getTextOrDefault(root, "taskId", "");
        if (taskId.isBlank()) {
            return "❌ 缺少任务ID(taskId)，请提供要取消的任务ID。当前活跃任务: "
                    + taskManager.listTasks();
        }
        return taskManager.cancelTask(taskId);
    }

    /**
     * 安全获取 JSON 字段值
     */
    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        if (node.has(field) && !node.get(field).asText().isBlank()) {
            return node.get(field).asText();
        }
        return defaultValue;
    }
}
