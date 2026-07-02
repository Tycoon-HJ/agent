package org.hai.work.tool.impl;

import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.hai.work.tool.Tool;
import org.hai.work.util.JwtUtil;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 天气查询工具
 * <p>
 * 这是项目中第一个真正的业务工具，实现了 Tool 接口。
 * LLM（DeepSeek）可以通过 tool_call 调用此工具获取天气数据。
 * <p>
 * 支持两种查询类型：
 * - current  → 获取当前天气（实时温度、天气状况、风力等）
 * - forecast → 获取未来7天天气预报（用于出行规划）
 * <p>
 * 工作流程：
 * LLM 决定需要查天气 → 发起 tool_call(weather_query, {city:"上海", type:"current"})
 * → Spring AI 拦截 tool_call → 调用本类 execute() 方法
 * → 请求和风天气 API → 返回天气数据给 LLM
 * → LLM 整合天气数据生成最终回答
 * <p>
 * 底层依赖：
 * 和风天气 API（https://dev.qweather.com）
 * 通过 HTTP 请求获取天气数据，返回 JSON 格式
 */
@Component
@Slf4j
public class WeatherTool implements Tool {

    /**
     * 和风天气 API 基础地址
     */
    private static final String BASE_URL = "https://q53qqjvuay.re.qweatherapi.com";

    /**
     * 工具名称
     * LLM 通过此名称识别和调用工具。在 prompt 中也会引用此名称。
     */
    @Override
    public String name() {
        return "weather_query";
    }

    /**
     * 工具描述
     * LLM 会阅读此描述来判断何时调用此工具。
     * 描述越清晰，LLM 越能准确判断调用时机。
     */
    @Override
    public String description() {
        return "查询天气信息。type=current 获取当前天气，type=forecast 获取未来7天天气预报（用于出行规划）。";
    }

