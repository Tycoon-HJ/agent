package org.hai.work.tool.impl;

import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.hai.work.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * 图像解释工具
 * <p>
 * 基于 Agnes 2.0 Flash 模型的图像理解能力，实现图像分析和解释功能。
 * LLM 可以通过此工具分析图像内容、提取信息、回答图像相关问题。
 * <p>
 * 模型特点：
 * - 支持图像 URL 输入
 * - 强大的图像理解能力
 * - 支持多种图像分析任务（描述、OCR、UI分析等）
 * <p>
 * API 文档：https://apihub.agnes-ai.com/v1/chat/completions
 * 模型名称：agnes-2.0-flash
 * <p>
 * 工作流程：
 * LLM 决定需要分析图像 → 发起 tool_call(explain_image, {image_url:"...", prompt:"..."})
 * → Spring AI 拦截 tool_call → 调用本类 execute() 方法
 * → 请求 Agnes 2.0 Flash API → 返回图像分析结果给 LLM
 * → LLM 整合分析结果生成最终回答
 */
@Component
@Slf4j
public class ImageExplainTool implements Tool {

    /**
     * Agnes API 基础地址
     */
    private static final String BASE_URL = "https://apihub.agnes-ai.com";

    /**
     * 聊天补全端点
     */
    private static final String CHAT_ENDPOINT = "/v1/chat/completions";

    /**
     * API Key（从环境变量或配置中读取）
     */
    private static final String API_KEY = System.getenv("AGNES_API_KEY") != null
            ? System.getenv("AGNES_API_KEY")
            : "";

    /**
     * 工具名称
     * LLM 通过此名称识别和调用工具
     */
    @Override
    public String name() {
        return "explain_image";
    }

    /**
     * 工具描述
     * LLM 会阅读此描述来判断何时调用此工具
     */
    @Override
    public String description() {
        return """
                图像解释工具。使用AI分析和理解图像内容。
                适用场景：
                - 描述图片内容
                - 提取图片中的文字（OCR）
                - 分析截图、UI界面
                - 识别图片中的物体、场景
                - 解释图表、数据可视化
                - 回答关于图片的问题
                返回对图像的详细分析和解释。
                """;
    }

