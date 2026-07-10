package org.hai.work.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hai.work.agent.core.RouterAgent;
import org.hai.work.agent.dto.AgentRequest;
import org.hai.work.agent.dto.AgentResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 统一入口（路由分发）
 * <p>
 * 提供两种接口：
 * 1. /api/ask       → 普通请求（等待完整回答后返回）
 * 2. /api/ask/stream → 流式请求（SSE 逐字返回，打字机效果）
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class RouterResource {

    private static final int MAX_LOG_INPUT_LENGTH = 200;
    private static final long SSE_TIMEOUT_MS = 300_000L;
    private static final long EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final RouterAgent routerAgent;
    private final ObjectMapper objectMapper;

    /**
     * 虚拟线程执行器，用于 SSE 流式处理
     * <p>
     * 使用虚拟线程避免阻塞 Servlet 线程，
     * 同时通过 ExecutorService 统一管理生命周期。
     */
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    public RouterResource(RouterAgent routerAgent, ObjectMapper objectMapper) {
        this.routerAgent = routerAgent;
        this.objectMapper = objectMapper;
    }

    /**
     * 普通请求接口（非流式）
     */
    @GetMapping("/ask")
    public AgentResponse ask(@RequestParam(defaultValue = "你好") String input, @RequestParam(required = false) String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        log.info("========== 收到请求 GET /api/ask ==========");
        log.info("用户输入: {}, 会话ID: {}", sanitize(input), sessionId);

        AgentRequest request = new AgentRequest();
        request.setInput(input);
        request.setSessionId(sessionId);
        request.setUserId("default-user");

        AgentResponse response = routerAgent.execute(request);
        log.info("请求处理完成");
        return response;
    }

    /**
     * 流式请求接口（SSE，POST 支持图片和文件）
     * <p>
     * 前端通过 JSON body 传递 text + images + files。
     * <p>
     * SSE 数据格式：
     * data:{"text":"..."}\n\n
     * data:[DONE]\n\n
     */
    @PostMapping("/ask/stream")
    public SseEmitter askStream(@RequestBody AgentRequest body, @RequestParam(required = false) String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        String input = body.getInput();
        if (input == null || input.isBlank()) {
            log.error("未从前端收到 input 字段");
            throw new IllegalArgumentException("未从前端收到 input 字段");
        }

        int imageCount = body.getImages() != null ? body.getImages().size() : 0;
        int fileCount = body.getFiles() != null ? body.getFiles().size() : 0;
        log.info("========== 收到流式请求 POST /api/ask/stream ==========");
        log.info("用户输入: {}, 会话ID: {}, 图片数: {}, 文件数: {}",
                sanitize(input), sessionId, imageCount, fileCount);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        AgentRequest request = new AgentRequest();
        request.setInput(input);
        request.setSessionId(sessionId);
        request.setUserId("default-user");
        request.setImages(body.getImages());
        request.setFiles(body.getFiles());

        // 使用统一执行器提交任务
        String finalSessionId = sessionId;
        String finalSessionId1 = sessionId;
        String finalSessionId2 = sessionId;
        executorService.submit(() -> {
            try {
                routerAgent.executeStream(request)
                        .doOnNext(chunk -> {
                            try {
                                String json = objectMapper.writeValueAsString(java.util.Map.of("text", chunk));
                                emitter.send(json);
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
                            log.error("流式请求异常: sessionId={}", finalSessionId1, e);
                            emitter.completeWithError(e);
                        })
                        .subscribe();
            } catch (Exception e) {
                log.error("流式请求启动失败: sessionId={}", finalSessionId2, e);
                emitter.completeWithError(e);
            }
        });

        // 超时和完成回调，确保资源清理
        String finalSessionId3 = sessionId;
        emitter.onTimeout(() -> {
            log.warn("SSE 超时: sessionId={}", finalSessionId3);
            emitter.complete();
        });

        String finalSessionId4 = sessionId;
        emitter.onCompletion(() -> {
            log.debug("SSE 完成: sessionId={}", finalSessionId4);
        });

        return emitter;
    }

    /**
     * 日志脱敏：截断过长输入，避免打印大量 base64 数据
     */
    private String sanitize(String input) {
        if (input == null) return "null";
        if (input.length() <= MAX_LOG_INPUT_LENGTH) return input;
        return input.substring(0, MAX_LOG_INPUT_LENGTH) + "... (truncated, total=" + input.length() + ")";
    }
}
