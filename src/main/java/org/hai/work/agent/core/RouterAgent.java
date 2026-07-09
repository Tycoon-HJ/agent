package org.hai.work.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.agent.Agent;
import org.hai.work.agent.dto.AgentRequest;
import org.hai.work.agent.dto.AgentResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 路由 Agent（统一入口）
 * <p>
 * 核心职责：分析用户意图，将请求分发到最合适的业务 Agent。
 * <p>
 * 工作流程：
 * 用户输入 → RouterAgent 分析意图（LLM 分类）
 * → weather? → WeatherAgent 处理
 * → code?    → CodeAgent 处理
 * → general? → 自己直接回答
 * <p>
 * 设计思路：
 * 不使用工具调用来路由（避免 Spring AI 自动执行工具导致嵌套问题），
 * 而是用一次轻量级的 LLM 调用做意图分类，再手动委托给目标 Agent。
 * 分类调用不挂载工具，响应速度快、token 消耗低。
 */
@Slf4j
@Component
public class RouterAgent extends AbstractAgent {

    /**
     * 已注册的 Agent 映射表（name → Agent 实例）
     */
    private final Map<String, Agent> agentMap = new HashMap<>();

    /**
     * 构造函数
     * <p>
     * Spring 会自动注入所有 Agent 实现（WeatherAgent、CodeAgent 等），
     * 这里将它们注册到 agentMap 中，方便后续按名称路由。
     * <p>
     * 注意：RouterAgent 自身也是 Agent，但不会出现在 agentMap 中（避免死循环）。
     *
     * @param chatModel            DeepSeek 大模型
     * @param toolCallbackProvider 工具回调（RouterAgent 不直接使用工具，但父类需要）
     * @param chatMemory           会话记忆
     * @param agents               所有已注册的 Agent 列表（Spring 自动注入）
     */
    public RouterAgent(DeepSeekChatModel chatModel,
                       ToolCallbackProvider toolCallbackProvider,
                       MessageWindowChatMemory chatMemory,
                       List<Agent> agents) {
        super(chatModel, toolCallbackProvider, chatMemory);
        // 将所有 Agent 注册到映射表（排除自身，避免路由死循环）
        for (Agent agent : agents) {
            if (agent instanceof RouterAgent) {
                continue;
            }
            if (agent instanceof AbstractAgent aa) {
                agentMap.put(aa.name(), agent);
                log.info("RouterAgent 注册子 Agent: {}", aa.name());
            }
        }
        log.info("RouterAgent 初始化完成，共注册 {} 个子 Agent: {}", agentMap.size(), agentMap.keySet());
    }

    @Override
    protected String name() {
        return "router-agent";
    }

    @Override
    protected String description() {
        return "Unified entry point that routes user requests to the most appropriate agent based on intent analysis.";
    }

    @Override
    protected String systemPrompt() {
        return """
                You are a request router. Your job is to analyze the user's intent and delegate to the right agent.
                """;
    }

    /**
     * 重写执行逻辑：先分类意图，再委托给目标 Agent
     * <p>
     * 流程：
     * 1. 调用 LLM 做意图分类（轻量级，不挂载工具，响应快）
     * 2. 根据分类结果找到目标 Agent
     * 3. 委托目标 Agent 处理用户请求
     * 4. 返回目标 Agent 的回答
     *
     * @param request 用户请求
     * @return 目标 Agent 的回答
     */
    @Override
    public AgentResponse execute(AgentRequest request) {
        String userInput = request.getInput();
        String sessionId = request.getSessionId();
        List<String> images = request.getImages();
        log.info("========== RouterAgent 开始路由 ==========");
        log.info("用户输入: {}, 会话ID: {}, 图片数: {}", userInput, sessionId, images != null ? images.size() : 0);

        // 有图片时，直接路由到 image-agent（跳过意图分类）
        if (images != null && !images.isEmpty()) {
            Agent imageAgent = agentMap.get("image-agent");
            if (imageAgent != null) {
                log.info("检测到 {} 张图片，直接路由到 image-agent", images.size());
                return imageAgent.execute(request);
            }
            log.warn("image-agent 未注册，降级为普通路由");
        }

        // ==================== 第一步：意图分类 ====================
        String category = classifyIntent(userInput, sessionId);
        log.info("意图分类结果: {}", category);

        // ==================== 第二步：路由到目标 Agent ====================
        Agent targetAgent = agentMap.get(category);
        if (targetAgent == null) {
            // 没有匹配的 Agent，用 LLM 直接回答
            log.info("未匹配到专用 Agent，使用 LLM 直接回答");
            return handleGeneralQuestion(request);
        }

        log.info("路由到 Agent: {}", category);
        AgentResponse response = targetAgent.execute(request);

        log.info("========== RouterAgent 路由完成 ==========");
        return response;
    }

