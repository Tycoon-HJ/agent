package org.hai.work.orchestrator.workflow;

import java.time.Instant;

/**
 * 步骤执行结果
 *
 * @param stepId    步骤 ID
 * @param status    执行状态
 * @param output    输出内容（Agent 回答或 Tool 返回值）
 * @param error     错误信息（失败时）
 * @param duration  执行耗时（毫秒）
 * @param timestamp 执行时间
 */
public record StepResult(
        int stepId,
        Status status,
        String output,
        String error,
        long duration,
        Instant timestamp
) {

    public enum Status {
        SUCCESS, FAILED, SKIPPED, TIMEOUT, CANCELLED
    }

    // ==================== 工厂方法 ====================

    public static StepResult success(int stepId, String output, long duration) {
        return new StepResult(stepId, Status.SUCCESS, output, null, duration, Instant.now());
    }

    public static StepResult failed(int stepId, String error, long duration) {
        return new StepResult(stepId, Status.FAILED, null, error, duration, Instant.now());
    }

    public static StepResult skipped(int stepId, String reason) {
        return new StepResult(stepId, Status.SKIPPED, null, reason, 0, Instant.now());
    }

    public static StepResult timeout(int stepId, long duration) {
        return new StepResult(stepId, Status.TIMEOUT, null, "执行超时", duration, Instant.now());
    }

    public static StepResult cancelled(int stepId) {
        return new StepResult(stepId, Status.CANCELLED, null, "已取消", 0, Instant.now());
    }

    // ==================== 状态判断 ====================

    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isFailed() { return status == Status.FAILED; }
    public boolean isSkipped() { return status == Status.SKIPPED; }
    public boolean isTimeout() { return status == Status.TIMEOUT; }
    public boolean isCancelled() { return status == Status.CANCELLED; }
    public boolean isTerminal() { return isFailed() || isTimeout() || isCancelled(); }

    @Override
    public String toString() {
        return "StepResult{step=%d, status=%s, output=%s, duration=%dms}"
                .formatted(stepId, status, output != null ? output.substring(0, Math.min(output.length(), 100)) : "null", duration);
    }
}
