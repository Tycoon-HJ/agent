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
 */
@Slf4j
@Configuration
public class AiConfig {

    /**
     * 统一 ObjectMapper Bean
     * <p>
     * 全局使用同一个实例，避免重复创建。
     * 所有需要 JSON 序列化/反序列化的地方都应注入此 Bean。
     */
    @Bean
    public ObjectMapper objectMapper() {
        log.info("初始化全局 ObjectMapper");
        return new ObjectMapper();
    }

    /**
     * 创建 ChatClient（LLM 对话客户端）
     */
    @Bean
    public ChatClient chatClient(DeepSeekChatModel model, ToolCallbackProvider provider) {
        log.info("初始化 ChatClient，已注册 ToolCallbackProvider: {}", provider.getClass().getSimpleName());
        return ChatClient.builder(model).defaultTools(provider).build();
    }

    /**
     * 会话记忆存储仓库（内存版）
     */
    @Bean
    public ChatMemoryRepository chatMemoryRepository() {
        log.info("初始化 InMemoryChatMemoryRepository");
        return new InMemoryChatMemoryRepository();
    }

    /**
     * 滑动窗口会话记忆
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
     */
    @Bean
    ToolCallbackProvider toolCallbackProvider(ApplicationContext context, ObjectMapper objectMapper) {
        log.info("开始注册工具回调...");

        // ==================== 第一部分：注册 @AgentTool 注解方式的工具 ====================
        Map<String, Object> agentTools = context.getBeansWithAnnotation(AgentTool.class);
        Object[] filtered = agentTools.values().stream()
                .filter(o -> o.getClass().isAnnotationPresent(AgentTool.class))
                .toArray();
        log.info("发现 @AgentTool 注解工具 {} 个（过滤后）: {}", filtered.length,
                Arrays.stream(filtered).map(o -> o.getClass().getSimpleName()).toList());

        // ==================== 第二部分：注册自定义 Tool 接口方式的工具 ====================
        Map<String, Tool> customTools = context.getBeansOfType(Tool.class);
        log.info("发现自定义 Tool 接口工具 {} 个: {}", customTools.size(), customTools.keySet());

        ToolCallback[] customCallbacks = customTools.values().stream()
                .<ToolCallback>map(tool -> {
                    log.info("注册自定义工具: name={}, description={}", tool.name(), truncate(tool.description()));
                    return FunctionToolCallback.builder(tool.name(), (Function<Map, String>) map -> {
                                try {
                                    String json = objectMapper.writeValueAsString(map);
                                    log.debug("自定义工具 [{}] 执行，参数: {}", tool.name(), truncate(json));
                                    String result = tool.execute(json);
                                    log.debug("自定义工具 [{}] 返回: {}", tool.name(), truncate(result));
                                    return result;
                                } catch (JsonProcessingException e) {
                                    log.error("自定义工具 [{}] 参数序列化失败", tool.name(), e);
                                    return "Error: " + e.getMessage();
                                }
                            })
                            .description(tool.description())
                            .inputType(Map.class)
                            .inputSchema(tool.inputSchema())
                            .build();
                })
                .toArray(ToolCallback[]::new);

        // ==================== 第三部分：合并所有工具 ====================
        List<ToolCallback> allCallbacks = new ArrayList<>(Arrays.asList(customCallbacks));

        if (filtered.length > 0) {
            ToolCallback[] annotationCallbacks = MethodToolCallbackProvider.builder()
                    .toolObjects(filtered)
                    .build()
                    .getToolCallbacks();
            allCallbacks.addAll(Arrays.asList(annotationCallbacks));
        }

        log.info("工具注册完成，共 {} 个 ToolCallback", allCallbacks.size());
        return () -> allCallbacks.toArray(ToolCallback[]::new);
    }

    /**
     * 截断过长的日志内容
     */
    private String truncate(String text) {
        if (text == null) return "null";
        int maxLen = 200;
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
