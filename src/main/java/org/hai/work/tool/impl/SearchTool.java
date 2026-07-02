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
 * 网络搜索工具
 * <p>
 * 基于 Tavily Search API 实现实时网络搜索功能。
 * LLM 可以通过此工具获取最新的网络信息，用于回答时效性问题。
 * <p>
 * API 文档：https://docs.tavily.com
 * <p>
 * 功能特性：
 * - 支持关键词搜索
 * - 支持高级搜索深度（获取更详细的结果）
 * - 返回标题、URL、内容摘要等结构化数据
 * <p>
 * 工作流程：
 * LLM 决定需要搜索 → 发起 tool_call(web_search, {query:"最新科技新闻"})
 * → Spring AI 拦截 tool_call → 调用本类 execute() 方法
 * → 请求 Tavily API → 返回搜索结果给 LLM
 * → LLM 整合搜索结果生成最终回答
 */
@Component
@Slf4j
public class SearchTool implements Tool {

    /**
     * Tavily API 基础地址
     */
    private static final String BASE_URL = "https://api.tavily.com";

    /**
     * 搜索端点
     */
    private static final String SEARCH_ENDPOINT = "/search";

    /**
     * API Key（从环境变量或配置中读取）
     */
    private static final String API_KEY = System.getenv("TAVILY_API_KEY") != null
            ? System.getenv("TAVILY_API_KEY")
            : "";

    /**
     * 工具名称
     * LLM 通过此名称识别和调用工具
     */
    @Override
    public String name() {
        return "web_search";
    }

    /**
     * 工具描述
     * LLM 会阅读此描述来判断何时调用此工具
     */
    @Override
    public String description() {
        return """
                实时网络搜索工具。用于获取最新的网络信息。
                适用场景：
                - 查询最新新闻、时事
                - 获取实时数据（股价等）
                - 搜索技术文档、教程
                - 查询产品信息、评价
                - 任何需要最新信息的问题
                返回搜索结果的标题、URL和内容摘要。
                """;
    }

    /**
     * 输入参数的 JSON Schema
     * <p>
     * 参数说明：
     * - query（必填）：搜索关键词
     * - search_depth（可选）：搜索深度，"basic" 或 "advanced"
     * - max_results（可选）：最大返回结果数量，默认 5
     */
    @Override
    public String inputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string",
                      "description": "搜索关键词或问题"
                    },
                    "search_depth": {
                      "type": "string",
                      "enum": ["basic", "advanced"],
                      "description": "搜索深度：basic=基础搜索（快速），advanced=高级搜索（更详细）"
                    },
                    "max_results": {
                      "type": "integer",
                      "description": "最大返回结果数量，默认5，最大10"
                    }
                  },
                  "required": ["query"]
                }
                """;
    }

    /**
     * 工具执行入口
     *
     * @param args JSON 格式的参数字符串，如 {"query":"最新科技新闻","search_depth":"advanced"}
     * @return 搜索结果（纯文本，会直接返回给 LLM）
     */
    @Override
    public String execute(String args) {
        log.info("SearchTool 开始执行，参数: {}", args);

        try {
            // 解析 JSON 参数
            JSONObject params = JSONUtil.parseObj(args);

            String query = params.getStr("query");
            String searchDepth = params.getStr("search_depth", "basic");
            int maxResults = params.getInt("max_results", 5);

            // 参数校验
            if (query == null || query.isBlank()) {
                return "错误：query 参数不能为空，请提供搜索关键词";
            }

            // 限制最大结果数
            if (maxResults > 10) {
                maxResults = 10;
            }

            log.info("解析参数: query={}, searchDepth={}, maxResults={}", query, searchDepth, maxResults);

            // 调用搜索 API
            String result = search(query, searchDepth, maxResults);

            log.info("SearchTool 执行完成");
            return result;

        } catch (Exception e) {
            log.error("SearchTool 执行异常", e);
            return "搜索失败: " + e.getMessage();
        }
    }

    /**
     * 执行搜索
     * <p>
     * 调用 Tavily Search API 获取搜索结果。
     *
     * @param query       搜索关键词
     * @param searchDepth 搜索深度（basic/advanced）
     * @param maxResults  最大结果数
     * @return 格式化的搜索结果
     */
    private String search(String query, String searchDepth, int maxResults) {
        String url = BASE_URL + SEARCH_ENDPOINT;
        log.debug("请求搜索API: {}", url);

        // 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.set("query", query);
        requestBody.set("search_depth", searchDepth);
        requestBody.set("max_results", maxResults);
        requestBody.set("include_answer", true);  // 包含总结答案
        requestBody.set("include_raw_content", false);  // 不包含原始HTML内容

        log.debug("搜索请求体: {}", requestBody);

        // 发起 HTTP POST 请求
        String response = HttpRequest.post(url)
                .header(Header.AUTHORIZATION, "Bearer " + API_KEY)
                .header(Header.CONTENT_TYPE, "application/json")
                .body(requestBody.toString())
                .timeout(30000)  // 超时 30 秒
                .execute()
                .body();

        log.debug("搜索响应: {}", response);

        // 解析并格式化搜索结果
        return formatSearchResult(response, query);
    }

    /**
     * 格式化搜索结果
     * <p>
     * 将 Tavily API 返回的 JSON 格式化为 LLM 易于理解的文本格式。
     * <p>
     * 响应格式示例：
     * {
     * "query": "...",
     * "answer": "AI生成的总结答案",
     * "results": [
     * {
     * "title": "...",
     * "url": "...",
     * "content": "...",
     * "score": 0.95
     * }
     * ]
     * }
     *
     * @param response API 响应 JSON 字符串
     * @param query    原始搜索词
     * @return 格式化的搜索结果文本
     */
    private String formatSearchResult(String response, String query) {
        try {
            JSONObject json = JSONUtil.parseObj(response);

            // 检查是否有错误
            if (json.containsKey("error")) {
                String error = json.getStr("error");
                return "搜索失败: " + error;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("【搜索结果】关键词: ").append(query).append("\n\n");

            // 获取 AI 总结答案（如果有）
            String answer = json.getStr("answer");
            if (answer != null && !answer.isBlank()) {
                sb.append("【AI总结】\n").append(answer).append("\n\n");
            }

            // 获取搜索结果列表
            JSONArray results = json.getJSONArray("results");
            if (results == null || results.isEmpty()) {
                sb.append("未找到相关搜索结果。");
                return sb.toString();
            }

            sb.append("【详细结果】共 ").append(results.size()).append(" 条\n");
            sb.append("=".repeat(50)).append("\n\n");

            // 遍历每条搜索结果
            for (int i = 0; i < results.size(); i++) {
                JSONObject result = results.getJSONObject(i);

                String title = result.getStr("title", "无标题");
                String resultUrl = result.getStr("url", "");
                String content = result.getStr("content", "");
                double score = result.getDouble("score", 0.0);

                sb.append(String.format("%d. %s\n", i + 1, title));
                sb.append("   链接: ").append(resultUrl).append("\n");

                // 截断过长的内容
                if (content.length() > 500) {
                    content = content.substring(0, 500) + "...";
                }
                sb.append("   摘要: ").append(content).append("\n");
                sb.append("   相关度: ").append(String.format("%.1f%%", score * 100)).append("\n\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("解析搜索结果失败: {}", response, e);
            return "搜索结果解析失败: " + e.getMessage();
        }
    }
}
