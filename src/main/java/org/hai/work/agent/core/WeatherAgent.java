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
 * 天气查询 Agent
 * <p>
 * 严格按照用户要求查询天气，不主动扩展。
 */
@Slf4j
@Component
public class WeatherAgent extends AbstractAgent {

    public WeatherAgent(DeepSeekChatModel chatModel,
                        ToolCallbackProvider toolCallbackProvider,
                        MessageWindowChatMemory chatMemory,
                        SkillRegistry skillRegistry,
                        ToolRegistry toolRegistry) {
        super(chatModel, toolCallbackProvider, chatMemory, skillRegistry, toolRegistry);
    }

    @Override
    public String name() {
        return "weather-agent";
    }

    @Override
    public String description() {
        return "天气查询 Agent，严格按照用户要求查询天气信息。";
    }

    @Override
    public List<String> requiredTools() {
        return List.of("weather_query");
    }

    @Override
    protected String systemPrompt() {
        return """
                You are WeatherAgent, a weather query agent.

                Your job is to strictly execute the user's weather-related requests.

                Strict rules:
                - Only query weather for the city the user specifies
                - Only provide the weather information the user asks for
                - Do not provide travel advice unless the user asks
                - Do not provide clothing suggestions unless the user asks
                - Do not compare weather across cities unless the user asks
                - Do not forecast beyond what the user requests

                When you see potential improvements, put them in the 【建议】 section.
                Never execute suggestions automatically.

                Output language: respond in the same language as the user.
                """;
    }
}
