package org.hai.work.agent.core;


import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.hai.work.agent.dto.AgentRequest;
import org.hai.work.tool.impl.ImageExplainTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Component
public class ImageAgent extends AbstractAgent {

    private final ImageExplainTool imageExplainTool;

    protected ImageAgent(DeepSeekChatModel chatModel,
                         ToolCallbackProvider toolCallbackProvider,
                         MessageWindowChatMemory chatMemory,
                         ImageExplainTool imageExplainTool) {
        super(chatModel, toolCallbackProvider, chatMemory);
        this.imageExplainTool = imageExplainTool;
    }

    @Override
    protected String name() {
        return "image-agent";
    }

    @Override
    protected String description() {
        return "用于图片理解和分析";
    }

    @Override
    protected String systemPrompt() {
        return """
                你是一个专业的图像分析助手。你的核心职责是根据图片分析结果和用户的问题，给出有价值的回答。
                
                规则：
                - 图片已经通过工具分析完毕，分析结果会以【图片X分析结果】的格式提供给你
                - 你不需要再调用任何图片分析工具
                - 根据分析结果和用户的具体问题，给出详细、有用的回答
                - 用中文回答，回答要详细、结构化
                - 返回的格式必须为markdown格式
                - 返回的内容必须调用search_meme这个工具类获取一个表情包，输出内容输出一个贴合实际的表情包
                """;
    }

    /**
     * 重写流式执行：先调用 ImageExplainTool 分析图片，再把结果交给 LLM
     */
    @Override
    public Flux<String> streamChat(AgentRequest request) {
        String userInput = request.getInput();
        String sessionId = request.getSessionId();
        List<String> images = request.getImages();

        log.info("ImageAgent 开始处理，图片数: {}", images != null ? images.size() : 0);

        // 第一步：直接调用 ImageExplainTool 分析每张图片
        StringBuilder analysisResult = new StringBuilder();
        if (images != null && !images.isEmpty()) {
            for (int i = 0; i < images.size(); i++) {
                String dataUrl = images.get(i);
                log.info("调用 ImageExplainTool 分析图片 {}", i + 1);
                try {
                    JSONObject args = new JSONObject();
                    args.set("image_url", dataUrl);
                    args.set("prompt", "请详细描述这张图片的内容");
                    args.set("language", "zh");
                    String result = imageExplainTool.execute(args.toString());
                    analysisResult.append("\n【图片").append(i + 1).append("分析结果】\n").append(result).append("\n");
                    log.info("图片 {} 分析完成", i + 1);
                } catch (Exception e) {
                    log.error("图片 {} 分析失败: {}", i + 1, e.getMessage());
                    analysisResult.append("\n【图片").append(i + 1).append("分析结果】\n分析失败：").append(e.getMessage()).append("\n");
                }
            }
        }

        // 第二步：构建包含分析结果的 prompt，交给 LLM 生成最终回答
        String fullPrompt = "用户需求: " + userInput + "\n\n"
                + analysisResult
                + "\n请根据以上图片分析结果，回答用户的问题。\n";

        log.info("图片分析完成，构建 LLM 请求，prompt长度={}", fullPrompt.length());

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultTools(toolCallbackProvider)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        return chatClient.prompt()
                .system(systemPrompt())
                .user(fullPrompt)
                .advisors(a -> a.param("chat_memory_conversation_id", sessionId))
                .stream()
                .content();
    }
}