    /**
     * 输入参数的 JSON Schema
     * <p>
     * 告诉 LLM 这个工具接受哪些参数、每个参数的类型和含义。
     * LLM 会根据此 schema 自动生成符合格式的参数。
     * <p>
     * 参数说明：
     * - city（必填）：城市名称，如 "上海"、"北京"、"东京"
     * - type（必填）：查询类型
     * - "current" → 当前实时天气
     * - "forecast" → 未来7天预报
     */
    @Override
    public String inputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "city": {
                      "type": "string",
                      "description": "城市名称，如 上海、北京、东京"
                    },
                    "type": {
                      "type": "string",
                      "enum": ["current", "forecast"],
                      "description": "查询类型：current=当前天气，forecast=未来7天预报"
                    }
                  },
                  "required": ["city", "type"]
                }
                """;
    }

    /**
     * 工具执行入口
     * <p>
     * 当 LLM 发起 tool_call 时，Spring AI 会调用此方法。
     *
     * @param args JSON 格式的参数字符串，如 {"city":"上海","type":"current"}
     * @return 天气查询结果（纯文本，会直接返回给 LLM）
     */
    @Override
    public String execute(String args) {
        log.info("WeatherTool 开始执行，参数: {}", args);

        // 解析 JSON 参数（使用简化解析器）
        Map<String, Object> parsed = parse(args);

        // 提取参数，设置默认值
        String city = (String) parsed.getOrDefault("city", "上海");
        String type = (String) parsed.getOrDefault("type", "forecast");
        log.info("解析参数: city={}, type={}", city, type);

        // 将中文城市名转换为和风天气 API 的 locationCode
        // 例如："上海" → "101020600"，"北京" → "101010100"
        String location = resolveLocation(city);
        log.info("城市 [{}] 解析为 locationCode: {}", city, location);

        // 根据查询类型调用不同的 API
        String result;
        if ("current".equals(type)) {
            result = fetchCurrentWeather(location, city);
        } else {
            result = fetchForecast(location, city);
        }

        log.info("WeatherTool 执行完成");
        return result;
    }

    /**
     * 获取当前实时天气
     * <p>
     * 调用和风天气 /v7/weather/now 接口，返回：
     * - 当前温度、体感温度
     * - 天气状况（晴/多云/雨等）
     * - 风向、风力等级
     * - 湿度、气压等
     *
     * @param location 城市 locationCode（如 "101020600"）
     * @param city     城市中文名（用于日志和返回文本）
     * @return 当前天气信息
     */
    private String fetchCurrentWeather(String location, String city) {
        String url = BASE_URL + "/v7/weather/now?location=" + location;
        log.debug("请求当前天气: {}", url);

        // 发起 HTTP GET 请求，携带 JWT 鉴权 token
        String response = HttpRequest.get(url)
                .header(Header.AUTHORIZATION, JwtUtil.createToken())
                .timeout(20000)  // 超时 20 秒
                .execute()
                .body();

        log.debug("当前天气响应: {}", response);
        return "【" + city + " 当前天气】\n" + response;
    }

    /**
     * 获取未来7天天气预报
     * <p>
     * 调用和风天气 /v7/weather/7d 接口，返回未来7天的：
     * - 每天最高/最低温度
     * - 每天天气状况
     * - 日出日落时间
     * - 风向风力等
     *
     * @param location 城市 locationCode
     * @param city     城市中文名
     * @return 7天天气预报信息
     */
    private String fetchForecast(String location, String city) {
        String url = BASE_URL + "/v7/weather/7d?location=" + location;
        log.debug("请求7天预报: {}", url);

        String response = HttpRequest.get(url)
                .header(Header.AUTHORIZATION, JwtUtil.createToken())
                .timeout(20000)
                .execute()
                .body();

        log.debug("7天预报响应: {}", response);
        return "【" + city + " 未来7天天气预报】\n" + response;
    }

    /**
     * 城市名 → 和风天气 locationCode 映射
     * <p>
     * 和风天气 API 使用数字编码标识城市，而不是中文名。
     * 这里维护一个常用城市的映射表。
     * <p>
     * 完整城市列表参考：https://dev.qweather.com/docs/api/geo/city-lookup/
     *
     * @param city 城市中文名
     * @return locationCode，未知城市默认返回上海
     */
    private String resolveLocation(String city) {
        return switch (city) {
            case "上海" -> "101020600";
            case "北京" -> "101010100";
            case "广州" -> "101280101";
            case "深圳" -> "101280601";
            case "杭州" -> "101210101";
            case "成都" -> "101270101";
            case "武汉" -> "101200101";
            case "南京" -> "101190101";
            case "重庆" -> "101040100";
            case "西安" -> "101110101";
            case "东京" -> "101030100";
            case "大阪" -> "101030200";
            case "首尔" -> "101090100";
            case "纽约" -> "101090500";
            case "伦敦" -> "101130100";
            case "巴黎" -> "101060100";
            default -> {
                log.warn("未知城市 [{}]，使用默认 locationCode(上海)", city);
                yield "101020600";
            }
        };
    }

    /**
     * 简化版 JSON 解析
     * <p>
     * 将 {"city":"上海","type":"current"} 解析为 Map。
     * 注意：这是非严格的解析方式，适用于简单场景。
     * 如果参数值中包含逗号或冒号，可能会解析错误。
     * 生产环境建议使用 Jackson ObjectMapper。
     *
     * @param args JSON 字符串
     * @return 解析后的键值对
     */
    private Map<String, Object> parse(String args) {
        try {
            // 去掉 JSON 格式符号，按逗号分割
            args = args.replace("{", "")
                    .replace("}", "")
                    .replace("\"", "");

            String[] parts = args.split(",");
            Map<String, Object> map = new java.util.HashMap<>();

            for (String part : parts) {
                if (!part.contains(":")) continue;
                String[] kv = part.split(":");
                map.put(kv[0].trim(), kv[1].trim());
            }

            return map;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
