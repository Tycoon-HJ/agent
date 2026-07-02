package org.hai.work.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 跨域配置
 * <p>
 * 前端（Vite dev server）运行在 localhost:5173
 * 后端（Spring Boot）运行在 localhost:8080
 * 浏览器的同源策略会阻止跨域请求，需要后端允许跨域访问
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");            // 允许所有来源（生产环境应限制）
        config.addAllowedMethod("*");                   // 允许所有 HTTP 方法
        config.addAllowedHeader("*");                   // 允许所有请求头
        config.setAllowCredentials(true);               // 允许携带 Cookie
        config.setMaxAge(3600L);                        // 预检请求缓存 1 小时

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
