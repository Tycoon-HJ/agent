package org.hai.work.tool.impl;

import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.hai.work.config.AgentTool;
import org.springframework.ai.tool.annotation.Tool;

/**
 * AI 图像生成工具
 * <p>
 * 基于 Agnes Image 2.1 Flash 模型，支持：
 * - 文生图（Text-to-Image）：根据文本提示词生成图像
 * - 图生图（Image-to-Image）：根据提示词转换或优化现有图像
 * <p>
 * API 文档：https://apihub.agnes-ai.com
 * 模型名称：agnes-image-2.1-flash
 * <p>
 * 返回值：生成的图像 URL 或错误信息
 */
@Slf4j
@AgentTool
public class ImageTool {

    /**
     * Agnes Image API 基础地址
     */
    private static final String BASE_URL = "https://apihub.agnes-ai.com";

    /**
     * 图像生成端点
     */
    private static final String ENDPOINT = "/v1/images/generations";

    /**
     * API Key（从环境变量或配置中读取）
     */
    private static final String API_KEY = System.getenv("AGNES_API_KEY");

    /**
     * 文生图
     * <p>
     * 根据文本提示词生成图像。适用于创意设计、营销内容、社交媒体素材等场景。
     *
     * @param prompt 图像生成的文本指令，建议包含：主体 + 场景 + 风格 + 光照 + 构图 + 质量要求
     * @param size   输出图像尺寸，可选值：1024x1024, 1024x768, 768x1024, 512x512
     * @return 生成的图像URL或错误信息
     */
    @Tool(description = "AI文生图工具。根据文本描述生成图像。参数：prompt=图像描述（英文效果更佳），size=图像尺寸（1024x1024/1024x768/768x1024/512x512）。返回图像URL。")
    public String textToImage(String prompt, String size) {
        log.info("执行文生图: prompt={}, size={}", prompt, size);

        if (API_KEY == null || API_KEY.isBlank()) {
            return "错误：AGNES_API_KEY 环境变量未设置，无法调用图像生成服务";
        }

        // 参数校验
        if (prompt == null || prompt.isBlank()) {
            return "错误：prompt 参数不能为空，请描述要生成的图像内容";
        }
        if (size == null || size.isBlank()) {
            size = "1024x768";  // 默认尺寸
        }

        try {
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.set("model", "agnes-image-2.1-flash");
            requestBody.set("prompt", prompt);
            requestBody.set("size", size);

            // 设置返回格式为 URL
            JSONObject extraBody = new JSONObject();
            extraBody.set("response_format", "url");
            requestBody.set("extra_body", extraBody);

            log.debug("文生图请求体: {}", requestBody);

            // 发起 HTTP POST 请求
            String response = HttpRequest.post(BASE_URL + ENDPOINT)
                    .header(Header.AUTHORIZATION, "Bearer " + API_KEY)
                    .header(Header.CONTENT_TYPE, "application/json")
                    .body(requestBody.toString())
                    .timeout(120000)  // 超时 120 秒
                    .execute()
                    .body();

            log.debug("文生图响应: {}", response);
            return parseImageResponse(response);

        } catch (Exception e) {
            log.error("文生图执行异常", e);
            return "图像生成失败: " + e.getMessage();
        }
    }

    /**
     * 图生图（URL输入）
     * <p>
     * 根据提示词转换或优化现有图像，保留原始构图。
     * 适用于风格迁移、场景重打光、背景变换等场景。
     *
     * @param prompt   图像编辑指令，需明确说明要改变什么、保留什么
     * @param size     输出图像尺寸
     * @param imageUrl 输入图像的公共URL（支持HTTPS）
     * @return 生成的图像URL或错误信息
     */
    @Tool(description = "AI图生图工具(URL)。根据提示词转换现有图像，保留原始构图。参数：prompt=编辑指令（需说明改变和保留的内容），size=输出尺寸，imageUrl=输入图像的公共HTTPS URL。返回图像URL。")
    public String imageToImage(String prompt, String size, String imageUrl) {
        log.info("执行图生图(URL): prompt={}, size={}, imageUrl={}", prompt, size, imageUrl);

        // 参数校验
        if (prompt == null || prompt.isBlank()) {
            return "错误：prompt 参数不能为空，请描述图像编辑指令";
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            return "错误：imageUrl 参数不能为空，请提供输入图像的URL";
        }
        if (size == null || size.isBlank()) {
            size = "1024x768";
        }

        try {
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.set("model", "agnes-image-2.1-flash");
            requestBody.set("prompt", prompt);
            requestBody.set("size", size);

            // 构建 extra_body（包含 image 和 response_format）
            JSONObject extraBody = new JSONObject();
            extraBody.set("image", new String[]{imageUrl});
            extraBody.set("response_format", "url");
            requestBody.set("extra_body", extraBody);

            log.debug("图生图请求体: {}", requestBody);

            // 发起 HTTP POST 请求
            String response = HttpRequest.post(BASE_URL + ENDPOINT)
                    .header(Header.AUTHORIZATION, "Bearer " + API_KEY)
                    .header(Header.CONTENT_TYPE, "application/json")
                    .body(requestBody.toString())
                    .timeout(180000)  // 超时 180 秒（图生图可能更慢）
                    .execute()
                    .body();

            log.debug("图生图响应: {}", response);
            return parseImageResponse(response);

        } catch (Exception e) {
            log.error("图生图执行异常", e);
            return "图像生成失败: " + e.getMessage();
        }
    }

