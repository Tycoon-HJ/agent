package org.hai.work.agent;

import org.hai.work.agent.dto.AgentRequest;
import org.hai.work.agent.dto.AgentResponse;

/**
 * Agent 顶层接口
 * <p>
 * 定义所有 Agent 的统一行为：接收请求，返回响应。
 * 不同的业务场景实现不同的 Agent（如 WeatherAgent 负责天气查询）。
 * <p>
 * 设计原则：
 * - 面向接口编程，AgentResource 只依赖 Agent 接口，不依赖具体实现
 * - 便于扩展：新增 Agent 只需实现此接口，无需修改调用方
 */
public interface Agent {

    /**
     * 执行 Agent 逻辑
     *
     * @param request 包含用户输入、sessionId、userId 等信息
     * @return Agent 的回答
     */
    AgentResponse execute(AgentRequest request);
}
