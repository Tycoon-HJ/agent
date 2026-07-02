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
 * 表情包搜索工具
 * <p>
 * 基于 ALAPI 斗图 API 实现表情包搜索功能。
 * LLM 可以通过此工具搜索并返回表情包图片 URL。
 * <p>
 * API 文档：https://www.alapi.cn/api/view/96
 * <p>
 * 功能特性：
 * - 根据关键词搜索表情包
 * - 返回表情包图片 URL
 * - 支持随机返回多张表情包
 * <p>
 * 工作流程：
 * LLM 决定需要表情包 → 发起 tool_call(search_meme, {keyword:"搞笑"})
 * → Spring AI 拦截 tool_call → 调用本类 execute() 方法
 * → 请求 ALAPI API → 返回表情包 URL 给 LLM
 * → LLM 将表情包 URL 嵌入回答中
 */
@Component
@Slf4j
public class MemeTool implements Tool {

    /**
     * ALAPI 斗图 API 基础地址
     */
    private static final String BASE_URL = "https://v3.alapi.cn/api/doutu";

    /**
     * API Token
     */
    private static final String API_TOKEN = System.getenv("ALAPI_TOKEN") != null
            ? System.getenv("ALAPI_TOKEN")
            : "";

    /**
     * 工具名称
     * LLM 通过此名称识别和调用工具
     */
    @Override
    public String name() {
        return "search_meme";
    }

    /**
     * 工具描述
     * LLM 会阅读此描述来判断何时调用此工具
     */
    @Override
    public String description() {
        return """
                表情包搜索工具。根据关键词搜索表情包图片。
                适用场景：
                - 用户想要斗图、发表情
                - 需要活跃聊天氛围
                - 回复中需要表情包点缀
                返回表情包图片URL，可直接在聊天中展示。
                """;
    }

    /**
     * 输入参数的 JSON Schema
     * <p>
     * 参数说明：
     * - keyword（必填）：搜索关键词，如"搞笑"、"无语"、"开心"
     * - num（可选）：返回数量，默认 1，最大 10
     */
    @Override
    public String inputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "keyword": {
                      "type": "string",
                      "description": "表情包搜索关键词，如：搞笑、无语、开心、哭笑不得、点赞、加油、晚安、摸鱼、干饭、社恐"
                    },
                    "num": {
                      "type": "integer",
                      "description": "返回表情包数量，默认1，最大10"
                    }
                  },
                  "required": ["keyword"]
                }
                """;
    }

    /**
     * 工具执行入口
     *
     * @param args JSON 格式的参数字符串，如 {"keyword":"搞笑","num":3}
     * @return 表情包图片URL（纯文本，会直接返回给 LLM）
     */
    @Override
    public String execute(String args) {
        log.info("MemeTool 开始执行，参数: {}", args);

        try {
            // 解析 JSON 参数
            JSONObject params = JSONUtil.parseObj(args);

            String keyword = params.getStr("keyword");
            int num = params.getInt("num", 1);

            // 参数校验
            if (keyword == null || keyword.isBlank()) {
                return "错误：keyword 参数不能为空，请提供表情包搜索关键词";
            }

            // 限制返回数量
            if (num > 10) {
                num = 10;
            }
            if (num < 1) {
                num = 1;
            }

            log.info("解析参数: keyword={}, num={}", keyword, num);

            // 调用搜索 API
            String result = searchMeme(keyword, num);

            log.info("MemeTool 执行完成");
            return result;

        } catch (Exception e) {
            log.error("MemeTool 执行异常", e);
            return "表情包搜索失败: " + e.getMessage();
        }
    }

    /**
     * 搜索表情包
     * <p>
     * 调用 ALAPI 斗图 API 获取表情包图片。
     *
     * @param keyword 搜索关键词
     * @param num     返回数量
     * @return 格式化的表情包结果
     */
    private String searchMeme(String keyword, int num) {
        // 构建请求 URL（GET 请求，参数拼接在 URL 中）
        String url = BASE_URL + "?token=" + API_TOKEN
                + "&keyword=" + cn.hutool.core.util.URLUtil.encode(keyword)
                + "&num=" + num
                + "&type=json";

        log.debug("请求表情包API: {}", url);

        // 发起 HTTP GET 请求
        String response = HttpRequest.get(url)
                .header(Header.CONTENT_TYPE, "application/json")
                .timeout(15000)  // 超时 15 秒
                .execute()
                .body();

        log.debug("表情包响应: {}", response);

        // 解析并格式化结果
        return formatMemeResult(response, keyword);
    }

    /**
     * 格式化表情包结果
     * <p>
     * 将 ALAPI 返回的 JSON 格式化为 LLM 易于理解的文本格式。
     * <p>
     * 响应格式示例：
     * {
     * "code": 200,
     * "msg": "success",
     * "data": [
     * "https://xxx.com/meme1.jpg",
     * "https://xxx.com/meme2.jpg"
     * ]
     * }
     *
     * @param response API 响应 JSON 字符串
     * @param keyword  原始搜索词
     * @return 格式化的表情包结果文本
     */
    private String formatMemeResult(String response, String keyword) {
        try {
            JSONObject json = JSONUtil.parseObj(response);

            // 检查响应状态
            int code = json.getInt("code", 0);
            if (code != 200) {
                String msg = json.getStr("msg", "未知错误");
                return "表情包搜索失败: " + msg;
            }

            // 获取表情包 URL 列表
            JSONArray data = json.getJSONArray("data");
            if (data == null || data.isEmpty()) {
                return "未找到关键词「" + keyword + "」相关的表情包，换个关键词试试吧~";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("【表情包搜索结果】关键词: ").append(keyword).append("\n");
            sb.append("共找到 ").append(data.size()).append(" 张表情包：\n\n");

            // 遍历每张表情包 URL
            for (int i = 0; i < data.size(); i++) {
                String imageUrl = data.getStr(i);
                if (imageUrl != null && !imageUrl.isBlank()) {
                    sb.append("表情包 ").append(i + 1).append(": ").append(imageUrl).append("\n");
                }
            }

            sb.append("\n提示：以上表情包图片可直接在聊天中展示。");

            return sb.toString();

        } catch (Exception e) {
            log.error("解析表情包结果失败: {}", response, e);
            return "表情包结果解析失败: " + e.getMessage();
        }
    }
}
