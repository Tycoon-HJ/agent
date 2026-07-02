package org.hai.work.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

/**
 * 代码编写 Agent
 * <p>
 * 专注于代码相关任务：
 * - 代码生成：根据需求描述生成完整代码
 * - 代码审查：分析代码质量、发现潜在问题
 * - 代码重构：优化代码结构、提升可读性
 * - Bug 调试：分析错误信息、定位问题原因
 * - 技术方案：设计架构、选择技术栈、给出实现建议
 * <p>
 * 继承 AbstractAgent 后自动拥有：
 * - LLM 对话能力（通过 ChatClient）
 * - 会话记忆能力（通过 MessageWindowChatMemory，记住对话上下文）
 * - 工具调用能力（预留，未来可添加代码执行、文件操作等工具）
 */
@Slf4j
@Component
public class CodeAgent extends AbstractAgent {

    /**
     * 构造函数，Spring 自动注入所有依赖
     *
     * @param chatModel            DeepSeek 大模型
     * @param toolCallbackProvider 工具回调（预留，未来可添加代码相关工具）
     * @param chatMemory           会话记忆（按 sessionId 隔离对话历史）
     */
    public CodeAgent(DeepSeekChatModel chatModel,
                     ToolCallbackProvider toolCallbackProvider,
                     MessageWindowChatMemory chatMemory) {
        super(chatModel, toolCallbackProvider, chatMemory);
    }

    @Override
    protected String name() {
        return "code-agent";
    }

    @Override
    protected String description() {
        return "A professional code writing agent that helps with code generation, debugging, review, refactoring, and technical design.";
    }

    /**
     * 系统提示词（System Prompt）
     * <p>
     * 定义 CodeAgent 的角色和行为规则：
     * - 角色：资深全栈工程师，精通多种编程语言和框架
     * - 能力：代码生成、审查、重构、调试、架构设计
     * - 规则：代码质量要求、安全意识、最佳实践
     * - 输出：代码块 + 解释说明，结构清晰
     */
    @Override
    protected String systemPrompt() {
        return """
                You are CodeAgent, a senior full-stack software engineer with deep expertise across multiple programming languages, frameworks, and architectural patterns.
                
                Your core capabilities:
                1. Code Generation: Write clean, production-ready code based on requirements
                2. Code Review: Analyze code for bugs, security issues, performance problems, and style violations
                3. Code Refactoring: Improve code structure, readability, and maintainability
                4. Bug Debugging: Analyze error messages, identify root causes, and provide fixes
                5. Technical Design: Design architectures, choose tech stacks, and plan implementations
                
                Rules:
                - Always provide complete, runnable code (not partial snippets unless requested)
                - Include proper error handling, input validation, and edge case coverage
                - Follow language-specific best practices and conventions (e.g., Java naming, Pythonic style)
                - Add clear comments for complex logic
                - If the request is ambiguous, state your assumptions before coding
                - For debugging: explain the root cause first, then provide the fix
                - For architecture: list trade-offs and alternatives when relevant
                - Respond in the same language as the user (Chinese if user writes Chinese)
                - Use markdown code blocks with language tags for all code output
                
                Output format:
                - For code generation: brief explanation → code block → usage notes
                - For code review: issues found (severity: high/medium/low) → suggested fixes
                - For debugging: root cause analysis → fix → explanation
                - For design: approach overview → key components → code structure
                """;
    }

    /**
     * 重写用户提示词构建方法
     * <p>
     * CodeAgent 的输出格式与 WeatherAgent 不同：
     * - WeatherAgent：分析 → 数据 → 结论 → 建议（适合信息查询类任务）
     * - CodeAgent：直接描述需求，让 LLM 自由发挥（适合代码生成类任务）
     * <p>
     * 代码任务不需要固定的输出模板，LLM 会根据任务类型自动选择合适的格式。
     *
     * @param userInput 用户原始输入
     * @return 代码任务专用的用户提示词
     */
    @Override
    protected String buildUserPrompt(String userInput) {
        return """
                用户需求: %s
                
                请根据需求提供完整的解决方案。如果涉及代码，请使用 markdown 代码块输出。
                """.formatted(userInput);
    }
}
