package org.hai.work.tool;

/**
 * 自定义工具接口
 * <p>
 * 所有需要被 LLM 调用的工具都必须实现此接口。
 * 实现类会被 Spring 自动扫描并注册到 AiConfig.toolCallbackProvider() 中。
 * <p>
 * 与 @AgentTool + @Tool 注解方式的区别：
 * - @AgentTool + @Tool：Spring AI 原生方式，用注解标记方法即可，适合简单工具
 * - Tool 接口：自定义方式，完全控制 name、description、inputSchema、execute，适合复杂工具
 * <p>
 * 本项目的 WeatherTool 就是通过此接口注册的。
 * <p>
 * 接口方法说明：
 * - name()        → 工具名称，LLM 通过此名称调用工具（如 "weather_query"）
 * - description() → 工具描述，LLM 据此判断何时调用（越清晰越好）
 * - inputSchema() → 参数的 JSON Schema，告诉 LLM 参数格式
 * - execute()     → 工具执行逻辑，接收 JSON 参数，返回结果文本
 */
public interface Tool {

    /**
     * 工具名称（唯一标识）
     * LLM 在 tool_call 中使用此名称来指定要调用的工具
     */
    String name();

    /**
     * 工具功能描述
     * LLM 会阅读此描述来理解工具的能力，从而决定何时调用
     */
    String description();

    /**
     * 输入参数的 JSON Schema
     * 定义工具接受的参数、类型、是否必填等，LLM 会据此生成合法参数
     */
    String inputSchema();

    /**
     * 工具执行方法
     *
     * @param args JSON 格式的参数字符串，如 {"city":"上海","type":"current"}
     * @return 执行结果（纯文本，会直接返回给 LLM 用于生成最终回答）
     */
    String execute(String args);
}
