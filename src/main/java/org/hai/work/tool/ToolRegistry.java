package org.hai.work.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一工具注册中心
 * <p>
 * 自动收集所有 Tool 实现，提供按名称查找能力。
 * Orchestrator 和 WorkflowEngine 通过此注册中心获取 Tool。
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<Tool> toolList) {
        for (Tool tool : toolList) {
            tools.put(tool.name(), tool);
            log.info("注册 Tool: {}", tool.name());
        }
        log.info("ToolRegistry 初始化完成，共 {} 个 Tool: {}", tools.size(), tools.keySet());
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public boolean has(String name) {
        return tools.containsKey(name);
    }

    public Map<String, Tool> getAll() {
        return Collections.unmodifiableMap(tools);
    }

    public List<String> getNames() {
        return List.copyOf(tools.keySet());
    }
}
