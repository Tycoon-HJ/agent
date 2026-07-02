package org.hai.work.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

/**
 * 天气旅行规划 Agent
 * <p>
 * 这是一个具体的业务 Agent 实现，专注于：
 * - 天气查询（当前天气、未来7天预报）
 * - 出行建议（根据天气推荐最佳出行日）
 * - 旅行规划辅助（结合天气给出实用建议）
 * <p>
 * 继承 AbstractAgent 后自动拥有：
 * - LLM 对话能力（通过 ChatClient）
 * - 工具调用能力（通过 ToolCallbackProvider，如调用 weather_query）
 * - 会话记忆能力（通过 MessageWindowChatMemory，记住对话上下文）
 * <p>
 * 本类只需定义：
 * 1. name()        → Agent 标识
 * 2. description() → 功能描述
 * 3. systemPrompt() → 系统提示词（控制 Agent 行为的核心）
 */
@Slf4j
@Component
public class WeatherAgent extends AbstractAgent {

    /**
     * 构造函数，Spring 自动注入所有依赖
     *
     * @param chatModel            DeepSeek 大模型
     * @param toolCallbackProvider 工具回调（包含 weather_query 等工具）
     * @param chatMemory           会话记忆（按 sessionId 隔离对话历史）
     */
    public WeatherAgent(DeepSeekChatModel chatModel,
                        ToolCallbackProvider toolCallbackProvider,
                        MessageWindowChatMemory chatMemory) {
        super(chatModel, toolCallbackProvider, chatMemory);
    }

    @Override
    protected String name() {
        return "weather-agent";
    }

    @Override
    protected String description() {
        return "A professional weather & travel planning agent that helps users decide best travel days based on weather conditions.";
    }

    /**
     * 系统提示词（System Prompt）
     * <p>
     * 这是控制 WeatherAgent 行为的核心。LLM 会严格遵循这些指令：
     * - 角色定义：专业的天气旅行规划助手
     * - 职责范围：理解意图 → 调用天气工具 → 分析数据 → 给出建议
     * - 行为规则：必须调用工具获取真实数据，不能凭空猜测
     * - 输出要求：用用户相同的语言回复，记住之前的对话
     */
    @Override
    protected String systemPrompt() {
        return """
                You are WeatherAgent, a professional travel and weather planning assistant.
                
                Your responsibilities:
                1. Understand user travel or outdoor activity intent
                2. Use the weather_query tool to get weather data when needed
                3. Analyze weather conditions and decide best days for travel
                4. Provide clear recommendations and actionable suggestions
                
                Rules:
                - Always call weather_query tool to get real weather data, never guess
                - Base all conclusions on tool results
                - Provide practical, actionable travel advice
                - Respond in the same language as the user
                - Remember previous conversations and refer to them when relevant
                
                ====== 输出格式规范（必须严格遵守） ======
                
                【核心原则】
                1. 输出结构必须整块输出，禁止把表格、标题、分割线拆成前后分片
                2. 表格必须一次性输出整个Markdown表格，禁止先输出表头再分片输出行
                3. 标题层级只用二级标题 ##，禁止使用 ### 三级标题
                4. 分割线 --- 只放在模块与模块之间，禁止放在句子中间
                
                【输出结构模板】
                ## {城市}天气情况全览
                
                ## 【分析】
                一整段完整的分析文字，不要换行拆分，一次性输出完整。
                
                ---
                
                ## 【数据】
                当前天气（附带更新时间）
                
                紧接着一次性输出整张完整Markdown表格，表格必须整体输出，不允许分片断开。
                
                【禁止行为】
                - 禁止输出 ### 三级标题
                - 禁止分片输出表格（如先输出表头，再逐行输出）
                - 禁止在句子中间插入分割线 ---
                - 禁止把一个完整段落拆成多次输出
                """;
    }
}
