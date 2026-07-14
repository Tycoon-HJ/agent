package org.hai.work.orchestrator.confirmation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 确认状态存储
 * <p>
 * 管理待确认的执行状态，支持：
 * - 保存待确认状态
 * - 加载待确认状态
 * - 删除已确认/过期的状态
 * - 定时清理过期状态
 */
@Slf4j
@Component
public class ConfirmationStore {

    /**
     * 存储待确认状态（confirmationId → PendingConfirmation）
     */
    private final Map<String, PendingConfirmation> store = new ConcurrentHashMap<>();

    /**
     * 保存待确认状态
     */
    public void save(PendingConfirmation confirmation) {
        store.put(confirmation.getConfirmationId(), confirmation);
        log.info("保存待确认状态: {}", confirmation);
    }

    /**
     * 加载待确认状态
     *
     * @return Optional.empty() 如果不存在或已过期
     */
    public Optional<PendingConfirmation> load(String confirmationId) {
        PendingConfirmation confirmation = store.get(confirmationId);
        if (confirmation == null) {
            log.warn("待确认状态不存在: {}", confirmationId);
            return Optional.empty();
        }
        if (confirmation.isExpired()) {
            log.warn("待确认状态已过期: {}", confirmationId);
            store.remove(confirmationId);
            return Optional.empty();
        }
        return Optional.of(confirmation);
    }

    /**
     * 删除待确认状态
     */
    public void remove(String confirmationId) {
        PendingConfirmation removed = store.remove(confirmationId);
        if (removed != null) {
            log.info("删除待确认状态: {}", confirmationId);
        }
    }

    /**
     * 根据 sessionId 获取待确认状态
     */
    public Optional<PendingConfirmation> getBySession(String sessionId) {
        return store.values().stream()
                .filter(c -> c.getSessionId().equals(sessionId))
                .filter(c -> !c.isExpired())
                .findFirst();
    }

    /**
     * 检查是否存在待确认状态
     */
    public boolean hasPending(String confirmationId) {
        return load(confirmationId).isPresent();
    }

    /**
     * 定时清理过期状态（每 5 分钟执行一次）
     */
    @Scheduled(fixedRate = 300_000)
    public void cleanupExpired() {
        int before = store.size();
        store.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int after = store.size();
        if (before != after) {
            log.info("清理过期待确认状态: {} → {}", before, after);
        }
    }
}
