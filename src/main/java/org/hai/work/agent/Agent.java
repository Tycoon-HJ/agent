package org.hai.work.agent;

import org.hai.work.context.AgentContext;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

/**
 * Agent 顶层接口
 * <p>
 * Agent 是纯能力单元，只负责执行自己的领域能力。
 * <p>
 * 设计原则：
 * - Agent 之间禁止直接调用
 * - Agent 只从 Context 读取输入，将结果写入 Context
 * - Agent 不知道 Orchestrator 的存在
 * - Agent 通过声明 requiredSkills 和 requiredTools 来表达依赖
 */
public interface Agent {

    /**
     * Agent 名称（唯一标识）
     */
    String name();

    /**
     * Agent 功能描述
     */
    String description();

    /**
     * 声明需要的 Skill
     * <p>
     * Orchestrator 会在执行前加载这些 Skill 的提示词。
     */
    default List<String> requiredSkills() {
        return Collections.emptyList();
    }

    /**
     * 声明需要的 Tool
     * <p>
     * Orchestrator 会检查这些 Tool 是否可用。
     */
    default List<String> requiredTools() {
        return Collections.emptyList();
    }

    /**
     * 同步执行
     *
     * @param context 统一上下文（包含用户输入、变量、历史等）
     * @return Agent 的回答文本
     */
    String execute(AgentContext context);

    /**
     * 流式执行（SSE）
     * <p>
     * 默认实现：将同步结果转为 Flux
     */
    default Flux<String> executeStream(AgentContext context) {
        String result = execute(context);
        return Flux.just(result);
    }
}
