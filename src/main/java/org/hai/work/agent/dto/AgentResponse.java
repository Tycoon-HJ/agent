package org.hai.work.agent.dto;

/**
 * Agent 响应对象
 * <p>
 * 封装 Agent 执行结果，由 AgentResource 返回给前端（自动序列化为 JSON）。
 * JSON 示例：{"answer": "【分析】：用户想查上海天气...\n【数据】：...\n【结论】：..."}
 */
public class AgentResponse {

    /**
     * Agent 的回答内容
     * 包含完整的分析、数据、结论和建议（由 LLM 生成）。
     */
    private String answer;

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