    /**
     * 流式路由执行
     * <p>
     * 1. 先做意图分类（非流式，快速）
     * 2. 再委托目标 Agent 流式输出
     *
     * @param request 完整请求对象（含文本 + 图片）
     * @return Flux<String> 流式文本数据
     */
    public Flux<String> executeStream(AgentRequest request) {
        String userInput = request.getInput();
        String sessionId = request.getSessionId();
        List<String> images = request.getImages();
        int imageCount = images != null ? images.size() : 0;

        log.info("========== RouterAgent 开始流式路由 ==========");
        log.info("用户输入: {}, 会话ID: {}, 图片数: {}", userInput, sessionId, imageCount);

        // 有图片时，直接路由到 image-agent（跳过意图分类）
        if (images != null && !images.isEmpty()) {
            Agent imageAgent = agentMap.get("image-agent");
            if (imageAgent instanceof AbstractAgent aa) {
                log.info("检测到 {} 张图片，直接路由到 image-agent", imageCount);
                return aa.streamChat(request);
            }
            log.warn("image-agent 未注册，降级为普通路由");
        }

        String category = classifyIntent(userInput, sessionId);
        log.info("意图分类结果: {}", category);

        Agent targetAgent = agentMap.get(category);
        if (targetAgent == null) {
            log.info("未匹配到专用 Agent，使用 LLM 直接流式回答");
            return super.streamChat(request);
        }

        log.info("路由到 Agent: {}（流式模式）", category);
        if (targetAgent instanceof AbstractAgent aa) {
            return aa.streamChat(request);
        }

        // 兜底：如果目标 Agent 不是 AbstractAgent，降级为非流式
        AgentResponse response = targetAgent.execute(request);
        return Flux.just(response.getAnswer());
    }

