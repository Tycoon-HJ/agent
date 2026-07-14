package org.hai.work.skill;

import java.util.Collections;
import java.util.List;

/**
 * 技能接口
 * <p>
 * Skill 是可复用的知识包，与 Agent 解耦。
 * 一个 Agent 可以动态加载多个 Skill。
 * <p>
 * Skill 包含：
 * - 系统提示词扩展（追加到 Agent 的 system prompt）
 * - 领域知识（专业术语、最佳实践等）
 * - 示例对话（few-shot examples）
 * - 依赖的 Tool 列表
 */
public interface Skill {

    /**
     * 技能名称（唯一标识）
     */
    String name();

    /**
     * 技能描述
     */
    String description();

    /**
     * 系统提示词扩展
     * <p>
     * 追加到 Agent 的 system prompt 中，为 Agent 增加领域知识和行为规则。
     */
    String getSystemPromptAddon();

    /**
     * 领域知识
     * <p>
     * 可选，提供专业术语、最佳实践等知识。
     */
    default String getKnowledge() {
        return "";
    }

    /**
     * 示例对话
     * <p>
     * 可选，提供 few-shot examples，帮助 LLM 理解预期行为。
     */
    default List<String> getExamples() {
        return Collections.emptyList();
    }

    /**
     * 依赖的 Tool 列表
     * <p>
     * 声明此 Skill 需要的 Tool，Orchestrator 会检查这些 Tool 是否可用。
     */
    default List<String> requiredTools() {
        return Collections.emptyList();
    }
}
