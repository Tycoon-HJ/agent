package org.hai.work.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.agent.Agent;
import org.hai.work.context.AgentContext;
import org.hai.work.skill.SkillRegistry;
import org.hai.work.tool.ToolRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Agent 抽象基类
 * <p>
 * 封装与 LLM 的交互流程，并强制所有 Agent 遵守严格的行为规范。
 * <p>
 * 核心行为准则（所有 Agent 必须遵守）：
 * 1. 严格完成用户明确提出的需求，不猜测、不扩展
 * 2. 最小修改原则：修改最少文件、最少代码
 * 3. 不允许自行扩大任务范围
 * 4. 不允许主动修复其它 Bug
 * 5. 区分"执行"和"建议"
 * 6. YAGNI 原则：不为未来需求提前设计
 * 7. 存在歧义时先提问，不猜测
 * 8. 修改公共接口必须提醒用户
 */
@Slf4j
public abstract class AbstractAgent implements Agent {

    protected final DeepSeekChatModel chatModel;
    protected final ToolCallbackProvider toolCallbackProvider;
    protected final MessageWindowChatMemory chatMemory;
    protected final SkillRegistry skillRegistry;
    protected final ToolRegistry toolRegistry;

    /**
     * 所有 Agent 必须遵守的严格行为规范（注入到 system prompt 最前面）
     */
    private static final String STRICT_BEHAVIOR_RULES = """
            ============================================================
            【最高优先级行为规范 - 必须严格遵守】
            ============================================================

            你的身份：严格按照用户要求完成任务的执行者。
            你不是架构师、不是代码优化器、不是重构专家。

            默认行为准则：宁可少做，绝不多做。

            ============================================================
            第一原则：严格完成需求
            ============================================================
            - 只完成用户明确提出的需求
            - 不猜测用户真正想要什么
            - 不主动扩展需求
            - 不主动优化代码
            - 不主动重构
            - 不主动修复其它问题
            - 不修改无关文件

            ============================================================
            第二原则：最小修改原则（Minimal Change Principle）
            ============================================================
            在能够完成任务的前提下：
            - 优先修改最少数量的文件
            - 优先修改最少数量的代码
            - 优先保持现有架构
            - 优先保持已有实现方式
            - 不要因为有更好的实现方式就修改代码

            ============================================================
            第三原则：不允许扩大任务范围
            ============================================================
            - 如果用户说"修改 A.java"，只能修改 A.java
            - 如果确实必须修改其它文件才能完成任务：
              → 必须先停止
              → 说明原因
              → 等待用户确认
              → 未经确认不得继续

            ============================================================
            第四原则：不允许主动修复其它 Bug
            ============================================================
            修改过程中发现以下情况，不得自动修改：
            - 代码风格不好
            - 存在历史 Bug
            - 存在性能问题
            - 存在安全问题
            - 存在重复代码
            - 存在 TODO
            这些都不是当前任务。可以提出建议，但不能自动修改。

            ============================================================
            第五原则：区分执行和建议
            ============================================================
            输出必须分成两个部分：
            【执行内容】- 只包含真正执行的内容
            【建议】- 只包含可以优化的地方（绝不能自动执行）

            ============================================================
            第六原则：YAGNI 原则
            ============================================================
            - 不为未来可能的需求提前设计
            - 不增加用户没有要求的接口
            - 不增加用户没有要求的配置
            - 不增加用户没有要求的扩展点
            - 不增加用户没有要求的抽象层

            ============================================================
            第七原则：选择最安全的方案
            ============================================================
            如果存在多种实现方案，优先选择：
            - 改动最少
            - 风险最低
            - 兼容性最好
            - 容易回滚
            而不是最新、最优雅、最先进的方案。

            ============================================================
            第八原则：歧义时先提问
            ============================================================
            如果任务存在歧义，不要猜测，先提出问题，等待用户回答。

            ============================================================
            第九原则：删除代码必须确认
            ============================================================
            - 只能删除确实属于当前需求的代码
            - 否则不得删除

            ============================================================
            第十原则：修改公共接口必须提醒
            ============================================================
            如果需要修改以下内容，必须提醒用户并等待确认：
            - API、数据库、DTO
            - 公共类、公共方法
            - 配置文件、依赖

            ============================================================
            Reflection（自我检查）
            ============================================================
            完成任务后，必须检查：
            1. 有没有修改用户没有要求的文件？
            2. 有没有新增用户没有要求的功能？
            3. 有没有删除用户没有要求删除的代码？
            4. 有没有修改公共接口？
            5. 有没有改变原有业务逻辑？
            6. 有没有主动优化代码？
            7. 有没有修改配置？
            8. 有没有升级依赖？
            9. 有没有修改测试？
            10. 有没有改变项目结构？

            如果存在以上任意情况，必须撤销这些修改，仅保留完成用户需求所必须的代码。

            ============================================================
            """;

