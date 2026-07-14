package org.hai.work.orchestrator.workflow;

import java.util.List;

/**
 * 工作流执行结果
 */
public class WorkflowResult {

    private final Status status;
    private final List<StepResult> stepResults;
    private final String finalOutput;

    public enum Status {
        COMPLETED, FAILED, CANCELLED, PARTIAL
    }

    public WorkflowResult(Status status, List<StepResult> stepResults, String finalOutput) {
        this.status = status;
        this.stepResults = stepResults;
        this.finalOutput = finalOutput;
    }

    public static WorkflowResult completed(List<StepResult> results) {
        String output = results.isEmpty() ? "" :
                results.stream()
                        .filter(StepResult::isSuccess)
                        .reduce((a, b) -> b)
                        .map(StepResult::output)
                        .orElse("");
        return new WorkflowResult(Status.COMPLETED, results, output);
    }

    public static WorkflowResult failed(List<StepResult> results, String error) {
        return new WorkflowResult(Status.FAILED, results, error);
    }

    public static WorkflowResult cancelled(List<StepResult> results) {
        return new WorkflowResult(Status.CANCELLED, results, "工作流已取消");
    }

    // ==================== Getter ====================

    public Status getStatus() { return status; }
    public List<StepResult> getStepResults() { return stepResults; }
    public String getFinalOutput() { return finalOutput; }
    public boolean isCompleted() { return status == Status.COMPLETED; }
    public boolean isFailed() { return status == Status.FAILED; }
}
