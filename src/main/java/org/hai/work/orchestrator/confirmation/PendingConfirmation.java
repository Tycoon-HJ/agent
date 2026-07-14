package org.hai.work.orchestrator.confirmation;

import org.hai.work.context.AgentContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 待确认状态
 * <p>
 * 当 Agent 执行任务时需要用户确认（如修改公共接口、删除代码等），
 * 将当前执行状态保存到此对象中，等待用户确认后继续执行。
 */
public class PendingConfirmation {

    private final String confirmationId;
    private final String sessionId;
    private final String agentName;
    private final AgentContext savedContext;
    private final String partialResult;
    private final String confirmationMessage;
    private final String pendingAction;
    private final Instant createdAt;
    private final Instant expiresAt;

    private PendingConfirmation(Builder builder) {
        this.confirmationId = builder.confirmationId;
        this.sessionId = builder.sessionId;
        this.agentName = builder.agentName;
        this.savedContext = builder.savedContext;
        this.partialResult = builder.partialResult;
        this.confirmationMessage = builder.confirmationMessage;
        this.pendingAction = builder.pendingAction;
        this.createdAt = builder.createdAt;
        this.expiresAt = builder.expiresAt;
    }

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    // ==================== Getter ====================

    public String getConfirmationId() { return confirmationId; }
    public String getSessionId() { return sessionId; }
    public String getAgentName() { return agentName; }
    public AgentContext getSavedContext() { return savedContext; }
    public String getPartialResult() { return partialResult; }
    public String getConfirmationMessage() { return confirmationMessage; }
    public String getPendingAction() { return pendingAction; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }

    @Override
    public String toString() {
        return "PendingConfirmation{id='%s', agent='%s', action='%s'}"
                .formatted(confirmationId, agentName, pendingAction);
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String confirmationId = UUID.randomUUID().toString();
        private String sessionId;
        private String agentName;
        private AgentContext savedContext;
        private String partialResult = "";
        private String confirmationMessage;
        private String pendingAction;
        private Instant createdAt = Instant.now();
        private Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);

        public Builder confirmationId(String confirmationId) { this.confirmationId = confirmationId; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder agentName(String agentName) { this.agentName = agentName; return this; }
        public Builder savedContext(AgentContext savedContext) { this.savedContext = savedContext; return this; }
        public Builder partialResult(String partialResult) { this.partialResult = partialResult; return this; }
        public Builder confirmationMessage(String confirmationMessage) { this.confirmationMessage = confirmationMessage; return this; }
        public Builder pendingAction(String pendingAction) { this.pendingAction = pendingAction; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }

        public PendingConfirmation build() {
            return new PendingConfirmation(this);
        }
    }
}
