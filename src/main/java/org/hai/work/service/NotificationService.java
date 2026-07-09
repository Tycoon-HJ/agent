package org.hai.work.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 通知推送服务
 * <p>
 * 封装 WebSocket 推送能力，供 ScheduledTaskManager 调用。
 * 实际的连接管理和消息发送委托给 NotificationWebSocketHandler。
 */
@Slf4j
@Service
public class NotificationService {

    private final NotificationWebSocketHandler webSocketHandler;

    public NotificationService(NotificationWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * 向指定用户推送通知
     *
     * @param userId  目标用户ID
     * @param taskId  任务ID
     * @param content 通知内容（任务执行结果）
     */
    public void sendNotification(String userId, String taskId, String content) {
        webSocketHandler.sendNotification(userId, taskId, content);
    }

    /**
     * 检查用户是否在线
     */
    public boolean isOnline(String userId) {
        return webSocketHandler.isOnline(userId);
    }
}
