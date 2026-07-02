package org.hai.work.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.agent.Agent;
import org.hai.work.agent.dto.AgentRequest;
import org.hai.work.agent.dto.AgentResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * Agent 抽象基类
 * <p>
 * 核心职责：
 * 1. 封装与 LLM 的交互流程（构建 prompt → 调用 LLM → 解析结果）
 * 2. 集成会话记忆（ChatMemory），让 Agent 能记住对话上下文
 * 3. 集成工具调用（Tool），让 LLM 能调用外部能力（如查天气）
 * <p>
 * 设计思路：
 * 每次请求独立构建 ChatClient，通过 sessionId 隔离不同会话的对话上下文。
 * Spring AI 会自动处理 LLM 返回的 tool_calls（工具调用请求），
 * 将工具执行结果回传给 LLM，最终返回完整的回答。
 * <p>
 * 调用链路：
 * 用户输入 → buildUserPrompt() → ChatClient.call()
 * → Spring AI 发送 prompt + 工具定义给 LLM
 * → LLM 决定是否调用工具
 * → 如果调用：Spring AI 自动执行工具 → 将结果回传 LLM → LLM 生成最终回答
 * → 如果不调用：LLM 直接生成回答
 * → 返回 ChatResponse
 */
@Slf4j
public abstract class AbstractAgent implements Agent {

    /**
     * DeepSeek 大模型实例
     * 由 Spring AI 自动配置，负责与 DeepSeek API 通信
     */
    protected final DeepSeekChatModel chatModel;

    /**
     * 工具回调提供者
     * 包含所有已注册的工具（如 weather_query），LLM 可以决定是否调用
     */
    protected final ToolCallbackProvider toolCallbackProvider;

    /**
     * 会话记忆
     * 为每个 sessionId 维护独立的对话历史，支持上下文关联
     */
    protected final MessageWindowChatMemory chatMemory;

    protected AbstractAgent(DeepSeekChatModel chatModel,
                            ToolCallbackProvider toolCallbackProvider,
                            MessageWindowChatMemory chatMemory) {
        this.chatModel = chatModel;
        this.toolCallbackProvider = toolCallbackProvider;
        this.chatMemory = chatMemory;
        log.info("Agent [{}] 初始化完成", name());
    }

    /**
     * Agent 名称标识，如 "weather-agent"
     */
    protected abstract String name();

    /**
     * Agent 功能描述
     */
    protected abstract String description();

    /**
     * 系统提示词（System Prompt）
     * <p>
     * 定义 Agent 的人格、职责、行为规则。
     * 这是控制 Agent 行为的核心手段——LLM 会严格遵循 system prompt 的指令。
     */
    protected abstract String systemPrompt();