    /**
     * 图生图（Base64输入）
     * <p>
     * 支持直接传入 Base64 编码的图像数据进行图生图。
     * 适用于前端粘贴图片、截图编辑等场景。
     *
     * @param prompt      图像编辑指令
     * @param size        输出图像尺寸
     * @param base64Image Base64 编码的图像数据（可带或不带 data:image/xxx;base64, 前缀）
     * @return 生成的图像URL或错误信息
     */
    @Tool(description = "AI图生图工具(Base64)。支持直接传入Base64图像数据进行编辑。参数：prompt=编辑指令，size=输出尺寸，base64Image=Base64编码的图像数据。返回图像URL。")
    public String imageToImageBase64(String prompt, String size, String base64Image) {
        log.info("执行图生图(Base64): prompt={}, size={}", prompt, size);

        // 参数校验
        if (prompt == null || prompt.isBlank()) {
            return "错误：prompt 参数不能为空，请描述图像编辑指令";
        }
        if (base64Image == null || base64Image.isBlank()) {
            return "错误：base64Image 参数不能为空，请提供 Base64 编码的图像数据";
        }
        if (size == null || size.isBlank()) {
            size = "1024x768";
        }

        try {
            // 处理 Base64 数据格式
            // 如果不包含 data: 前缀，自动添加默认的 PNG 格式前缀
            String imageData = base64Image.trim();
            if (!imageData.startsWith("data:")) {
                imageData = "data:image/png;base64," + imageData;
            }

            log.debug("Base64 图像数据长度: {} 字符", imageData.length());

            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.set("model", "agnes-image-2.1-flash");
            requestBody.set("prompt", prompt);
            requestBody.set("size", size);

            // 构建 extra_body（包含 image 和 response_format）
            JSONObject extraBody = new JSONObject();
            extraBody.set("image", new String[]{imageData});
            extraBody.set("response_format", "url");
            requestBody.set("extra_body", extraBody);

            log.debug("图生图(Base64)请求体大小: {} bytes", requestBody.toString().length());

            // 发起 HTTP POST 请求
            String response = HttpRequest.post(BASE_URL + ENDPOINT)
                    .header(Header.AUTHORIZATION, "Bearer " + API_KEY)
                    .header(Header.CONTENT_TYPE, "application/json")
                    .body(requestBody.toString())
                    .timeout(180000)  // 超时 180 秒
                    .execute()
                    .body();

            log.debug("图生图(Base64)响应: {}", response);
            return parseImageResponse(response);

        } catch (Exception e) {
            log.error("图生图(Base64)执行异常", e);
            return "图像生成失败: " + e.getMessage();
        }
    }

    /**
     * 解析图像生成 API 响应
     * <p>
     * 成功响应格式：
     * {
     * "created": 1780000000,
     * "data": [
     * {
     * "url": "https://storage.googleapis.com/agnes-aigc/xxx.png",
     * "b64_json": null,
     * "revised_prompt": null
     * }
     * ]
     * }
     *
     * @param response API 响应 JSON 字符串
     * @return 解析后的结果文本
     */
    private String parseImageResponse(String response) {
        try {
            JSONObject json = JSONUtil.parseObj(response);

            // 检查是否有错误
            if (json.containsKey("error")) {
                JSONObject error = json.getJSONObject("error");
                String message = error.getStr("message", "未知错误");
                return "图像生成失败: " + message;
            }

            // 提取图像 URL
            var dataArray = json.getJSONArray("data");
            if (dataArray == null || dataArray.isEmpty()) {
                return "图像生成失败: 响应中没有图像数据";
            }

            JSONObject firstImage = dataArray.getJSONObject(0);
            String imageUrl = firstImage.getStr("url");

            if (imageUrl != null && !imageUrl.isBlank()) {
                return "图像生成成功！\n图像URL: " + imageUrl;
            }

            // 如果返回的是 Base64
            String b64Json = firstImage.getStr("b64_json");
            if (b64Json != null && !b64Json.isBlank()) {
                return "图像生成成功！（Base64格式，长度: " + b64Json.length() + " 字符）";
            }

            return "图像生成完成，但未能提取到图像数据";

        } catch (Exception e) {
            log.error("解析图像响应失败: {}", response, e);
            return "图像生成失败: 响应解析错误 - " + e.getMessage();
        }
    }
}