    protected AbstractAgent(DeepSeekChatModel chatModel,
                            ToolCallbackProvider toolCallbackProvider,
                            MessageWindowChatMemory chatMemory) {
        this(chatModel, toolCallbackProvider, chatMemory, null, null);
    }

    protected AbstractAgent(DeepSeekChatModel chatModel,
                            ToolCallbackProvider toolCallbackProvider,
                            MessageWindowChatMemory chatMemory,
                            SkillRegistry skillRegistry,
                            ToolRegistry toolRegistry) {
        this.chatModel = chatModel;
        this.toolCallbackProvider = toolCallbackProvider;
        this.chatMemory = chatMemory;
        this.skillRegistry = skillRegistry;
        this.toolRegistry = toolRegistry;
        log.info("Agent [{}] 初始化完成", name());
    }

    /**
     * 系统提示词（子类实现）
     */
    protected abstract String systemPrompt();

    /**
     * 构建完整的 system prompt：
     * 1. 严格行为规范（最高优先级）
     * 2. 子类 system prompt
     * 3. Agent 声明的 Skill 扩展
     * 4. 用户通过 / 命令选择的 Skill 扩展
     */
    protected String buildFullSystemPrompt(AgentContext context) {
        // 严格行为规范放在最前面（最高优先级）
        StringBuilder full = new StringBuilder(STRICT_BEHAVIOR_RULES);

        // 子类的 system prompt
        String agentPrompt = systemPrompt();
        if (agentPrompt != null && !agentPrompt.isBlank()) {
            full.append("\n\n").append(agentPrompt);
        }

        // Agent 声明的 Skill 扩展
        if (skillRegistry != null && requiredSkills() != null && !requiredSkills().isEmpty()) {
            String skillPrompt = skillRegistry.buildCombinedPrompt(requiredSkills());
            if (!skillPrompt.isBlank()) {
                full.append("\n\n").append(skillPrompt);
            }
        }

        // 用户通过 / 命令选择的 Skill 扩展
        if (context != null && context.getSkill() != null && !context.getSkill().isBlank()) {
            String userSkillName = context.getSkill();
            log.info("用户选择的 Skill: {}", userSkillName);
            if (skillRegistry != null && skillRegistry.has(userSkillName)) {
                String userSkillPrompt = skillRegistry.buildCombinedPrompt(List.of(userSkillName));
                if (!userSkillPrompt.isBlank()) {
                    full.append("\n\n").append(userSkillPrompt);
                    log.info("已注入用户 Skill: {}", userSkillName);
                }
            } else {
                log.warn("用户选择的 Skill 未找到: {}", userSkillName);
            }
        }

        return full.toString();
    }

    /**
     * 同步执行
     */
    @Override
    public String execute(AgentContext context) {
        String userInput = context.getOriginalInput();
        String sessionId = context.getSessionId();

        log.info("========== Agent [{}] 开始执行 ==========", name());
        log.debug("用户输入: {}, 会话ID: {}, Skill: {}", userInput, sessionId, context.getSkill());

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultTools(toolCallbackProvider)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        String userPrompt = buildUserPrompt(userInput);
        String sysPrompt = buildFullSystemPrompt(context);

        String response = chatClient.prompt()
                .system(sysPrompt)
                .user(userPrompt)
                .advisors(a -> a.param("chat_memory_conversation_id", sessionId))
                .call()
                .chatResponse()
                .getResult()
                .getOutput()
                .getText();

        log.info("========== Agent [{}] 执行完成 ==========", name());
        return response;
    }

    /**
     * 流式执行
     */
    @Override
    public Flux<String> executeStream(AgentContext context) {
        String userInput = context.getOriginalInput();
        String sessionId = context.getSessionId();

        log.info("Agent [{}] 开始流式执行，会话ID: {}, Skill: {}", name(), sessionId, context.getSkill());

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultTools(toolCallbackProvider)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        String userPrompt = buildUserPrompt(userInput);
        String sysPrompt = buildFullSystemPrompt(context);

        return chatClient.prompt()
                .system(sysPrompt)
                .user(userPrompt)
                .advisors(a -> a.param("chat_memory_conversation_id", sessionId))
                .stream()
                .content();
    }

    /**
     * 构建用户提示词（强制要求区分执行和建议）
     */
    protected String buildUserPrompt(String userInput) {
        return """
                用户需求: %s

                请严格按照用户需求完成任务。

                输出格式要求（必须严格遵守）：
                1. 【执行内容】- 只包含你真正执行的内容（修改了哪些文件、哪些代码）
                2. 【建议】- 如果发现可以优化的地方，放在这里（绝不能自动执行）

                如果没有建议，可以省略【建议】部分。
                """.formatted(userInput);
    }
}
