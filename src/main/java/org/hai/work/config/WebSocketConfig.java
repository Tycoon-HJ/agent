package org.hai.work.config;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.service.NotificationWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 配置
 * <p>
 * 注册 WebSocket 端点，前端通过此端点建立长连接接收定时任务通知。
 * <p>
 * 握手阶段从 query 参数提取 userId 并校验非空，
 * 通过后存入 Session attributes 供 Handler 使用。
 */
@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler handler;

    public WebSocketConfig(NotificationWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/notifications")
                .addInterceptors(new UserIdHandshakeInterceptor())
                .setAllowedOrigins("http://localhost:*", "http://127.0.0.1:*");
    }

    /**
     * 握手拦截器：从 query 参数提取并校验 userId
     */
    @Slf4j
    private static class UserIdHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String userId = extractParam(request.getURI(), "userId");

            if (userId == null || userId.isBlank()) {
                log.warn("WebSocket 握手失败: 缺少 userId, remote={}", request.getRemoteAddress());
                return false;
            }

            attributes.put(NotificationWebSocketHandler.ATTR_AUTHENTICATED_USER_ID, userId);
            log.info("WebSocket 握手成功: userId={}, remote={}", userId, request.getRemoteAddress());
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }

        private String extractParam(URI uri, String name) {
            String query = uri.getQuery();
            if (query == null) return null;
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && name.equals(kv[0])) {
                    return kv[1];
                }
            }
            return null;
        }
    }
}
