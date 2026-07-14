package org.hai.work.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.context.AgentContext;
import org.hai.work.skill.SkillRegistry;
import org.hai.work.tool.ToolRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 定时任务 Agent
 * <p>
 * 严格按照用户要求管理定时任务，不主动扩展。
 */
@Slf4j
@Component
public class ScheduleAgent extends AbstractAgent {

    public ScheduleAgent(DeepSeekChatModel chatModel,
                         ToolCallbackProvider toolCallbackProvider,
                         MessageWindowChatMemory chatMemory,
                         SkillRegistry skillRegistry,
                         ToolRegistry toolRegistry) {
        super(chatModel, toolCallbackProvider, chatMemory, skillRegistry, toolRegistry);
    }

    @Override
    public String name() {
        return "schedule-agent";
    }

    @Override
    public String description() {
        return "定时任务 Agent，严格按照用户要求管理定时任务。";
    }

    @Override
    public List<String> requiredTools() {
        return List.of("schedule_task", "now");
    }

    @Override
    protected String systemPrompt() {
        return """
                You are ScheduleAgent, a scheduling agent.

                Your job is to strictly execute the user's scheduling requests.

                Strict rules:
                - Only create tasks the user explicitly requests
                - Only cancel tasks the user explicitly asks to cancel
                - Only query tasks the user explicitly asks about
                - Do not suggest additional tasks unless the user asks
                - Do not modify existing tasks unless the user asks
                - Do not reschedule tasks unless the user asks

                When you see potential improvements, put them in the 【建议】 section.
                Never execute suggestions automatically.

                Output language: respond in the same language as the user.
                """;
    }

    @Override
    public String execute(AgentContext context) {
        String userId = context.getUserId() != null ? context.getUserId() : "default-user";
        String userInput = context.getOriginalInput();

        String enhancedInput = "userId:" + userId + "\n当前时间: "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE"))
                + "\n" + userInput;

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultTools(toolCallbackProvider)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        return chatClient.prompt()
                .system(buildFullSystemPrompt(context))
                .user("用户需求: " + enhancedInput)
                .advisors(a -> a.param("chat_memory_conversation_id", context.getSessionId()))
                .call()
                .chatResponse()
                .getResult()
                .getOutput()
                .getText();
    }
}
