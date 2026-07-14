package org.hai.work.orchestrator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hai.work.agent.AgentRegistry;
import org.hai.work.orchestrator.workflow.Step;
import org.hai.work.orchestrator.workflow.WorkflowDefinition;
import org.hai.work.tool.ToolRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务规划器
 * <p>
 * 将用户的复杂任务分解为多个可执行的 Step。
 * 使用 LLM 分析任务，生成结构化的执行计划。
 * <p>
 * 输出：WorkflowDefinition（Step 列表 + 依赖关系 + 条件）
 */
@Slf4j
@Component
public class Planner {

    private final DeepSeekChatModel chatModel;
    private final AgentRegistry agentRegistry;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public Planner(DeepSeekChatModel chatModel,
                   AgentRegistry agentRegistry,
                   ToolRegistry toolRegistry,
                   ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.agentRegistry = agentRegistry;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * 为复杂任务生成执行计划
     */
    public WorkflowDefinition plan(String userInput) {
        log.info("Planner 开始规划任务: {}", userInput);

        String agentList = agentRegistry.getNames().stream()
                .filter(name -> !"router-agent".equals(name))
                .collect(Collectors.joining(", "));
        String toolList = String.join(", ", toolRegistry.getNames());

        String prompt = """
                You are a task planner. Your job is to decompose a complex task into a sequence of steps.

                Available Agents: %s
                Available Tools: %s

                Rules:
                1. Each step must use either an Agent OR a Tool (not both)
                2. Steps execute sequentially by default
                3. Use "condition" for conditional execution (e.g., "step3.failed")
                4. Use "dependsOn" to declare dependencies
                5. Use "critical: true" for steps where failure should stop the workflow
                6. Use "maxRetries" for steps that may need retrying
                7. Use ${stepN.output} in inputTemplate to reference previous step outputs
                8. Use ${originalInput} to reference the user's original request

                Output a JSON object with this structure:
                {
                  "name": "workflow name",
                  "description": "what this workflow does",
                  "steps": [
                    {
                      "id": 1,
                      "agent": "agent-name OR null",
                      "tool": "tool-name OR null",
                      "description": "what this step does",
                      "inputTemplate": "input for this step, can use ${stepN.output}",
                      "dependsOn": [],
                      "condition": null,
                      "maxRetries": 0,
                      "timeoutMs": 60000,
                      "critical": false
                    }
                  ]
                }

                IMPORTANT:
                - Output ONLY valid JSON, no markdown, no explanation
                - Either "agent" or "tool" must be set for each step (not both)
                - Agent names: %s
                - Tool names: %s

                User task: %s
                """.formatted(agentList, toolList, agentList, toolList, userInput);

        try {
            ChatClient client = ChatClient.builder(chatModel).build();
            String response = client.prompt()
                    .system("You are a task planner. Output only valid JSON.")
                    .user(prompt)
                    .call()
                    .chatResponse()
                    .getResult()
                    .getOutput()
                    .getText();

            log.info("Planner 原始响应: {}", response);

            // 清理响应（去掉可能的 markdown 代码块标记）
            String json = cleanJsonResponse(response);
            return parseWorkflowDefinition(json);

        } catch (Exception e) {
            log.error("Planner 规划失败", e);
            // 降级：创建单步工作流
            return createFallbackWorkflow(userInput);
        }
    }

    /**
     * 解析 JSON 为 WorkflowDefinition
     */
    private WorkflowDefinition parseWorkflowDefinition(String json) {
        try {
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {});
            String name = (String) root.getOrDefault("name", "unnamed-workflow");
            String description = (String) root.getOrDefault("description", "");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stepMaps = (List<Map<String, Object>>) root.get("steps");
            List<Step> steps = stepMaps.stream()
                    .map(this::mapToStep)
                    .toList();

            WorkflowDefinition definition = new WorkflowDefinition(name, description, steps);
            log.info("Planner 生成工作流: {}", definition);
            return definition;

        } catch (Exception e) {
            log.error("解析工作流定义失败", e);
            throw new RuntimeException("工作流定义解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将 Map 转换为 Step 对象
     */
    private Step mapToStep(Map<String, Object> map) {
        Step step = new Step();
        step.setId((Integer) map.get("id"));
        step.setAgentName((String) map.get("agent"));
        step.setToolName((String) map.get("tool"));
        step.setDescription((String) map.get("description"));
        step.setInputTemplate((String) map.get("inputTemplate"));
        step.setCondition((String) map.get("condition"));

        @SuppressWarnings("unchecked")
        List<Integer> dependsOn = (List<Integer>) map.get("dependsOn");
        step.setDependsOn(dependsOn != null ? dependsOn : List.of());

        Object maxRetries = map.get("maxRetries");
        step.setMaxRetries(maxRetries instanceof Number n ? n.intValue() : 0);

        Object timeoutMs = map.get("timeoutMs");
        step.setTimeoutMs(timeoutMs instanceof Number n ? n.longValue() : 60000);

        Object critical = map.get("critical");
        step.setCritical(critical instanceof Boolean b ? b : false);

        return step;
    }

    /**
     * 清理 JSON 响应（去掉 markdown 代码块标记等）
     */
    private String cleanJsonResponse(String response) {
        String trimmed = response.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    /**
     * 降级工作流：当 Planner 失败时，创建单步工作流直接调用 LLM
     */
    private WorkflowDefinition createFallbackWorkflow(String userInput) {
        log.warn("创建降级工作流");
        Step step = new Step();
        step.setId(1);
        step.setAgentName("code-agent");
        step.setDescription("直接回答用户问题");
        step.setCritical(true);

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setName("fallback-workflow");
        definition.setDescription("降级工作流：直接调用 Agent 回答");
        definition.addStep(step);
        return definition;
    }
}
