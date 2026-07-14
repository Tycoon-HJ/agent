package org.hai.work.orchestrator.workflow;

import org.hai.work.context.AgentContext;

import java.util.List;
import java.util.Map;

/**
 * 工作流步骤定义
 * <p>
 * 一个 Step 要么由 Agent 执行，要么由 Tool 执行，二者只能选其一。
 * <p>
 * 条件执行：
 * - condition 为空时始终执行
 * - condition 非空时，解析表达式判断是否执行
 * - 表达式可引用 Context 变量和前置 Step 的结果
 */
public class Step {

    private int id;
    private String agentName;         // 由哪个 Agent 执行（与 toolName 二选一）
    private String toolName;          // 由哪个 Tool 执行
    private String description;       // 步骤描述
    private String inputTemplate;     // 输入模板，可引用 ${variable} 或 ${stepN.output}
    private List<Integer> dependsOn;  // 依赖的前置 Step ID
    private String condition;         // 执行条件（简单表达式）
    private int maxRetries;           // 最大重试次数
    private long timeoutMs;           // 超时时间（毫秒）
    private boolean critical;         // 是否关键步骤（失败则终止整个 workflow）
    private Map<String, String> toolArgs; // Tool 执行时的额外参数

    public Step() {}

    // ==================== 状态判断 ====================

    public boolean hasAgent() { return agentName != null && !agentName.isBlank(); }
    public boolean hasTool() { return toolName != null && !toolName.isBlank(); }
    public boolean canRetry() { return maxRetries > 0; }

    /**
     * 判断此 Step 是否应该执行
     * <p>
     * 简单条件解析：
     * - null/空 → 始终执行
     * - "stepN.success" → 前置 Step 成功才执行
     * - "stepN.failed" → 前置 Step 失败才执行
     * - "variable == value" → 变量匹配才执行
     */
    public boolean shouldExecute(AgentContext context) {
        if (condition == null || condition.isBlank()) {
            return true;
        }

        String expr = condition.trim();

        // stepN.success / stepN.failed
        if (expr.matches("step\\d+\\.success")) {
            int stepId = Integer.parseInt(expr.substring(4, expr.indexOf('.')));
            StepResult result = context.getStepResult(stepId);
            return result != null && result.isSuccess();
        }
        if (expr.matches("step\\d+\\.failed")) {
            int stepId = Integer.parseInt(expr.substring(4, expr.indexOf('.')));
            StepResult result = context.getStepResult(stepId);
            return result != null && result.isTerminal();
        }

        // variable == value
        if (expr.contains("==")) {
            String[] parts = expr.split("==", 2);
            String key = parts[0].trim();
            String expected = parts[1].trim().replace("\"", "");
            Object actual = context.getVariable(key);
            return expected.equals(String.valueOf(actual));
        }

        // variable != value
        if (expr.contains("!=")) {
            String[] parts = expr.split("!=", 2);
            String key = parts[0].trim();
            String expected = parts[1].trim().replace("\"", "");
            Object actual = context.getVariable(key);
            return !expected.equals(String.valueOf(actual));
        }

        // 默认执行
        return true;
    }

    /**
     * 从 Context 中解析输入模板
     * <p>
     * 支持占位符：
     * - ${originalInput} → 用户原始输入
     * - ${variableName} → Context 变量
     * - ${stepN.output} → 前置 Step 的输出
     */
    public String resolveInput(AgentContext context) {
        if (inputTemplate == null || inputTemplate.isBlank()) {
            return context.getOriginalInput();
        }

        String resolved = inputTemplate;

        // 替换 ${originalInput}
        resolved = resolved.replace("${originalInput}", context.getOriginalInput());

        // 替换 ${stepN.output}
        java.util.regex.Matcher stepMatcher = java.util.regex.Pattern.compile("\\$\\{step(\\d+)\\.output}")
                .matcher(resolved);
        StringBuilder sb = new StringBuilder();
        while (stepMatcher.find()) {
            int stepId = Integer.parseInt(stepMatcher.group(1));
            StepResult result = context.getStepResult(stepId);
            String replacement = result != null && result.isSuccess() ? result.output() : "";
            stepMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        stepMatcher.appendTail(sb);
        resolved = sb.toString();

        // 替换 ${variableName}
        java.util.regex.Matcher varMatcher = java.util.regex.Pattern.compile("\\$\\{([\\w.]+)}")
                .matcher(resolved);
        sb = new StringBuilder();
        while (varMatcher.find()) {
            String key = varMatcher.group(1);
            Object value = context.getVariable(key);
            String replacement = value != null ? value.toString() : "";
            varMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        varMatcher.appendTail(sb);
        resolved = sb.toString();

        return resolved;
    }

    // ==================== Getter/Setter ====================

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInputTemplate() { return inputTemplate; }
    public void setInputTemplate(String inputTemplate) { this.inputTemplate = inputTemplate; }
    public List<Integer> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<Integer> dependsOn) { this.dependsOn = dependsOn; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public boolean isCritical() { return critical; }
    public void setCritical(boolean critical) { this.critical = critical; }
    public Map<String, String> getToolArgs() { return toolArgs; }
    public void setToolArgs(Map<String, String> toolArgs) { this.toolArgs = toolArgs; }
}
