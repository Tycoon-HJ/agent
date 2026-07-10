package org.hai.work.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.agent.Agent;
import org.hai.work.agent.dto.AgentRequest;
import org.hai.work.agent.dto.AgentResponse;
import org.hai.work.agent.dto.FileData;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 路由 Agent（统一入口）
 * <p>
 * 核心职责：分析用户意图，将请求分发到最合适的业务 Agent。
 * <p>
 * 优化策略：
 * 1. 优先使用关键词规则匹配（零 token 消耗）
 * 2. 规则未命中时才调用 LLM 分类（精简 prompt）
 */
@Slf4j
@Component
public class RouterAgent extends AbstractAgent {

    /**
     * 已注册的 Agent 映射表（name → Agent 实例）
     */
    private final Map<String, Agent> agentMap = new HashMap<>();

    /**
     * 调度意图关键词模式
     */
    private static final Pattern SCHEDULE_PATTERN = Pattern.compile(
            "(提醒|定时|闹钟|分钟后|小时后|秒后|明天|后天|今晚|今天.*点|下周|每天|每周|每月|每隔|工作日|周末|以后|稍后|待会|一会)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 调度动作关键词
     */
    private static final Pattern SCHEDULE_ACTION_PATTERN = Pattern.compile(
            "(提醒|帮我|写|生成|创建|执行|运行|发送|通知|总结|翻译|分析|关闭|启动|部署|备份|同步|检查|学习|记录|调用|查询)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 构造函数
     */
    public RouterAgent(DeepSeekChatModel chatModel,
                       ToolCallbackProvider toolCallbackProvider,
                       MessageWindowChatMemory chatMemory,
                       List<Agent> agents) {
        super(chatModel, toolCallbackProvider, chatMemory);
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
        return "You are a request router. Your job is to analyze the user's intent and delegate to the right agent.";
    }

    @Override
    public AgentResponse execute(AgentRequest request) {
        String userInput = request.getInput();
        String sessionId = request.getSessionId();
        List<String> images = request.getImages();
        List<FileData> files = request.getFiles();
        log.info("========== RouterAgent 开始路由 ==========");
        log.info("用户输入: {}, 会话ID: {}, 图片数: {}, 文件数: {}", userInput, sessionId,
                images != null ? images.size() : 0, files != null ? files.size() : 0);

        // 文件 → file-agent
        if (files != null && !files.isEmpty()) {
            Agent fileAgent = agentMap.get("file-agent");
            if (fileAgent != null) {
                log.info("检测到 {} 个文件，直接路由到 file-agent", files.size());
                return fileAgent.execute(request);
            }
            log.warn("file-agent 未注册，降级为普通路由");
        }

        // 图片 → image-agent
        if (images != null && !images.isEmpty()) {
            Agent imageAgent = agentMap.get("image-agent");
            if (imageAgent != null) {
                log.info("检测到 {} 张图片，直接路由到 image-agent", images.size());
                return imageAgent.execute(request);
            }
            log.warn("image-agent 未注册，降级为普通路由");
        }

        // 关键词规则匹配（优先，零 token 消耗）
        String category = tryKeywordRouting(userInput);
        if (category != null) {
            log.info("关键词规则匹配: {}", category);
            Agent targetAgent = agentMap.get(category);
            if (targetAgent != null) {
                return targetAgent.execute(request);
            }
        }

        // LLM 意图分类
        category = classifyIntent(userInput, sessionId);
        log.info("意图分类结果: {}", category);

        Agent targetAgent = agentMap.get(category);
        if (targetAgent == null) {
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
     */
    public Flux<String> executeStream(AgentRequest request) {
        String userInput = request.getInput();
        String sessionId = request.getSessionId();
        List<String> images = request.getImages();
        List<FileData> files = request.getFiles();
        int imageCount = images != null ? images.size() : 0;
        int fileCount = files != null ? files.size() : 0;

        log.info("========== RouterAgent 开始流式路由 ==========");
        log.info("用户输入: {}, 会话ID: {}, 图片数: {}, 文件数: {}", userInput, sessionId, imageCount, fileCount);

        // 文件 → file-agent
        if (files != null && !files.isEmpty()) {
            Agent fileAgent = agentMap.get("file-agent");
            if (fileAgent instanceof AbstractAgent aa) {
                log.info("检测到 {} 个文件，直接路由到 file-agent", fileCount);
                return aa.streamChat(request);
            }
            log.warn("file-agent 未注册，降级为普通路由");
        }

        // 图片 → image-agent
        if (images != null && !images.isEmpty()) {
            Agent imageAgent = agentMap.get("image-agent");
            if (imageAgent instanceof AbstractAgent aa) {
                log.info("检测到 {} 张图片，直接路由到 image-agent", imageCount);
                return aa.streamChat(request);
            }
            log.warn("image-agent 未注册，降级为普通路由");
        }

        // 关键词规则匹配
        String category = tryKeywordRouting(userInput);
        if (category != null) {
            log.info("关键词规则匹配: {}", category);
            Agent targetAgent = agentMap.get(category);
            if (targetAgent instanceof AbstractAgent aa) {
                return aa.streamChat(request);
            }
        }

        // LLM 意图分类
        category = classifyIntent(userInput, sessionId);
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

        AgentResponse response = targetAgent.execute(request);
        return Flux.just(response.getAnswer());
    }

    /**
     * 尝试关键词规则路由（零 token 消耗）
     *
     * @return 匹配的 agent 名称，未匹配返回 null
     */
    private String tryKeywordRouting(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return null;
        }

        String input = userInput.trim();

        // 调度意图检测：同时包含时间表达和动作词
        if (SCHEDULE_PATTERN.matcher(input).find() && SCHEDULE_ACTION_PATTERN.matcher(input).find()) {
            if (agentMap.containsKey("scheduleAgent")) {
                return "scheduleAgent";
            }
        }

        return null;
    }

    /**
     * 意图分类（精简 prompt，减少 token 消耗）
     */
    private String classifyIntent(String userInput, String sessionId) {
        ChatClient classifyClient = ChatClient.builder(chatModel).build();
        String agentList = agentMap.keySet().toString();
        log.info("agentList: {}", agentList);

        // 精简后的分类 prompt（减少约 60% token）
        String classifyPrompt = """
                You are an intent classifier. Select exactly ONE agent from the list below.

                Available agents: %s

                Rules (priority high→low):
                1. If user wants action at a future time → scheduleAgent
                2. If user asks for tool/operation → corresponding tool agent
                3. If user has a domain task → corresponding domain agent
                4. Otherwise → general

                Return ONLY one word (agent name or "general"). No explanations.

                User input: %s
                """.formatted(agentList, userInput);

        ChatResponse chatResponse = classifyClient.prompt()
                .system("You are an intent classifier. Return only one word.")
                .user(classifyPrompt)
                .advisors(a -> a.param("chat_memory_conversation_id", sessionId))
                .call()
                .chatResponse();

        String raw = null;
        if (chatResponse != null) {
            raw = Objects.requireNonNull(Objects.requireNonNull(chatResponse.getResult()).getOutput().getText()).trim().toLowerCase();
        }
        log.info("LLM意图分类结果为：{}", raw);

        if (raw != null && agentList.contains(raw)) {
            return raw;
        }
        return "general";
    }

    private AgentResponse handleGeneralQuestion(AgentRequest request) {
        log.info("处理通用问题，直接调用 LLM...");
        return super.execute(request);
    }
}
