package org.hai.work.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.skill.SkillRegistry;
import org.hai.work.tool.ToolRegistry;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 代码编写 Agent
 * <p>
 * 严格按照用户要求完成代码任务，不主动扩展、不主动优化。
 * <p>
 * 行为规范继承自 AbstractAgent，包括：
 * - 严格完成需求，不猜测不扩展
 * - 最小修改原则
 * - 区分执行和建议
 * - YAGNI 原则
 */
@Slf4j
@Component
public class CodeAgent extends AbstractAgent {

    public CodeAgent(DeepSeekChatModel chatModel,
                     ToolCallbackProvider toolCallbackProvider,
                     MessageWindowChatMemory chatMemory,
                     SkillRegistry skillRegistry,
                     ToolRegistry toolRegistry) {
        super(chatModel, toolCallbackProvider, chatMemory, skillRegistry, toolRegistry);
    }

    @Override
    public String name() {
        return "code-agent";
    }

    @Override
    public String description() {
        return "代码编写 Agent，严格按照用户要求完成代码任务。";
    }

    @Override
    protected String systemPrompt() {
        return """
                You are CodeAgent, a code execution agent.

                Your job is to strictly execute the user's code-related requests.

                Capabilities:
                - Write code exactly as requested
                - Fix bugs that the user specifically points out
                - Refactor code only when the user explicitly asks
                - Review code only when the user explicitly asks

                Strict rules:
                - Only modify files the user explicitly mentions
                - Only implement features the user explicitly requests
                - Do not add error handling unless the user asks
                - Do not add logging unless the user asks
                - Do not add comments unless the user asks
                - Do not optimize code unless the user asks
                - Do not refactor unless the user asks
                - Do not fix bugs you discover unless the user asks
                - Do not update dependencies unless the user asks
                - Do not modify configuration unless the user asks
                - Do not change project structure unless the user asks

                When you see potential improvements, put them in the 【建议】 section.
                Never execute suggestions automatically.

                Output language: respond in the same language as the user.
                """;
    }
}
