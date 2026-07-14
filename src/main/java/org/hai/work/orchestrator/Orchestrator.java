package org.hai.work.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.agent.Agent;
import org.hai.work.agent.AgentRegistry;
import org.hai.work.agent.dto.AgentRequest;
import org.hai.work.agent.dto.AgentResponse;
import org.hai.work.context.AgentContext;
import org.hai.work.orchestrator.confirmation.ConfirmationStore;
import org.hai.work.orchestrator.confirmation.PendingConfirmation;
import org.hai.work.orchestrator.workflow.WorkflowDefinition;
import org.hai.work.orchestrator.workflow.WorkflowResult;
import org.hai.work.skill.SkillRegistry;
import org.hai.work.tool.ToolRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.regex.Pattern;

/**
 * 编排器（统一调度中心）
 * <p>
 * 职责：
 * 1. 判断任务复杂度（简单 vs 复杂）
 * 2. 简单任务：直接路由到单个 Agent
 * 3. 复杂任务：调用 Planner 规划 → WorkflowEngine 执行
 * 4. 管理 AgentContext 的生命周期
 * 5. 管理异常、重试、超时
 * 6. 管理待确认状态
 */
@Slf4j
@Component
public class Orchestrator {

    /**
     * 待确认标记前缀
     * Agent 返回此标记时，表示需要用户确认
     */
    public static final String PENDING_CONFIRMATION_MARKER = "[PENDING_CONFIRMATION]";

    private final AgentRegistry agentRegistry;
    private final ToolRegistry toolRegistry;
    private final SkillRegistry skillRegistry;
    private final Planner planner;
    private final WorkflowEngine workflowEngine;
    private final ConfirmationStore confirmationStore;

