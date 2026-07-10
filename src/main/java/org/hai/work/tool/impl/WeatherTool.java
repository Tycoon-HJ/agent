package org.hai.work.tool.impl;

import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hai.work.tool.Tool;
import org.hai.work.util.JwtUtil;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 天气查询工具
 * <p>
 * 调用和风天气 API 获取天气数据，支持当前天气和 7 天预报。
 */
@Component
@Slf4j
public class WeatherTool implements Tool {

    private static final String BASE_URL = "https://q53qqjvuay.re.qweatherapi.com";
    private static final int HTTP_TIMEOUT_MS = 20000;
    private static final int MAX_RESPONSE_LOG_LENGTH = 500;

    private final ObjectMapper objectMapper;

    public WeatherTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "weather_query";
    }

    @Override
    public String description() {
        return "查询天气信息。type=current 获取当前天气，type=forecast 获取未来7天天气预报（用于出行规划）。";
    }

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

    @Override
    public String execute(String args) {
        log.info("WeatherTool 开始执行");

        try {
            // 使用 Jackson 解析 JSON 参数
            Map<String, Object> parsed = objectMapper.readValue(args, Map.class);

            String city = (String) parsed.getOrDefault("city", "上海");
            String type = (String) parsed.getOrDefault("type", "forecast");

            // 参数校验
            if (city == null || city.isBlank()) {
                return "错误：city 参数不能为空";
            }
            if (type == null || type.isBlank()) {
                type = "forecast";
            }
            if (!"current".equals(type) && !"forecast".equals(type)) {
                return "错误：type 参数必须为 current 或 forecast";
            }

            log.info("解析参数: city={}, type={}", city, type);

            String location = resolveLocation(city);
            log.info("城市 [{}] 解析为 locationCode: {}", city, location);

            String result;
            if ("current".equals(type)) {
                result = fetchCurrentWeather(location, city);
            } else {
                result = fetchForecast(location, city);
            }

            log.info("WeatherTool 执行完成");
            return result;

        } catch (Exception e) {
            log.error("WeatherTool 执行异常", e);
            return "天气查询失败: " + e.getMessage();
        }
    }

    private String fetchCurrentWeather(String location, String city) {
        String url = BASE_URL + "/v7/weather/now?location=" + location;
        log.debug("请求当前天气: {}", url);

        String response = HttpRequest.get(url)
                .header(Header.AUTHORIZATION, JwtUtil.createToken())
                .timeout(HTTP_TIMEOUT_MS)
                .execute()
                .body();

        log.debug("当前天气响应: {}", truncate(response));
        return "【" + city + " 当前天气】\n" + response;
    }

    private String fetchForecast(String location, String city) {
        String url = BASE_URL + "/v7/weather/7d?location=" + location;
        log.debug("请求7天预报: {}", url);

        String response = HttpRequest.get(url)
                .header(Header.AUTHORIZATION, JwtUtil.createToken())
                .timeout(HTTP_TIMEOUT_MS)
                .execute()
                .body();

        log.debug("7天预报响应: {}", truncate(response));
        return "【" + city + " 未来7天天气预报】\n" + response;
    }

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
     * 截断过长的日志内容
     */
    private String truncate(String text) {
        if (text == null) return "null";
        if (text.length() <= MAX_RESPONSE_LOG_LENGTH) return text;
        return text.substring(0, MAX_RESPONSE_LOG_LENGTH) + "... (truncated)";
    }
}
