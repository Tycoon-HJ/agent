package org.hai.work.resource;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.agent.dto.AgentRequest;
import org.hai.work.agent.dto.AgentResponse;
import org.hai.work.orchestrator.Orchestrator;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * 工作流管理接口
 * <p>
 * 提供复杂任务的编排执行能力。
 * 与 /api/ask 不同，此接口始终使用 Planner + WorkflowEngine 执行。
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow")
public class WorkflowResource {

    private final Orchestrator orchestrator;

    public WorkflowResource(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * 执行工作流（非流式）
     */
    @PostMapping("/execute")
    public AgentResponse execute(@RequestBody AgentRequest body,
                                 @RequestParam(required = false) String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        log.info("========== 收到工作流请求 ==========");
        log.info("用户输入: {}, 会话ID: {}", body.getInput(), sessionId);

        AgentRequest request = new AgentRequest();
        request.setInput(body.getInput());
        request.setSessionId(sessionId);
        request.setUserId("default-user");
        request.setImages(body.getImages());
        request.setFiles(body.getFiles());

        return orchestrator.executeComplex(request);
    }

    /**
     * 判断任务是否为复杂任务
     */
    @PostMapping("/analyze")
    public java.util.Map<String, Object> analyze(@RequestBody AgentRequest body) {
        String input = body.getInput();
        boolean isComplex = orchestrator.isComplexTask(input);
        return java.util.Map.of(
                "input", input,
                "isComplex", isComplex,
                "suggestion", isComplex ? "建议使用工作流执行" : "可以直接执行"
        );
    }
}
