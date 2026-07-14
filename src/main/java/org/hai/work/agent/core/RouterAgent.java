package org.hai.work.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.context.AgentContext;
import org.hai.work.skill.SkillRegistry;
import org.hai.work.tool.ToolRegistry;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

/**
 * 路由 Agent（向后兼容）
 * <p>
 * 严格按照用户要求提供帮助，不主动扩展。
 * <p>
 * 注意：新架构中，路由逻辑已迁移到 Orchestrator。
 * 此类保留用于向后兼容。
 */
@Slf4j
@Component
public class RouterAgent extends AbstractAgent {

    public RouterAgent(DeepSeekChatModel chatModel,
                       ToolCallbackProvider toolCallbackProvider,
                       MessageWindowChatMemory chatMemory,
                       SkillRegistry skillRegistry,
                       ToolRegistry toolRegistry) {
        super(chatModel, toolCallbackProvider, chatMemory, skillRegistry, toolRegistry);
    }

    @Override
    public String name() {
        return "router-agent";
    }

    @Override
    public String description() {
        return "路由 Agent（向后兼容），新代码应使用 Orchestrator。";
    }

    @Override
    protected String systemPrompt() {
        return """
                You are a request router.

                Your job is to analyze the user's intent and provide a helpful response.

                Strict rules:
                - Only respond to what the user explicitly asks
                - Do not suggest additional actions unless the user asks
                - Do not expand the scope of the request

                When you see potential improvements, put them in the 【建议】 section.
                Never execute suggestions automatically.

                Output language: respond in the same language as the user.
                """;
    }
}
