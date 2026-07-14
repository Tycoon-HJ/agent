package org.hai.work.context;

import lombok.Data;
import lombok.Getter;
import org.hai.work.agent.dto.FileData;
import org.hai.work.orchestrator.workflow.StepResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 统一上下文
 * <p>
 * 所有 Agent 共享同一个 Context 实例，用于：
 * - 传递中间结果（如 SearchAgent 的输出 → CodeAgent 的输入）
 * - 共享变量（workflow 级别的状态）
 * - 记录执行历史
 * - 管理文件、图片等资源
 * <p>
 * 设计原则：
 * - Agent 只从 Context 读取，将结果写入 Context
 * - Agent 之间不直接调用，通过 Context 解耦
 * - Context 在 Workflow 开始时创建，结束时销毁
 */
@Data
public class AgentContext {

    private final String requestId;
    private final String sessionId;
    private final String userId;
    private final String originalInput;
    private final String skill;
    private final Map<String, Object> variables;
    private final List<FileData> files;
    private final List<String> images;
    private final List<StepResult> history;
    private final Map<String, Object> metadata;
    private volatile boolean cancelled = false;

    private AgentContext(Builder builder) {
        this.requestId = builder.requestId;
        this.sessionId = builder.sessionId;
        this.userId = builder.userId;
        this.originalInput = builder.originalInput;
        this.skill = builder.skill;
        this.variables = new ConcurrentHashMap<>(builder.variables);
        this.files = List.copyOf(builder.files);
        this.images = List.copyOf(builder.images);
        this.history = Collections.synchronizedList(new ArrayList<>(builder.history));
        this.metadata = new ConcurrentHashMap<>(builder.metadata);
    }

    // ==================== 变量读写 ====================

    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key, Class<T> type) {
        Object value = variables.get(key);
        if (value == null) return null;
        if (type.isInstance(value)) return (T) value;
        return null;
    }

    public Object getVariable(String key) {
        return variables.get(key);
    }

    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    public boolean hasVariable(String key) {
        return variables.containsKey(key);
    }

    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    // ==================== 历史记录 ====================

    public void addStepResult(StepResult result) {
        history.add(result);
    }

    public StepResult getLastStepResult() {
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    public StepResult getStepResult(int stepId) {
        return history.stream()
                .filter(r -> r.stepId() == stepId)
                .findFirst()
                .orElse(null);
    }

    public List<StepResult> getHistory() {
        return Collections.unmodifiableList(history);
    }

    // ==================== 元数据 ====================

    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        return type.isInstance(value) ? (T) value : null;
    }

    // ==================== 取消控制 ====================

    public void cancel() {
        this.cancelled = true;
    }


    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String requestId = UUID.randomUUID().toString();
        private String sessionId = "";
        private String userId = "default-user";
        private String originalInput = "";
        private String skill = null;
        private Map<String, Object> variables = new HashMap<>();
        private List<FileData> files = List.of();
        private List<String> images = List.of();
        private List<StepResult> history = new ArrayList<>();
        private Map<String, Object> metadata = new HashMap<>();

        public Builder requestId(String requestId) { this.requestId = requestId; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder originalInput(String originalInput) { this.originalInput = originalInput; return this; }
        public Builder skill(String skill) { this.skill = skill; return this; }
        public Builder variables(Map<String, Object> variables) { this.variables = variables; return this; }
        public Builder files(List<FileData> files) { this.files = files != null ? files : List.of(); return this; }
        public Builder images(List<String> images) { this.images = images != null ? images : List.of(); return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public AgentContext build() {
            return new AgentContext(this);
        }
    }
}
