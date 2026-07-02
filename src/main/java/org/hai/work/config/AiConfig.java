package org.hai.work.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hai.work.tool.Tool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Spring AI 核心配置类
 * <p>
 * 职责：
 * 1. 创建 ChatClient（LLM 对话客户端）
 * 2. 配置会话记忆（ChatMemory），让 Agent 能记住上下文
 * 3. 统一注册所有工具（Tool），使 LLM 能调用外部能力
 * <p>
 * 工具注册机制：
 * 本项目支持两种工具注册方式，最终都统一转为 Spring AI 的 ToolCallback：
 * 方式一：@AgentTool + @Tool 注解（Spring AI 原生方式，适合简单工具）
 * 方式二：自定义 Tool 接口（适合需要灵活控制的工具，如 WeatherTool）
 */
@Slf4j
@Configuration
public class AiConfig {

    /**
     * 创建 ChatClient（LLM 对话客户端）
     * <p>
     * ChatClient 是 Spring AI 的核心入口，负责：
     * - 发送 prompt 给 LLM（DeepSeek）
     * - 自动处理 LLM 返回的工具调用请求（tool_calls）
     * - 将工具执行结果回传给 LLM，获取最终回答
     *
     * @param model    DeepSeek 大模型实例（由 Spring AI 自动配置）
     * @param provider 所有已注册的工具回调（由下方 toolCallbackProvider 提供）
     * @return 配置好的 ChatClient 实例
     */
    @Bean
    public ChatClient chatClient(DeepSeekChatModel model, ToolCallbackProvider provider) {
        log.info("初始化 ChatClient，已注册 ToolCallbackProvider: {}", provider.getClass().getSimpleName());
        return ChatClient.builder(model).defaultTools(provider).build();
    }

    /**
     * 会话记忆存储仓库（内存版）
     * <p>
     * InMemoryChatMemoryRepository 将对话历史保存在 JVM 内存中。
     * 特点：速度快，但应用重启后数据丢失。
     * 如需持久化，可替换为 JdbcChatMemoryRepository（数据库存储）。
     */
    @Bean
    public ChatMemoryRepository chatMemoryRepository() {
        log.info("初始化 InMemoryChatMemoryRepository");
        return new InMemoryChatMemoryRepository();
    }

    /**
     * 滑动窗口会话记忆
     * <p>
     * MessageWindowChatMemory 的工作原理：
     * - 为每个 sessionId 维护一个独立的对话历史
     * - 采用滑动窗口策略，只保留最近 N 条消息（这里设为 100）
     * - 超出窗口的旧消息会被自动丢弃，避免 token 超限
     * <p>
     * 例如：
     * sessionId=abc 的对话历史：[用户1, 助手1, 用户2, 助手2, ...]
     * sessionId=xyz 的对话历史：[用户A, 助手A, ...]
     * 两个会话完全隔离，互不干扰
     *
     * @param repository 底层存储仓库
     */
    @Bean
    public MessageWindowChatMemory chatMemory(ChatMemoryRepository repository) {
        log.info("初始化 MessageWindowChatMemory，最大消息数: 100");
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(100)
                .build();
    }

