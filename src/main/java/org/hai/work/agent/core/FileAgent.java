package org.hai.work.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.agent.dto.FileData;
import org.hai.work.context.AgentContext;
import org.hai.work.service.PdfParsingService;
import org.hai.work.skill.SkillRegistry;
import org.hai.work.tool.ToolRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 文件处理 Agent
 * <p>
 * 严格按照用户要求分析文件，不主动扩展。
 */
@Slf4j
@Component
public class FileAgent extends AbstractAgent {

    private final PdfParsingService pdfParsingService;

    public FileAgent(DeepSeekChatModel chatModel,
                     ToolCallbackProvider toolCallbackProvider,
                     MessageWindowChatMemory chatMemory,
                     SkillRegistry skillRegistry,
                     ToolRegistry toolRegistry,
                     PdfParsingService pdfParsingService) {
        super(chatModel, toolCallbackProvider, chatMemory, skillRegistry, toolRegistry);
        this.pdfParsingService = pdfParsingService;
    }

    @Override
    public String name() {
        return "file-agent";
    }

    @Override
    public String description() {
        return "文件分析 Agent，严格按照用户要求分析文件内容。";
    }

    @Override
    protected String systemPrompt() {
        return """
                You are FileAgent, a file analysis agent.

                Your job is to strictly execute the user's file-related requests.

                Strict rules:
                - Only analyze the files the user provides
                - Only answer the questions the user asks about the files
                - Do not suggest file modifications unless the user asks
                - Do not reformat files unless the user asks
                - Do not extract additional data unless the user asks
                - Do not compare files unless the user asks

                When you see potential improvements, put them in the 【建议】 section.
                Never execute suggestions automatically.

                Output language: respond in the same language as the user.
                """;
    }

    @Override
    public String execute(AgentContext context) {
        String userInput = context.getOriginalInput();
        String sessionId = context.getSessionId();
        List<FileData> files = context.getFiles();

        log.info("FileAgent 开始处理，文件数: {}", files != null ? files.size() : 0);

        String fullPrompt = buildPrompt(userInput, files);

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
        List<FileData> files = context.getFiles();

        log.info("FileAgent 流式处理，文件数: {}", files != null ? files.size() : 0);

        String fullPrompt = buildPrompt(userInput, files);

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

    private String buildPrompt(String userInput, List<FileData> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户需求: ").append(userInput).append("\n\n");
        sb.append("===== 文件内容 =====");

        if (files != null && !files.isEmpty()) {
            for (int i = 0; i < files.size(); i++) {
                FileData file = files.get(i);
                String content = resolveFileContent(file);
                sb.append("\n【文件").append(i + 1).append("：").append(file.getName()).append("】\n");
                sb.append("类型：").append(file.getType()).append("\n");
                sb.append("内容：\n").append(content).append("\n");
            }
        }

        sb.append("\n===== 文件内容结束 =====\n\n");
        sb.append("请根据以上文件内容，回答用户的问题。\n");
        return sb.toString();
    }

    private String resolveFileContent(FileData file) {
        if (isPdf(file)) {
            try {
                return pdfParsingService.extractText(file.getContent());
            } catch (Exception e) {
                log.error("PDF 解析失败: {}", file.getName(), e);
                return "[PDF 解析失败: " + e.getMessage() + "]";
            }
        }
        return file.getContent();
    }

    private boolean isPdf(FileData file) {
        if (file.getType() != null && file.getType().equals("application/pdf")) return true;
        return file.getName() != null && file.getName().toLowerCase().endsWith(".pdf");
    }
}
