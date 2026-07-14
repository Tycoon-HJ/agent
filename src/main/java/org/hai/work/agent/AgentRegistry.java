package org.hai.work.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 注册中心
 * <p>
 * 自动收集所有 Agent 实现，提供按名称查找能力。
 * Orchestrator 和 WorkflowEngine 通过此注册中心获取 Agent。
 */
@Slf4j
@Component
public class AgentRegistry {

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    public AgentRegistry(List<Agent> agentList) {
        for (Agent agent : agentList) {
            agents.put(agent.name(), agent);
            log.info("注册 Agent: {}", agent.name());
        }
        log.info("AgentRegistry 初始化完成，共 {} 个 Agent: {}", agents.size(), agents.keySet());
    }

    public Agent get(String name) {
        return agents.get(name);
    }

    public boolean has(String name) {
        return agents.containsKey(name);
    }

    public Map<String, Agent> getAll() {
        return Collections.unmodifiableMap(agents);
    }

    public List<String> getNames() {
        return List.copyOf(agents.keySet());
    }
}