    /**
     * 意图分类
     * <p>
     * 发送一次轻量级 LLM 请求，让 LLM 判断用户意图属于哪个类别。
     * 不挂载工具，不做复杂推理，只返回类别名称。
     *
     * @param userInput 用户输入
     * @param sessionId 会话ID（用于记忆上下文）
     * @return 分类结果：weather / code / general
     */
    private String classifyIntent(String userInput, String sessionId) {
        // 构建分类 prompt（不挂载工具，纯分类）
        ChatClient classifyClient = ChatClient.builder(chatModel).build();
        String agentList = agentMap.keySet().toString();
        log.info("agentList: {}", agentList);

        String classifyPrompt = """
                You are an expert AI Intent Router.
                
                Your ONLY responsibility is to determine which agent should handle the user's request.
                
                You MUST NOT answer the user's question.
                You MUST NOT explain your reasoning.
                You MUST NOT generate any additional text.
                
                ==================================================
                AVAILABLE AGENTS
                ==================================================
                
                %s
                
                ==================================================
                YOUR TASK
                ==================================================
                
                Analyze the user's PRIMARY intent and select exactly ONE agent from the available agents.
                
                If no suitable agent exists, return:
                
                general
                
                ==================================================
                ROUTING PRINCIPLES
                ==================================================
                
                Always determine the user's PRIMARY intent.
                
                When multiple intents exist, follow the priority rules below.
                
                Higher priority ALWAYS overrides lower priority.
                
                ==================================================
                PRIORITY 1 — SCHEDULING (HIGHEST PRIORITY)
                ==================================================
                
                If the user wants ANY action to happen at a future time,
                you MUST select:
                
                scheduleAgent
                
                This rule has the highest priority.
                
                It overrides every other agent.
                
                A request is considered a scheduling request when BOTH conditions are satisfied:
                
                Condition 1:
                The request contains a future time expression.
                
                Condition 2:
                The request describes an action that should happen at that future time.
                
                Examples of future time expressions include (but are NOT limited to):
                
                Relative Time
                
                - 一分钟后
                - 两分钟后
                - 五分钟后
                - 十秒后
                - 半小时后
                - 一小时后
                - 两小时后
                - 今天晚一点
                - 稍后
                - 待会
                - 等一下
                - 一会儿
                - 一会以后
                
                Absolute Time
                
                - 今天晚上
                - 今天19点
                - 今晚8点
                - 明天
                - 后天
                - 明天下午三点
                - 下周一
                - 下个月
                - 明年
                - 2027年1月1日
                
                Recurring Time
                
                - 每天
                - 每周
                - 每个月
                - 每隔
                - 每隔十分钟
                - 每隔一小时
                - 工作日
                - 周末
                
                Typical scheduled actions include (but are NOT limited to):
                
                - 提醒
                - 帮我
                - 写
                - 生成
                - 创建
                - 执行
                - 运行
                - 发送
                - 通知
                - 总结
                - 翻译
                - 分析
                - 关闭
                - 启动
                - 部署
                - 备份
                - 同步
                - 检查
                - 学习
                - 记录
                - 调用
                - 查询
                
                Examples:
                
                一分钟后提醒我喝水
                → scheduleAgent
                
                一分钟后帮我写一个恐怖故事
                → scheduleAgent
                
                十分钟后生成Java代码
                → scheduleAgent
                
                今天晚上八点提醒我开会
                → scheduleAgent
                
                明天下午发送日报
                → scheduleAgent
                
                每天上午九点提醒学习
                → scheduleAgent
                
                每周五执行数据库备份
                → scheduleAgent
                
                每隔一小时同步数据库
                → scheduleAgent
                
                IMPORTANT:
                
                If ANY future time expression exists together with ANY future action,
                ALWAYS select scheduleAgent.
                
                Never select another agent.
                
                ==================================================
                PRIORITY 2 — TOOL OPERATION
                ==================================================
                
                If the user is asking to use or manage a specific tool,
                select the corresponding tool agent.
                
                Examples:
                
                Search
                
                Database
                
                File
                
                Browser
                
                Email
                
                Calendar
                
                etc.
                
                ==================================================
                PRIORITY 3 — DOMAIN TASK
                ==================================================
                
                If there is NO scheduling intent,
                route according to the user's primary task.
                
                Examples:
                
                写故事
                → writerAgent
                
                写Java代码
                → codeAgent
                
                翻译英文
                → translatorAgent
                
                生成图片
                → imageAgent
                
                搜索资料
                → searchAgent
                
                ==================================================
                PRIORITY 4 — GENERAL CHAT
                ==================================================
                
                If no agent clearly matches,
                return
                
                general
                
                ==================================================
                DISAMBIGUATION
                ==================================================
                
                When a request could match multiple agents,
                always choose the higher-priority intent.
                
                Examples:
                
                一分钟后帮我写故事
                
                Correct:
                scheduleAgent
                
                Wrong:
                writerAgent
                
                --------------------------------
                
                十分钟后生成Spring Boot代码
                
                Correct:
                scheduleAgent
                
                Wrong:
                codeAgent
                
                --------------------------------
                
                今晚八点翻译文档
                
                Correct:
                scheduleAgent
                
                Wrong:
                translatorAgent
                
                --------------------------------
                
                每天九点搜索AI新闻
                
                Correct:
                scheduleAgent
                
                Wrong:
                searchAgent
                
                --------------------------------
                
                每小时生成一次日报
                
                Correct:
                scheduleAgent
                
                Wrong:
                writerAgent
                
                ==================================================
                OUTPUT FORMAT
                ==================================================
                
                Return ONLY ONE word.
                
                The output MUST be exactly one of:
                
                - One agent name from the available agents
                - general
                
                Do NOT output:
                
                - explanations
                - markdown
                - code block
                - punctuation
                - reasoning
                - extra words
                - multiple agents
                
                ==================================================
                USER INPUT
                ==================================================
                
                %s
                """.formatted(agentList, userInput);

        ChatResponse chatResponse = classifyClient.prompt()
                .system("You are an intent classifier.")
                .user(classifyPrompt)
                .advisors(a -> a.param("chat_memory_conversation_id", sessionId))
                .call()
                .chatResponse();
        String raw = null;
        if (chatResponse != null) {
            raw = Objects.requireNonNull(Objects.requireNonNull(chatResponse.getResult()).getOutput().getText()).trim().toLowerCase();
        }
        log.info("LLM意图分类结果为：{}", raw);

        String result = "general";
        if (raw != null && agentList.contains(raw)) {
            return raw;
        }
        return result;
    }

    /**
     * 处理通用问题（未匹配到专用 Agent 时的兜底）
     * <p>
     * 直接用 LLM 回答，不经过任何子 Agent。
     * 适合闲聊、知识问答等不需要专用 Agent 的场景。
     *
     * @param request 用户请求
     * @return LLM 直接回答
     */
    private AgentResponse handleGeneralQuestion(AgentRequest request) {
        log.info("处理通用问题，直接调用 LLM...");
        return super.execute(request);
    }
}
