package org.hai.work.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hai.work.tool.Tool;
import org.hai.work.tool.ToolRegistry;
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
 * 1. 创建 ChatClient
 * 2. 配置会话记忆
 * 3. 注册所有工具为 ToolCallbackProvider
 */
@Slf4j
@Configuration
public class AiConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public ChatClient chatClient(DeepSeekChatModel model, ToolCallbackProvider provider) {
        return ChatClient.builder(model).defaultTools(provider).build();
    }

    @Bean
    public ChatMemoryRepository chatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    public MessageWindowChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(100)
                .build();
    }

    /**
     * 注册所有工具为 ToolCallbackProvider
     * <p>
     * 扫描两种工具：
     * 1. @AgentTool 注解方式
     * 2. Tool 接口方式
     */
    @Bean
    ToolCallbackProvider toolCallbackProvider(ApplicationContext context, ObjectMapper objectMapper) {
        log.info("开始注册工具回调...");

        // @AgentTool 注解方式
        Map<String, Object> agentTools = context.getBeansWithAnnotation(AgentTool.class);
        Object[] filtered = agentTools.values().stream()
                .filter(o -> o.getClass().isAnnotationPresent(AgentTool.class))
                .toArray();

        // Tool 接口方式
        Map<String, Tool> customTools = context.getBeansOfType(Tool.class);
        ToolCallback[] customCallbacks = customTools.values().stream()
                .<ToolCallback>map(tool -> FunctionToolCallback.builder(tool.name(), (Function<Map, String>) map -> {
                            try {
                                String json = objectMapper.writeValueAsString(map);
                                return tool.execute(json);
                            } catch (JsonProcessingException e) {
                                return "Error: " + e.getMessage();
                            }
                        })
                        .description(tool.description())
                        .inputType(Map.class)
                        .inputSchema(tool.inputSchema())
                        .build())
                .toArray(ToolCallback[]::new);

        // 合并
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
}