    /**
     * 执行 Agent 逻辑
     * <p>
     * 完整流程：
     * 1. 从请求中提取用户输入和 sessionId
     * 2. 构建 ChatClient（挂载工具 + 会话记忆）
     * 3. 构建用户提示词（引导 LLM 按格式输出）
     * 4. 调用 LLM（Spring AI 自动处理工具调用）
     * 5. 返回结果
     *
     * @param request 包含用户输入、sessionId、userId 的请求对象
     * @return Agent 的回答（包含分析、数据、结论、建议）
     */
    @Override
    public AgentResponse execute(AgentRequest request) {
        String userInput = request.getInput();
        String sessionId = request.getSessionId();
        log.info("========== Agent [{}] 开始执行 ==========", name());
        log.debug("用户输入: {}", userInput);
        log.debug("会话ID: {}, 用户ID: {}", sessionId, request.getUserId());

        // ==================== 构建 ChatClient ====================
        // 每次请求独立构建 ChatClient，原因：
        // 1. 需要通过 sessionId 隔离不同会话的对话上下文
        // 2. ChatClient.builder() 是轻量级操作（只存储引用，不发起网络请求）
        // 3. 真正的 LLM 调用发生在 .call() 时
        //
        // defaultTools: 注册所有工具，LLM 可以决定调用哪些
        // defaultAdvisors: 注册 MessageChatMemoryAdvisor，自动管理对话记忆
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultTools(toolCallbackProvider)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        // ==================== 构建用户提示词 ====================
        String userPrompt = buildUserPrompt(userInput);

        // ==================== 调用 LLM ====================
        // .system(): 设置系统提示词（Agent 人格 + 行为规则）
        // .user(): 设置用户提示词（用户需求 + 输出格式引导）
        // .advisors(): 传入 sessionId，让记忆 Advisor 知道当前是哪个会话
        //   - chat_memory_conversation_id 是 Spring AI 约定的参数名
        //   - MessageChatMemoryAdvisor 会用此 ID 存取对话历史
        // .call(): 发起 LLM 调用
        //   - Spring AI 自动处理：如果 LLM 返回 tool_calls，会自动执行工具并回传结果
        log.info("调用 LLM，会话ID: {}，Spring AI 将自动处理工具调用...", sessionId);
        ChatResponse chatResponse = chatClient.prompt()
                .system(systemPrompt())
                .user(userPrompt)
                .advisors(a -> a.param("chat_memory_conversation_id", sessionId))
                .call()
                .chatResponse();

        // ==================== 解析结果 ====================
        String answer = null;
        if (chatResponse != null) {
            answer = Objects.requireNonNull(chatResponse.getResult()).getOutput().getText();
        }
        log.info("LLM 返回结果: {}", answer);

        AgentResponse response = new AgentResponse();
        response.setAnswer(answer);

        log.info("========== Agent [{}] 执行完成 ==========", name());
        return response;
    }

    /**
     * 流式执行 Agent 逻辑（SSE，支持图片）
     * <p>
     * 与 execute() 类似，但使用 .stream() 替代 .call()，
     * 返回 Flux<String>，每个元素是一小段文本，实现打字机效果。
     * <p>
     * 当请求包含图片时，构建多模态消息（文本 + 图片）发送给 LLM。
     *
     * @param request 完整请求对象（含文本 + 图片）
     * @return Flux<String> 流式文本数据
     */
    public Flux<String> streamChat(AgentRequest request) {
        String userInput = request.getInput();
        String sessionId = request.getSessionId();

        log.info("Agent [{}] 开始流式执行，会话ID: {}", name(), sessionId);

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultTools(toolCallbackProvider)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        String userPrompt = buildUserPrompt(userInput);

        return chatClient.prompt()
                .system(systemPrompt())
                .user(userPrompt)
                .advisors(a -> a.param("chat_memory_conversation_id", sessionId))
                .stream()
                .content();
    }

    /**
     * 构建用户提示词
     * <p>
     * 引导 LLM 按照固定格式输出，包含：
     * - 【分析】：用户意图分析
     * - 【数据】：工具查询结果（如果调用了工具）
     * - 【结论】：核心推荐
     * - 【建议】：实用的行动建议
     *
     * @param userInput 用户原始输入
     * @return 格式化后的用户提示词
     */
    protected String buildUserPrompt(String userInput) {
        return """
                用户需求: %s
                
                你是一位经验丰富的专业顾问。
                
                你的目标不是回答问题，而是真正帮助用户解决问题。
                
                工作原则：
                
                - 首先理解用户真正想解决的问题，而不是仅回答字面意思。
                - 当需要实时信息时，主动调用工具获取最新数据，不要凭空猜测。
                - 工具返回的数据应自然融入回答，用自己的语言解释数据背后的含义，而不是机械罗列。
                - 回答应流畅、自然、有温度，像人与人交流，而不是生成报告。
                - 优先给出明确的建议，而不是把所有可能性都列给用户自行判断。
                - 如果存在更好的方案，应主动推荐，并解释推荐理由。
                - 不要暴露你的思考过程，不要使用"分析""推理""工具返回"等措辞。
                - 使用 Markdown 提高可读性，但避免为了格式而堆砌标题或固定模板。
                - 如果当前话题适合增加一点轻松氛围，可调用 search_meme 获取一个贴切的表情包；如果不适合，则不要调用。
                """.formatted(userInput);
    }
}
