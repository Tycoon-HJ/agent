package org.hai.work.orchestrator.workflow;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流定义
 * <p>
 * 包含一组有序的 Step，由 Planner 生成或手动定义。
 * WorkflowEngine 负责执行这些 Step。
 */
public class WorkflowDefinition {

    private String name;
    private String description;
    private List<Step> steps;

    public WorkflowDefinition() {
        this.steps = new ArrayList<>();
    }

    public WorkflowDefinition(String name, String description, List<Step> steps) {
        this.name = name;
        this.description = description;
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
    }

    public void addStep(Step step) {
        steps.add(step);
    }

    // ==================== Getter/Setter ====================

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<Step> getSteps() { return steps; }
    public void setSteps(List<Step> steps) { this.steps = steps; }

    @Override
    public String toString() {
        return "WorkflowDefinition{name='%s', steps=%d}".formatted(name, steps.size());
    }
}
