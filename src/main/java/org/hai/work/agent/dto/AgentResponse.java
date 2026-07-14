package org.hai.work.agent.dto;

/**
 * Agent 响应对象
 * <p>
 * 封装 Agent 执行结果，支持：
 * - 普通响应：answer 包含完整回答
 * - 待确认响应：answer 包含部分结果 + 确认提示
 * - 流式响应：通过 SSE 逐字返回
 */
public class AgentResponse {

    /**
     * Agent 的回答内容
     */
    private String answer;

    /**
     * 是否需要用户确认
     * <p>
     * 当 Agent 检测到需要确认的操作时设置为 true。
     * 此时 answer 包含已完成的部分结果和确认提示。
     */
    private boolean needsConfirmation;

    /**
     * 确认ID
     * <p>
     * 当 needsConfirmation=true 时，此字段包含确认ID。
     * 用户确认时需要传递此ID以继续执行。
     */
    private String confirmationId;

    /**
     * 确认消息
     * <p>
     * 显示给用户的确认提示，说明需要确认什么操作。
     */
    private String confirmationMessage;

    /**
     * 已完成的部分结果
     * <p>
     * 当 needsConfirmation=true 时，此字段包含已经完成的工作。
     * 用户确认后，会继续执行剩余工作，最终结果 = partialResult + 继续执行的结果。
     */
    private String partialResult;

    /**
     * 待执行的操作描述
     * <p>
     * 说明确认后将执行什么操作。
     */
    private String pendingAction;

    public AgentResponse() {
    }

    public AgentResponse(String answer) {
        this.answer = answer;
    }

    /**
     * 创建待确认响应
     */
    public static AgentResponse pendingConfirmation(
            String confirmationId,
            String confirmationMessage,
            String partialResult,
            String pendingAction) {
        AgentResponse response = new AgentResponse();
        response.setNeedsConfirmation(true);
        response.setConfirmationId(confirmationId);
        response.setConfirmationMessage(confirmationMessage);
        response.setPartialResult(partialResult);
        response.setPendingAction(pendingAction);
        response.setAnswer(partialResult + "\n\n---\n\n⚠️ **需要确认**\n\n" + confirmationMessage);
        return response;
    }

    // ==================== Getter/Setter ====================

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public boolean isNeedsConfirmation() {
        return needsConfirmation;
    }

    public void setNeedsConfirmation(boolean needsConfirmation) {
        this.needsConfirmation = needsConfirmation;
    }

    public String getConfirmationId() {
        return confirmationId;
    }

    public void setConfirmationId(String confirmationId) {
        this.confirmationId = confirmationId;
    }

    public String getConfirmationMessage() {
        return confirmationMessage;
    }

    public void setConfirmationMessage(String confirmationMessage) {
        this.confirmationMessage = confirmationMessage;
    }

    public String getPartialResult() {
        return partialResult;
    }

    public void setPartialResult(String partialResult) {
        this.partialResult = partialResult;
    }

    public String getPendingAction() {
        return pendingAction;
    }

    public void setPendingAction(String pendingAction) {
        this.pendingAction = pendingAction;
    }

    @Override
    public String toString() {
        if (needsConfirmation) {
            return "AgentResponse{needsConfirmation=true, confirmationId='%s', pendingAction='%s'}"
                    .formatted(confirmationId, pendingAction);
        }
        return "AgentResponse{answer='%s'}".formatted(
                answer != null ? answer.substring(0, Math.min(answer.length(), 100)) : "null");
    }
}