    /**
     * 输入参数的 JSON Schema
     * <p>
     * 参数说明：
     * - image_url（必填）：图像的公开可访问 URL
     * - prompt（可选）：分析指令，默认描述图像内容
     * - language（可选）：返回语言，默认中文
     */
    @Override
    public String inputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "image_url": {
                      "type": "string",
                      "description": "图像的公开可访问URL（支持JPG、PNG、WebP格式）"
                    },
                    "prompt": {
                      "type": "string",
                      "description": "分析指令，如：描述这张图片、提取图中的文字、分析这个截图的问题"
                    },
                    "language": {
                      "type": "string",
                      "enum": ["zh", "en"],
                      "description": "返回语言：zh=中文（默认），en=英文"
                    }
                  },
                  "required": ["image_url"]
                }
                """;
    }

    /**
     * 工具执行入口
     *
     * @param args JSON 格式的参数字符串
     * @return 图像分析结果（纯文本，会直接返回给 LLM）
     */
    @Override
    public String execute(String args) {
        log.info("ImageExplainTool 开始执行，参数: {}", args);

        try {
            // 解析 JSON 参数
            JSONObject params = JSONUtil.parseObj(args);

            String imageUrl = params.getStr("image_url");
            String prompt = params.getStr("prompt", "请详细描述这张图片的内容");
            String language = params.getStr("language", "zh");

            // 参数校验
            if (imageUrl == null || imageUrl.isBlank()) {
                return "错误：image_url 参数不能为空，请提供图像的URL";
            }

            // 如果是中文，添加中文指令
            if ("zh".equals(language)) {
                prompt = prompt + "（请用中文回答）";
            }

            log.info("解析参数: imageUrl={}, prompt={}, language={}", imageUrl, prompt, language);

            // 调用图像解释 API
            String result = explainImage(imageUrl, prompt);

            log.info("ImageExplainTool 执行完成");
            return result;

        } catch (Exception e) {
            log.error("ImageExplainTool 执行异常", e);
            return "图像解释失败: " + e.getMessage();
        }
    }

    /**
     * 调用 Agnes 2.0 Flash API 解释图像
     * <p>
     * 使用图像 URL 输入功能，让模型分析图像内容。
     *
     * @param imageUrl 图像 URL
     * @param prompt   分析指令
     * @return 图像分析结果
     */
    private String explainImage(String imageUrl, String prompt) {
        String url = BASE_URL + CHAT_ENDPOINT;
        log.debug("请求图像解释API: {}", url);

        // 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.set("model", "agnes-2.0-flash");
        requestBody.set("temperature", 0.7);
        requestBody.set("max_tokens", 2048);

        // 构建 messages 数组
        JSONArray messages = new JSONArray();

        // System 消息
        JSONObject systemMessage = new JSONObject();
        systemMessage.set("role", "system");
        systemMessage.set("content", """
                你是一个专业的图像分析助手。你的任务是仔细分析用户提供的图像，并根据用户的要求提供详细的解释和分析。
                
                分析要点：
                - 整体内容和场景描述
                - 主要元素和物体识别
                - 文字内容提取（如有）
                - 颜色、布局、风格分析
                - 可能的问题或改进建议（如适用）
                
                请用清晰、结构化的方式组织你的回答。
                """);
        messages.add(systemMessage);

        // User 消息（包含图像 URL）
        JSONObject userMessage = new JSONObject();
        userMessage.set("role", "user");

        // 构建 content 数组（支持图像输入）
        JSONArray content = new JSONArray();

        // 文本部分
        JSONObject textContent = new JSONObject();
        textContent.set("type", "text");
        textContent.set("text", prompt);
        content.add(textContent);

        // 图像部分
        JSONObject imageContent = new JSONObject();
        imageContent.set("type", "image_url");
        JSONObject imageUrlObj = new JSONObject();
        imageUrlObj.set("url", imageUrl);
        imageContent.set("image_url", imageUrlObj);
        content.add(imageContent);

        userMessage.set("content", content);
        messages.add(userMessage);

        requestBody.set("messages", messages);

        log.debug("图像解释请求体: {}", requestBody);

        // 发起 HTTP POST 请求
        String response = HttpRequest.post(url)
                .header(Header.AUTHORIZATION, "Bearer " + API_KEY)
                .header(Header.CONTENT_TYPE, "application/json")
                .body(requestBody.toString())
                .timeout(60000)  // 超时 60 秒
                .execute()
                .body();

        log.debug("图像解释响应: {}", response);

        // 解析并返回结果
        return parseResponse(response);
    }

    /**
     * 解析 API 响应
     * <p>
     * 响应格式示例：
     * {
     * "id": "chatcmpl_xxx",
     * "choices": [
     * {
     * "message": {
     * "role": "assistant",
     * "content": "这张图片展示了..."
     * }
     * }
     * ]
     * }
     *
     * @param response API 响应 JSON 字符串
     * @return 解析后的图像分析结果
     */
    private String parseResponse(String response) {
        try {
            JSONObject json = JSONUtil.parseObj(response);

            // 检查是否有错误
            if (json.containsKey("error")) {
                JSONObject error = json.getJSONObject("error");
                String message = error.getStr("message", "未知错误");
                return "图像解释失败: " + message;
            }

            // 提取响应内容
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return "图像解释失败: 未获取到响应";
            }

            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.getJSONObject("message");
            String content = message.getStr("content", "");

            if (content.isBlank()) {
                return "图像解释失败: 响应内容为空";
            }

            // 获取 token 使用信息（可选）
            JSONObject usage = json.getJSONObject("usage");
            String tokenInfo = "";
            if (usage != null) {
                int promptTokens = usage.getInt("prompt_tokens", 0);
                int completionTokens = usage.getInt("completion_tokens", 0);
                tokenInfo = String.format("\n\n[Token 使用: 输入 %d, 输出 %d]", promptTokens, completionTokens);
            }

            return "【图像分析结果】\n\n" + content + tokenInfo;

        } catch (Exception e) {
            log.error("解析图像解释响应失败: {}", response, e);
            return "图像解释失败: 响应解析错误 - " + e.getMessage();
        }
    }
}
