package org.hai.work.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 通知处理器
 * <p>
 * 管理前端 WebSocket 连接，支持向指定用户推送定时任务执行结果。
 * <p>
 * 安全要求：
 * - 必须通过 HTTP 握手阶段的 JWT 鉴权
 * - userId 从已验证的 HTTP Session 属性中获取，不从 query 参数读取
 * - 拒绝匿名连接（不再回落到 default-user）
 */
@Slf4j
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    /**
     * HTTP 握手阶段设置的 Session 属性名：已验证的 userId
     */
    public static final String ATTR_AUTHENTICATED_USER_ID = "authenticatedUserId";

    /**
     * 活跃的 WebSocket 连接（userId → WebSocketSession）
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public NotificationWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = extractAuthenticatedUserId(session);
        if (userId == null) {
            log.warn("WebSocket 连接缺少已验证的 userId，关闭连接: sessionId={}", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("未认证"));
            return;
        }

        sessions.put(userId, session);
        log.info("WebSocket 连接建立: userId={}, sessionId={}, 当前活跃连接数: {}",
                userId, session.getId(), sessions.size());

        // 发送连接确认
        sendMessage(session, Map.of(
                "type", "connected",
                "message", "通知服务已连接",
                "userId", userId
        ));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("收到 WebSocket 消息: sessionId={}", session.getId());

        try {
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String type = (String) msg.get("type");

            // 心跳响应
            if ("ping".equals(type)) {
                sendMessage(session, Map.of("type", "pong"));
            }
        } catch (Exception e) {
            log.warn("解析 WebSocket 消息失败: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = extractAuthenticatedUserId(session);
        if (userId != null) {
            sessions.remove(userId);
        }
        log.info("WebSocket 连接关闭: userId={}, status={}, 当前活跃连接数: {}",
                userId, status, sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String userId = extractAuthenticatedUserId(session);
        if (userId != null) {
            sessions.remove(userId);
        }
        log.warn("WebSocket 传输错误: userId={}, error={}", userId, exception.getMessage());
    }

    /**
     * 向指定用户推送通知
     *
     * @param userId  目标用户ID
     * @param taskId  任务ID
     * @param content 通知内容
     */
    public void sendNotification(String userId, String taskId, String content) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) {
            log.warn("用户 [{}] 未连接 WebSocket，无法推送任务结果: taskId={}", userId, taskId);
            return;
        }

        try {
            sendMessage(session, Map.of(
                    "type", "scheduled_task_result",
                    "taskId", taskId,
                    "content", content
            ));
            log.info("推送 WebSocket 通知成功: userId={}, taskId={}", userId, taskId);
        } catch (Exception e) {
            log.error("推送 WebSocket 通知失败: userId={}, taskId={}", userId, taskId, e);
            sessions.remove(userId);
        }
    }

    /**
     * 检查用户是否在线
     */
    public boolean isOnline(String userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    /**
     * 发送 JSON 消息
     */
    private void sendMessage(WebSocketSession session, Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("发送 WebSocket 消息失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 从 WebSocket Session 属性中提取已认证的 userId
     * <p>
     * userId 由 HTTP 握手拦截器在 JWT 验证后设置，
     * 不从 query 参数读取（防止伪造）。
     */
    private String extractAuthenticatedUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get(ATTR_AUTHENTICATED_USER_ID);
        return userId instanceof String id ? id : null;
    }
}