    /**
     * 复杂任务关键词模式
     */
    private static final Pattern COMPLEX_TASK_PATTERN = Pattern.compile(
            "(然后|接着|之后|最后|并且|同时|如果.*就|失败.*修复|运行.*测试|提交.*Git|分析.*生成|生成.*运行|运行.*修复)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 待确认消息模式
     * 匹配 Agent 返回的待确认消息格式
     */
    private static final Pattern CONFIRMATION_PATTERN = Pattern.compile(
            "\\[PENDING_CONFIRMATION]\\s*\\n确认事项：(.+?)\\n待执行操作：(.+?)\\n已完成工作：([\\s\\S]*?)(?=\\n---|$)",
            Pattern.CASE_INSENSITIVE
    );

    public Orchestrator(AgentRegistry agentRegistry,
                        ToolRegistry toolRegistry,
                        SkillRegistry skillRegistry,
                        Planner planner,
                        WorkflowEngine workflowEngine,
                        ConfirmationStore confirmationStore) {
        this.agentRegistry = agentRegistry;
        this.toolRegistry = toolRegistry;
        this.skillRegistry = skillRegistry;
        this.planner = planner;
        this.workflowEngine = workflowEngine;
        this.confirmationStore = confirmationStore;
    }

    /**
     * 判断任务是否为复杂任务
     */
    public boolean isComplexTask(String input) {
        if (input == null || input.isBlank()) return false;
        return COMPLEX_TASK_PATTERN.matcher(input).find();
    }

    /**
     * 执行简单任务（单 Agent）
     */
    public AgentResponse executeSimple(AgentRequest request) {
        log.info("Orchestrator 执行简单任务, skill={}", request.getSkill());

        AgentContext context = createContext(request);
        Agent agent = selectAgent(request);

        if (agent == null) {
            AgentResponse response = new AgentResponse();
            response.setAnswer("抱歉，没有找到合适的 Agent 处理您的请求。");
            return response;
        }

        String output = agent.execute(context);

        // 检查是否需要确认
        if (output != null && output.contains(PENDING_CONFIRMATION_MARKER)) {
            return handlePendingConfirmation(context, agent.name(), output);
        }

        AgentResponse response = new AgentResponse();
        response.setAnswer(output);
        return response;
    }

    /**
     * 流式执行简单任务
     */
    public Flux<String> executeSimpleStream(AgentRequest request) {
        log.info("Orchestrator 流式执行简单任务, skill={}", request.getSkill());

        AgentContext context = createContext(request);
        Agent agent = selectAgent(request);

        if (agent == null) {
            return Flux.just("抱歉，没有找到合适的 Agent 处理您的请求。");
        }

        return agent.executeStream(context);
    }

    /**
     * 执行复杂任务（Planner → Workflow）
     */
    public AgentResponse executeComplex(AgentRequest request) {
        log.info("Orchestrator 执行复杂任务");

        AgentContext context = createContext(request);

        // 1. Planner 生成执行计划
        WorkflowDefinition plan = planner.plan(request.getInput());

        // 2. WorkflowEngine 执行计划
        WorkflowResult result = workflowEngine.execute(plan, context);

        // 3. 汇总结果
        AgentResponse response = new AgentResponse();
        response.setAnswer(result.getFinalOutput());
        return response;
    }

    /**
     * 流式执行复杂任务
     */
    public Flux<String> executeComplexStream(AgentRequest request) {
        log.info("Orchestrator 流式执行复杂任务");

        AgentContext context = createContext(request);
        WorkflowDefinition plan = planner.plan(request.getInput());
        return workflowEngine.executeStream(plan, context);
    }

    /**
     * 处理待确认响应
     * <p>
     * 解析 Agent 返回的待确认消息，保存执行状态，返回确认提示。
     */
    public AgentResponse handlePendingConfirmation(AgentContext context, String agentName, String output) {
        // 解析待确认消息
        String confirmationMessage = "需要确认操作";
        String pendingAction = "继续执行";
        String partialResult = "";

        java.util.regex.Matcher matcher = CONFIRMATION_PATTERN.matcher(output);
        if (matcher.find()) {
            confirmationMessage = matcher.group(1).trim();
            pendingAction = matcher.group(2).trim();
            partialResult = matcher.group(3).trim();
        } else {
            // 如果格式不匹配，尝试简单解析
            int markerIndex = output.indexOf(PENDING_CONFIRMATION_MARKER);
            if (markerIndex >= 0) {
                partialResult = output.substring(0, markerIndex).trim();
            }
            String afterMarker = output.substring(markerIndex + PENDING_CONFIRMATION_MARKER.length()).trim();
            if (!afterMarker.isEmpty()) {
                confirmationMessage = afterMarker;
            }
        }

        // 保存待确认状态
        PendingConfirmation pending = PendingConfirmation.builder()
                .sessionId(context.getSessionId())
                .agentName(agentName)
                .savedContext(context)
                .partialResult(partialResult)
                .confirmationMessage(confirmationMessage)
                .pendingAction(pendingAction)
                .build();

        confirmationStore.save(pending);

        log.info("保存待确认状态: confirmationId={}, agent={}", pending.getConfirmationId(), agentName);

        // 返回待确认响应
        return AgentResponse.pendingConfirmation(
                pending.getConfirmationId(),
                confirmationMessage,
                partialResult,
                pendingAction
        );
    }

    /**
     * 确认后继续执行
     * <p>
     * 用户确认后，加载保存的状态，继续执行剩余任务。
     *
     * @param confirmationId 确认ID
     * @return 完整的执行结果
     */
    public AgentResponse continueAfterConfirmation(String confirmationId) {
        log.info("收到确认请求: confirmationId={}", confirmationId);

        // 加载待确认状态
        PendingConfirmation pending = confirmationStore.load(confirmationId)
                .orElseThrow(() -> new IllegalStateException("待确认状态不存在或已过期: " + confirmationId));

        AgentContext context = pending.getSavedContext();
        String agentName = pending.getAgentName();
        String partialResult = pending.getPartialResult();

        // 删除待确认状态（已确认）
        confirmationStore.remove(confirmationId);

        // 获取 Agent
        Agent agent = agentRegistry.get(agentName);
        if (agent == null) {
            AgentResponse response = new AgentResponse();
            response.setAnswer("错误：Agent 不存在: " + agentName);
            return response;
        }

        // 构建继续执行的输入
        // 将已确认的操作作为输入的一部分
        String continueInput = "用户已确认继续执行以下操作：\n\n" +
                pending.getPendingAction() + "\n\n" +
                "已完成的工作：\n" + partialResult + "\n\n" +
                "请继续执行剩余工作，并输出完整结果。";

        // 创建新的 Context 继续执行
        AgentContext continueContext = AgentContext.builder()
                .sessionId(context.getSessionId())
                .userId(context.getUserId())
                .originalInput(continueInput)
                .skill(context.getSkill())
                .files(context.getFiles())
                .images(context.getImages())
                .build();

        // 继续执行
        String output = agent.execute(continueContext);

        // 合并结果
        String finalResult = partialResult.isEmpty() ? output : partialResult + "\n\n---\n\n" + output;

        AgentResponse response = new AgentResponse();
        response.setAnswer(finalResult);
        return response;
    }

    /**
     * 创建 AgentContext
     */
    public AgentContext createContext(AgentRequest request) {
        log.info("创建 AgentContext, skill={}", request.getSkill());
        return AgentContext.builder()
                .sessionId(request.getSessionId())
                .userId(request.getUserId() != null ? request.getUserId() : "default-user")
                .originalInput(request.getInput())
                .skill(request.getSkill())
                .files(request.getFiles())
                .images(request.getImages())
                .build();
    }

    /**
     * 选择合适的 Agent（简单路由逻辑）
     */
    private Agent selectAgent(AgentRequest request) {
        String input = request.getInput();

        // 文件 → file-agent
        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            Agent agent = agentRegistry.get("file-agent");
            if (agent != null) return agent;
        }

        // 图片 → image-agent
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            Agent agent = agentRegistry.get("image-agent");
            if (agent != null) return agent;
        }

        // 关键词匹配 → schedule-agent（更精确的匹配，避免否定句误匹配）
        if (input != null && input.matches("^(?!.*(?:不需要|不要|取消|忘了|算了)).*(?:提醒我|设置提醒|定时|\\d+分钟后|\\d+小时后|明天.*点|后天.*点|每天|每周|每月).*")) {
            Agent agent = agentRegistry.get("schedule-agent");
            if (agent != null) return agent;
        }

        // 默认 → code-agent（通用能力）
        return agentRegistry.get("code-agent");
    }
}
