package org.hai.work.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hai.work.agent.dto.AgentRequest;
import org.hai.work.agent.dto.AgentResponse;
import org.hai.work.context.AgentContext;
import org.hai.work.orchestrator.Orchestrator;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 统一入口（路由分发）
 * <p>
 * 提供接口：
 * 1. /api/ask       → 普通请求（等待完整回答后返回）
 * 2. /api/ask/stream → 流式请求（SSE 逐字返回，打字机效果）
 * 3. /api/confirm/{confirmationId} → 确认后继续执行
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class RouterResource {

    private static final int MAX_LOG_INPUT_LENGTH = 200;
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final Orchestrator orchestrator;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    public RouterResource(Orchestrator orchestrator, ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }

    /**
     * 普通请求接口（非流式）
     */
    @GetMapping("/ask")
    public AgentResponse ask(@RequestParam(defaultValue = "你好") String input,
                             @RequestParam(required = false) String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        log.info("========== 收到请求 GET /api/ask ==========");
        log.info("用户输入: {}, 会话ID: {}", sanitize(input), sessionId);

        AgentRequest request = new AgentRequest();
        request.setInput(input);
        request.setSessionId(sessionId);
        request.setUserId("default-user");

        // Orchestrator 自动判断简单/复杂任务
        AgentResponse response;
        if (orchestrator.isComplexTask(input)) {
            log.info("检测到复杂任务，使用 Orchestrator 执行");
            response = orchestrator.executeComplex(request);
        } else {
            log.info("简单任务，直接路由到 Agent");
            response = orchestrator.executeSimple(request);
        }

        log.info("请求处理完成");
        return response;
    }

    /**
     * 流式请求接口（SSE）
     */
    @PostMapping("/ask/stream")
    public SseEmitter askStream(@RequestBody AgentRequest body,
                                @RequestParam(required = false) String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        String input = body.getInput();
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("未从前端收到 input 字段");
        }

        log.info("========== 收到流式请求 POST /api/ask/stream ==========");
        log.info("用户输入: {}, 会话ID: {}", sanitize(input), sessionId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 构建请求
        AgentRequest request = new AgentRequest();
        request.setInput(input);
        request.setSessionId(sessionId);
        request.setUserId("default-user");
        request.setImages(body.getImages());
        request.setFiles(body.getFiles());
        request.setSkill(body.getSkill());

        // 获取流式数据
        Flux<String> stream;
        if (orchestrator.isComplexTask(input)) {
            log.info("检测到复杂任务，使用 Orchestrator 流式执行");
            stream = orchestrator.executeComplexStream(request);
        } else {
            log.info("简单任务，直接路由到 Agent 流式执行");
            stream = orchestrator.executeSimpleStream(request);
        }

        // 发送到 SSE
        String finalSessionId = sessionId;
        // 提前创建 Context，确保流式确认时能保存完整的执行状态
        AgentContext streamContext = orchestrator.createContext(request);
        executorService.submit(() -> {
            try {
                stream
                        .doOnNext(chunk -> {
                            try {
                                // 检查是否包含待确认标记
                                if (chunk.contains(Orchestrator.PENDING_CONFIRMATION_MARKER)) {
                                    // 解析并发送确认事件
                                    AgentResponse confirmResponse = orchestrator.handlePendingConfirmation(
                                            streamContext, "unknown", chunk);
                                    String confirmJson = objectMapper.writeValueAsString(java.util.Map.of(
                                            "type", "confirmation",
                                            "confirmationId", confirmResponse.getConfirmationId(),
                                            "confirmationMessage", confirmResponse.getConfirmationMessage(),
                                            "partialResult", confirmResponse.getPartialResult(),
                                            "pendingAction", confirmResponse.getPendingAction()
                                    ));
                                    emitter.send(confirmJson);
                                } else {
                                    String json = objectMapper.writeValueAsString(java.util.Map.of("text", chunk));
                                    emitter.send(json);
                                }
                            } catch (Exception e) {
                                log.warn("发送 SSE 数据失败: {}", e.getMessage());
                            }
                        })
                        .doOnComplete(() -> {
                            try {
                                emitter.send("[DONE]");
                                emitter.complete();
                                log.info("流式请求完成: sessionId={}", finalSessionId);
                            } catch (Exception e) {
                                log.warn("发送 SSE 完成标记失败: {}", e.getMessage());
                            }
                        })
                        .doOnError(e -> {
                            log.error("流式请求异常: sessionId={}", finalSessionId, e);
                            emitter.completeWithError(e);
                        })
                        .subscribe();
            } catch (Exception e) {
                log.error("流式请求启动失败: sessionId={}", finalSessionId, e);
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(() -> {
            log.warn("SSE 超时: sessionId={}", finalSessionId);
            emitter.complete();
        });

        return emitter;
    }

    /**
     * 确认后继续执行
     * <p>
     * 当 Agent 返回需要确认的响应时，用户通过此接口确认后继续执行。
     *
     * @param confirmationId 确认ID
     * @return 完整的执行结果
     */
    @PostMapping("/confirm/{confirmationId}")
    public AgentResponse confirm(@PathVariable String confirmationId) {
        log.info("========== 收到确认请求 POST /api/confirm/{} ==========", confirmationId);

        try {
            AgentResponse response = orchestrator.continueAfterConfirmation(confirmationId);
            log.info("确认后执行完成");
            return response;
        } catch (IllegalStateException e) {
            log.error("确认失败: {}", e.getMessage());
            AgentResponse errorResponse = new AgentResponse();
            errorResponse.setAnswer("确认失败: " + e.getMessage());
            return errorResponse;
        }
    }

    private String sanitize(String input) {
        if (input == null) return "null";
        // 去除控制字符、ANSI 转义码、换行符，防止日志注入
        String cleaned = input.replaceAll("[\\x00-\\x1f\\x7f]", "")
                .replaceAll("\\x1b\\[[0-9;]*[a-zA-Z]", "");
        if (cleaned.length() <= MAX_LOG_INPUT_LENGTH) return cleaned;
        return cleaned.substring(0, MAX_LOG_INPUT_LENGTH) + "... (truncated, total=" + cleaned.length() + ")";
    }
}
