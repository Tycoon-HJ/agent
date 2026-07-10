package org.hai.work.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.agent.dto.AgentRequest;
import org.hai.work.agent.dto.AgentResponse;
import org.hai.work.agent.dto.FileData;
import org.hai.work.service.PdfParsingService;
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
 * 支持的文件类型：
 * - CSV、TXT、Markdown：前端直接读取文本
 * - Excel：前端转为 CSV 文本
 * - PDF：后端使用 PDFBox 提取文本
 */
@Slf4j
@Component
public class FileAgent extends AbstractAgent {

    private final PdfParsingService pdfParsingService;

    public FileAgent(DeepSeekChatModel chatModel,
                     ToolCallbackProvider toolCallbackProvider,
                     MessageWindowChatMemory chatMemory,
                     PdfParsingService pdfParsingService) {
        super(chatModel, toolCallbackProvider, chatMemory);
        this.pdfParsingService = pdfParsingService;
    }

    @Override
    protected String name() {
        return "file-agent";
    }

    @Override
    protected String description() {
        return "A file analysis agent that reads file contents and answers questions based on the data.";
    }

    @Override
    protected String systemPrompt() {
        return """
                You are FileAgent, a professional data analysis assistant specialized in reading and understanding file contents.

                Your core capabilities:
                1. Read and parse CSV/Excel data files
                2. Understand Markdown documents
                3. Analyze plain text files
                4. Parse and understand PDF documents
                5. Answer questions based on file content

                Rules:
                - Always base your answers on the actual file content provided
                - For CSV/Excel data: analyze rows, columns, statistics, patterns
                - For Markdown: understand document structure, extract key information
                - For TXT: read and summarize content, answer specific questions
                - For PDF: analyze the extracted text, understand document structure
                - If multiple files are provided, find relationships between them
                - Respond in the same language as the user
                - Use markdown tables for tabular data when appropriate
                - Provide specific numbers and data points when available
                - If the question cannot be answered from the file content, say so clearly

                Output format:
                - For data analysis: summary → key findings → details
                - For document questions: direct answer → supporting evidence from file
                - Use markdown for formatting (tables, lists, code blocks)
                """;
    }

    /**
     * 构建包含文件内容的 prompt
     */
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
                sb.append("内容：\n");
                sb.append(content).append("\n");
            }
        }

        sb.append("\n===== 文件内容结束 =====\n\n");
        sb.append("请根据以上文件内容，回答用户的问题。如果文件是表格数据，请用表格形式展示关键信息。\n");
        return sb.toString();
    }

    /**
     * 解析文件内容，PDF 文件使用后端 PDFBox 提取文本
     */
    private String resolveFileContent(FileData file) {
        // PDF 文件：content 字段存放 base64 数据，需要后端解析
        if (isPdf(file)) {
            try {
                log.info("检测到 PDF 文件，使用 PDFBox 解析: {}", file.getName());
                return pdfParsingService.extractText(file.getContent());
            } catch (Exception e) {
                log.error("PDF 解析失败: {}", file.getName(), e);
                return "[PDF 解析失败: " + e.getMessage() + "]";
            }
        }

        // 其他文件：前端已提取文本，直接使用
        return file.getContent();
    }

    private boolean isPdf(FileData file) {
        if (file.getType() != null && file.getType().equals("application/pdf")) {
            return true;
        }
        return file.getName() != null && file.getName().toLowerCase().endsWith(".pdf");
    }

    @Override
    public Flux<String> streamChat(AgentRequest request) {
        String userInput = request.getInput();
        String sessionId = request.getSessionId();
        List<FileData> files = request.getFiles();

        log.info("FileAgent 开始处理，文件数: {}", files != null ? files.size() : 0);

        String fullPrompt = buildPrompt(userInput, files);
        log.info("文件内容已注入，构建 LLM 请求，prompt长度={}", fullPrompt.length());

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

    @Override
    public AgentResponse execute(AgentRequest request) {
        String userInput = request.getInput();
        String sessionId = request.getSessionId();
        List<FileData> files = request.getFiles();

        log.info("FileAgent 开始处理（非流式），文件数: {}", files != null ? files.size() : 0);

        String fullPrompt = buildPrompt(userInput, files);
        log.info("文件内容已注入，构建 LLM 请求，prompt长度={}", fullPrompt.length());

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultTools(toolCallbackProvider)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        var chatResponse = chatClient.prompt()
                .system(systemPrompt())
                .user(fullPrompt)
                .advisors(a -> a.param("chat_memory_conversation_id", sessionId))
                .call()
                .chatResponse();

        AgentResponse response = new AgentResponse();
        if (chatResponse != null) {
            response.setAnswer(chatResponse.getResult().getOutput().getText());
        }
        return response;
    }
}
