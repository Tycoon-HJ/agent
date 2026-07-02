package org.hai.work;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 应用启动类
 * <p>
 * 启动后会自动完成：
 * 1. 扫描 @SpringBootApplication 下的所有组件（@Component、@Configuration 等）
 * 2. 自动配置 Spring AI（DeepSeek 模型、工具调用等）
 * 3. 注册所有 Bean（ChatClient、ChatMemory、Tool、Agent 等）
 * 4. 启动内嵌 Web 服务器（默认 8080 端口）
 * <p>
 * 启动后可访问：
 * GET http://localhost:8080/api/weather/ask?input=上海天气怎么样
 */
@SpringBootApplication
public class AgentApplication {

    static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
