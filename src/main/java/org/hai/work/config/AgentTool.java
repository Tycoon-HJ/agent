package org.hai.work.config;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义工具标记注解
 * <p>
 * 用于标记一个类是 Agent 工具。被此注解标记的类会被 Spring 自动扫描，
 * 并在 AiConfig.toolCallbackProvider() 中注册为 Spring AI 的 ToolCallback。
 * <p>
 * 使用方式：
 *
 * @AgentTool public class UserTool {
 * @Tool(description = "查询用户信息")
 * public String findUser(String userId) { ... }
 * }
 * <p>
 * 注意：
 * - @AgentTool 上有 @Component 元注解，所以被标记的类会自动注册为 Spring Bean
 * - 类中的 @Tool 方法会被 MethodToolCallbackProvider 提取为工具方法
 * - 与 Tool 接口方式二选一，两种方式可以共存
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface AgentTool {
}