    /**
     * 统一注册所有工具为 ToolCallbackProvider
     * <p>
     * 这是整个工具系统的核心注册中心。LLM 能调用哪些工具，全由这里决定。
     * <p>
     * 注册流程：
     * 1. 扫描所有 @AgentTool 注解的 bean（如 UserTool），提取 @Tool 方法 → 转为 ToolCallback
     * 2. 扫描所有实现 Tool 接口的 bean（如 WeatherTool）→ 用 FunctionToolCallback 包装
     * 3. 合并所有 ToolCallback，返回给 Spring AI
     * <p>
     * 为什么需要两种方式？
     * - @AgentTool + @Tool：Spring AI 原生方式，用注解标记方法即可，简单方便
     * - Tool 接口：自定义方式，可以完全控制工具的 name、description、inputSchema、execute 逻辑
     *
     * @param context Spring 应用上下文，用于扫描 bean
     * @return 合并后的工具回调提供者
     */
    @Bean
    ToolCallbackProvider toolCallbackProvider(ApplicationContext context) {
        ObjectMapper objectMapper = new ObjectMapper();
        log.info("开始注册工具回调...");

        // ==================== 第一部分：注册 @AgentTool 注解方式的工具 ====================
        // getBeansWithAnnotation 会扫描所有带有 @AgentTool 注解的 bean
        // 注意：@AgentTool 上有 @Component 元注解，所以也会匹配到仅标注 @Component 的 bean
        // 因此需要额外过滤，只保留真正标注了 @AgentTool 的类
        Map<String, Object> agentTools = context.getBeansWithAnnotation(AgentTool.class);
        Object[] filtered = agentTools.values().stream()
                .filter(o -> o.getClass().isAnnotationPresent(AgentTool.class))
                .toArray();
        log.info("发现 @AgentTool 注解工具 {} 个（过滤后）: {}", filtered.length,
                Arrays.stream(filtered).map(o -> o.getClass().getSimpleName()).toList());

        // ==================== 第二部分：注册自定义 Tool 接口方式的工具 ====================
        // getBeansOfType(Tool.class) 会扫描所有实现了 Tool 接口的 bean
        // 每个 Tool 会被包装为 FunctionToolCallback，注册到 Spring AI
        Map<String, Tool> customTools = context.getBeansOfType(Tool.class);
        log.info("发现自定义 Tool 接口工具 {} 个: {}", customTools.size(), customTools.keySet());

        ToolCallback[] customCallbacks = customTools.values().stream()
                .<ToolCallback>map(tool -> {
                    log.info("注册自定义工具: name={}, description={}", tool.name(), tool.description());
                    // FunctionToolCallback.builder 接收：
                    //   - 工具名称（LLM 通过此名称调用工具）
                    //   - 执行函数（接收 Map 参数，返回字符串结果）
                    return FunctionToolCallback.builder(tool.name(), (Function<Map, String>) map -> {
                                try {
                                    // 将 Map 参数序列化为 JSON 字符串，传给 Tool.execute()
                                    String json = objectMapper.writeValueAsString(map);
                                    log.debug("自定义工具 [{}] 执行，参数: {}", tool.name(), json);
                                    String result = tool.execute(json);
                                    log.debug("自定义工具 [{}] 返回: {}", tool.name(), result);
                                    return result;
                                } catch (JsonProcessingException e) {
                                    log.error("自定义工具 [{}] 参数序列化失败", tool.name(), e);
                                    return "Error: " + e.getMessage();
                                }
                            })
                            .description(tool.description())   // 工具描述，LLM 据此判断何时调用
                            .inputType(Map.class)               // 参数类型
                            .inputSchema(tool.inputSchema())    // JSON Schema，告诉 LLM 参数格式
                            .build();
                })
                .toArray(ToolCallback[]::new);

        // ==================== 第三部分：合并所有工具 ====================
        List<ToolCallback> allCallbacks = new ArrayList<>(Arrays.asList(customCallbacks));

        // 如果有 @AgentTool 注解方式的工具，也转换为 ToolCallback 加入列表
        if (filtered.length > 0) {
            ToolCallback[] annotationCallbacks = MethodToolCallbackProvider.builder()
                    .toolObjects(filtered)
                    .build()
                    .getToolCallbacks();
            allCallbacks.addAll(Arrays.asList(annotationCallbacks));
        }

        log.info("工具注册完成，共 {} 个 ToolCallback", allCallbacks.size());
        // 返回 ToolCallbackProvider，Spring AI 在调用 LLM 时会自动附带这些工具定义
        return () -> allCallbacks.toArray(ToolCallback[]::new);
    }
}