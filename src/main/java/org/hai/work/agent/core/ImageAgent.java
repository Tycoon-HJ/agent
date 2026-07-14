package org.hai.work.agent.core;

import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.hai.work.context.AgentContext;
import org.hai.work.skill.SkillRegistry;
import org.hai.work.tool.ToolRegistry;
import org.hai.work.tool.impl.ImageExplainTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 图片理解和分析 Agent
 * <p>
 * 严格按照用户要求分析图片，不主动扩展。
 */
@Slf4j
@Component
public class ImageAgent extends AbstractAgent {

    private final ImageExplainTool imageExplainTool;

    public ImageAgent(DeepSeekChatModel chatModel,
                      ToolCallbackProvider toolCallbackProvider,
                      MessageWindowChatMemory chatMemory,
                      SkillRegistry skillRegistry,
                      ToolRegistry toolRegistry,
                      ImageExplainTool imageExplainTool) {
        super(chatModel, toolCallbackProvider, chatMemory, skillRegistry, toolRegistry);
        this.imageExplainTool = imageExplainTool;
    }

    @Override
    public String name() {
        return "image-agent";
    }

    @Override
    public String description() {
        return "图片分析 Agent，严格按照用户要求分析图片。";
    }

    @Override
    protected String systemPrompt() {
        return """
                You are ImageAgent, an image analysis agent.

                Your job is to strictly execute the user's image-related requests.

                Strict rules:
                - Only analyze the images the user provides
                - Only answer the questions the user asks about the images
                - Do not provide unsolicited analysis
                - Do not suggest edits unless the user asks
                - Do not compare with other images unless the user asks

                When you see potential improvements, put them in the 【建议】 section.
                Never execute suggestions automatically.

                Output language: respond in the same language as the user.
                """;
    }

    @Override
    public String execute(AgentContext context) {
        String userInput = context.getOriginalInput();
        String sessionId = context.getSessionId();
        List<String> images = context.getImages();

        log.info("ImageAgent 开始处理，图片数: {}", images != null ? images.size() : 0);

        StringBuilder analysisResult = new StringBuilder();
        if (images != null && !images.isEmpty()) {
            for (int i = 0; i < images.size(); i++) {
                try {
                    JSONObject args = new JSONObject();
                    args.set("image_url", images.get(i));
                    args.set("prompt", "请详细描述这张图片的内容");
                    args.set("language", "zh");
                    String result = imageExplainTool.execute(args.toString());
                    analysisResult.append("\n【图片").append(i + 1).append("分析结果】\n").append(result).append("\n");
                } catch (Exception e) {
                    log.error("图片 {} 分析失败: {}", i + 1, e.getMessage());
                    analysisResult.append("\n【图片").append(i + 1).append("分析结果】\n分析失败：").append(e.getMessage()).append("\n");
                }
            }
        }

        String fullPrompt = "用户需求: " + userInput + "\n\n" + analysisResult + "\n请根据以上图片分析结果，回答用户的问题。\n";

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultTools(toolCallbackProvider)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        return chatClient.prompt()
                .system(buildFullSystemPrompt(context))
                .user(fullPrompt)
                .advisors(a -> a.param("chat_memory_conversation_id", sessionId))
                .call()
                .chatResponse()
                .getResult()
                .getOutput()
                .getText();
    }

    @Override
    public Flux<String> executeStream(AgentContext context) {
        String userInput = context.getOriginalInput();
        String sessionId = context.getSessionId();
        List<String> images = context.getImages();

        log.info("ImageAgent 流式处理，图片数: {}", images != null ? images.size() : 0);

        StringBuilder analysisResult = new StringBuilder();
        if (images != null && !images.isEmpty()) {
            for (int i = 0; i < images.size(); i++) {
                try {
                    JSONObject args = new JSONObject();
                    args.set("image_url", images.get(i));
                    args.set("prompt", "请详细描述这张图片的内容");
                    args.set("language", "zh");
                    String result = imageExplainTool.execute(args.toString());
                    analysisResult.append("\n【图片").append(i + 1).append("分析结果】\n").append(result).append("\n");
                } catch (Exception e) {
                    log.error("图片 {} 分析失败: {}", i + 1, e.getMessage());
                }
            }
        }

        String fullPrompt = "用户需求: " + userInput + "\n\n" + analysisResult + "\n请根据以上图片分析结果，回答用户的问题。\n";

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultTools(toolCallbackProvider)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        return chatClient.prompt()
                .system(buildFullSystemPrompt(context))
                .user(fullPrompt)
                .advisors(a -> a.param("chat_memory_conversation_id", sessionId))
                .stream()
                .content();
    }
}
