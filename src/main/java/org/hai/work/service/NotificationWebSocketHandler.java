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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebSocket 通知处理器
 * <p>
 * 管理前端 WebSocket 连接，支持向指定用户推送定时任务执行结果。
 * <p>
 * 连接协议：
 * - 前端连接: ws://host/ws/notifications?userId=xxx
 * - 连接建立后，服务端发送: {"type":"connected","message":"通知服务已连接"}
 * - 任务完成时，服务端推送: {"type":"scheduled_task_result","taskId":"xxx","content":"xxx"}
 * - 前端发送: {"type":"ping"} → 服务端回复: {"type":"pong"}
 */
@Slf4j
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {
    private static final Pattern USER_ID_PATTERN = Pattern.compile("(^|&)userId=([^&]+)");

    /**
     * 活跃的 WebSocket 连接（userId → WebSocketSession）
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = extractUserId(session);
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
        log.debug("收到 WebSocket 消息: sessionId={}, payload={}", session.getId(), payload);

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
        String userId = extractUserId(session);
        sessions.remove(userId);
        log.info("WebSocket 连接关闭: userId={}, status={}, 当前活跃连接数: {}",
                userId, status, sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String userId = extractUserId(session);
        sessions.remove(userId);
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
     * 从 WebSocket URL 参数中提取 userId
     * URL 格式: ws://host/ws/notifications?userId=xxx
     */
    private String extractUserId(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query != null && !query.isBlank()) {
            Matcher matcher = USER_ID_PATTERN.matcher(query);
            if (matcher.find()) {
                return java.net.URLDecoder.decode(matcher.group(2), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return "default-user";
    }
}
