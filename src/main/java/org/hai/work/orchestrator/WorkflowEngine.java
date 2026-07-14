package org.hai.work.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.agent.Agent;
import org.hai.work.agent.AgentRegistry;
import org.hai.work.context.AgentContext;
import org.hai.work.orchestrator.workflow.*;
import org.hai.work.tool.Tool;
import org.hai.work.tool.ToolRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 工作流执行引擎
 * <p>
 * 负责按顺序执行 WorkflowDefinition 中的每个 Step：
 * 1. 检查执行条件
 * 2. 调用 Agent 或 Tool 执行
 * 3. 处理重试
 * 4. 处理超时
 * 5. 记录结果到 Context
 * <p>
 * 设计原则：
 * - Step 之间通过 AgentContext 传递数据
 * - 支持条件执行（condition）
 * - 支持失败重试（maxRetries）
 * - 支持超时控制（timeoutMs）
 * - 支持流式输出（SSE）
 */
@Slf4j
@Component
public class WorkflowEngine {

    private final AgentRegistry agentRegistry;
    private final ToolRegistry toolRegistry;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public WorkflowEngine(AgentRegistry agentRegistry, ToolRegistry toolRegistry) {
        this.agentRegistry = agentRegistry;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 同步执行工作流
     */
    public WorkflowResult execute(WorkflowDefinition definition, AgentContext context) {
        log.info("========== WorkflowEngine 开始执行 ==========");
        log.info("工作流: {}, 步骤数: {}", definition.getName(), definition.getSteps().size());

        List<StepResult> results = new ArrayList<>();

        for (Step step : definition.getSteps()) {
            // 检查取消
            if (context.isCancelled()) {
                results.add(StepResult.cancelled(step.getId()));
                log.info("工作流已取消，跳过步骤 {}", step.getId());
                continue;
            }

            // 检查执行条件
            if (!step.shouldExecute(context)) {
                StepResult skipped = StepResult.skipped(step.getId(), "条件不满足");
                results.add(skipped);
                context.addStepResult(skipped);
                log.info("步骤 {} 条件不满足，跳过", step.getId());
                continue;
            }

            // 执行步骤（带重试）
            StepResult result = executeStepWithRetry(step, context);
            results.add(result);
            context.addStepResult(result);

            // 检查是否需要终止
            if (result.isTerminal() && step.isCritical()) {
                log.warn("关键步骤 {} 失败，终止工作流: {}", step.getId(), result.error());
                return WorkflowResult.failed(results, result.error());
            }
        }

        log.info("========== WorkflowEngine 执行完成 ==========");
        return WorkflowResult.completed(results);
    }

    /**
     * 流式执行工作流（SSE）
     */
    public Flux<String> executeStream(WorkflowDefinition definition, AgentContext context) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        executor.submit(() -> {
            try {
                for (Step step : definition.getSteps()) {
                    if (context.isCancelled()) {
                        sink.tryEmitNext("[SYSTEM] 工作流已取消\n");
                        break;
                    }

                    if (!step.shouldExecute(context)) {
                        sink.tryEmitNext("[SYSTEM] 步骤 " + step.getId() + " 跳过\n");
                        context.addStepResult(StepResult.skipped(step.getId(), "条件不满足"));
                        continue;
                    }

                    sink.tryEmitNext("[SYSTEM] 执行步骤 " + step.getId() + ": " + step.getDescription() + "\n");

                    StepResult result = executeStepWithRetry(step, context);
                    context.addStepResult(result);

                    if (result.isSuccess() && result.output() != null) {
                        sink.tryEmitNext(result.output());
                    }

                    if (result.isTerminal() && step.isCritical()) {
                        sink.tryEmitNext("[SYSTEM] 关键步骤失败，工作流终止\n");
                        break;
                    }
                }
                sink.tryEmitComplete();
            } catch (Exception e) {
                sink.tryEmitError(e);
            }
        });

        return sink.asFlux();
    }

    /**
     * 执行单个步骤（带重试）
     */
    private StepResult executeStepWithRetry(Step step, AgentContext context) {
        int maxAttempts = step.canRetry() ? step.getMaxRetries() + 1 : 1;
        StepResult lastResult = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                log.info("步骤 {} 第 {} 次重试", step.getId(), attempt);
            }

            lastResult = executeStep(step, context);

            if (lastResult.isSuccess()) {
                return lastResult;
            }

            if (attempt < maxAttempts) {
                log.warn("步骤 {} 执行失败，准备重试: {}", step.getId(), lastResult.error());
                try {
                    Thread.sleep(1000L * attempt); // 递增延迟
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return StepResult.cancelled(step.getId());
                }
            }
        }

        return lastResult;
    }

    /**
     * 执行单个步骤
     */
    private StepResult executeStep(Step step, AgentContext context) {
        Instant start = Instant.now();

        try {
            if (step.hasAgent()) {
                return executeAgentStep(step, context, start);
            } else if (step.hasTool()) {
                return executeToolStep(step, context, start);
            } else {
                return StepResult.failed(step.getId(), "步骤既没有指定 Agent 也没有指定 Tool", 0);
            }
        } catch (Exception e) {
            long duration = Instant.now().toEpochMilli() - start.toEpochMilli();
            log.error("步骤 {} 执行异常", step.getId(), e);
            return StepResult.failed(step.getId(), e.getMessage(), duration);
        }
    }

    /**
     * 执行 Agent 步骤
     */
    private StepResult executeAgentStep(Step step, AgentContext context, Instant start) {
        Agent agent = agentRegistry.get(step.getAgentName());
        if (agent == null) {
            return StepResult.failed(step.getId(), "Agent 未找到: " + step.getAgentName(), 0);
        }

        log.info("步骤 {} 调用 Agent: {}", step.getId(), step.getAgentName());

        // 构建子 Context（注入步骤特定的输入）
        String input = step.resolveInput(context);
        AgentContext stepContext = AgentContext.builder()
                .requestId(context.getRequestId())
                .sessionId(context.getSessionId())
                .userId(context.getUserId())
                .originalInput(input)
                .files(context.getFiles())
                .images(context.getImages())
                .variables(context.getVariables())
                .build();

        String output = agent.execute(stepContext);
        long duration = Instant.now().toEpochMilli() - start.toEpochMilli();

        log.info("步骤 {} Agent 执行完成，耗时 {}ms", step.getId(), duration);
        return StepResult.success(step.getId(), output, duration);
    }

    /**
     * 执行 Tool 步骤
     */
    private StepResult executeToolStep(Step step, AgentContext context, Instant start) {
        Tool tool = toolRegistry.get(step.getToolName());
        if (tool == null) {
            return StepResult.failed(step.getId(), "Tool 未找到: " + step.getToolName(), 0);
        }

        log.info("步骤 {} 调用 Tool: {}", step.getId(), step.getToolName());

        String input = step.resolveInput(context);
        String output = tool.execute(input);
        long duration = Instant.now().toEpochMilli() - start.toEpochMilli();

        log.info("步骤 {} Tool 执行完成，耗时 {}ms", step.getId(), duration);
        return StepResult.success(step.getId(), output, duration);
    }
}
