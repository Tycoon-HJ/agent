package org.hai.work.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hai.work.agent.core.RouterAgent;
import org.hai.work.agent.dto.AgentRequest;
import org.hai.work.agent.dto.AgentResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 统一入口（路由分发）
 * <p>
 * 提供两种接口：
 * 1. /api/ask       → 普通请求（等待完整回答后返回）
 * 2. /api/ask/stream → 流式请求（SSE 逐字返回，打字机效果）
 * <p>
 * 通知推送通过 WebSocket: ws://host/ws/notifications?userId=xxx
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class RouterResource {

    private final RouterAgent routerAgent;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RouterResource(RouterAgent routerAgent) {
        this.routerAgent = routerAgent;
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
        log.info("用户输入: {}, 会话ID: {}", input, sessionId);

        AgentRequest request = new AgentRequest();
        request.setInput(input);
        request.setSessionId(sessionId);
        request.setUserId("default-user");

        AgentResponse response = routerAgent.execute(request);
        log.info("请求处理完成");
        return response;
    }

    /**
     * 流式请求接口（SSE，POST 支持图片）
     * <p>
     * 改为 POST 请求，前端通过 JSON body 传递 text + images。
     * base64 图片数据量大，不能放在 GET 的 query string 中（URL 长度限制）。
     * <p>
     * SSE 数据格式（前端收到的原始数据）：
     * data:你好\n\n
     * data:，我来\n\n
     * data:[DONE]\n\n
     */
    @PostMapping("/ask/stream")
    public SseEmitter askStream(@RequestBody AgentRequest body, @RequestParam(required = false) String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        // 兼容：如果 body 中没有 input 字段，用默认值
        String input = body.getInput();
        if (input == null || input.isBlank()) {
            log.error("未从前端收到input字段");
            throw new RuntimeException("未从前端收到input字段");
        }

        log.info("========== 收到流式请求 POST /api/ask/stream ==========");
        log.info("用户输入: {}, 会话ID: {}, 图片数: {}",
                input, sessionId, body.getImages() != null ? body.getImages().size() : 0);

        // 设置超时时间为 5 分钟（LLM 生成可能较慢）
        SseEmitter emitter = new SseEmitter(300_000L);

        // 构建完整的请求对象
        AgentRequest request = new AgentRequest();
        request.setInput(input);
        request.setSessionId(sessionId);
        request.setUserId("default-user");
        request.setImages(body.getImages());

        // 在独立线程中执行流式调用，避免阻塞 Servlet 线程
        Thread.startVirtualThread(() -> {
            try {
                routerAgent.executeStream(request)
                        .doOnNext(chunk -> {
                            try {
                                // JSON 编码：将 chunk 包装为 {"text":"..."} 格式
                                // 避免 markdown 中的换行符破坏 SSE 协议格式
                                String json = objectMapper.writeValueAsString(java.util.Map.of("text", chunk));
                                emitter.send(json);
                            } catch (Exception e) {
                                log.warn("发送 SSE 数据失败: {}", e.getMessage());
                            }
                        })
                        .doOnComplete(() -> {
                            try {
                                // 发送结束标记
                                emitter.send("[DONE]");
                                emitter.complete();
                                log.info("流式请求完成");
                            } catch (Exception e) {
                                log.warn("发送 SSE 完成标记失败: {}", e.getMessage());
                            }
                        })
                        .doOnError(e -> {
                            log.error("流式请求异常", e);
                            emitter.completeWithError(e);
                        })
                        .subscribe(); // 订阅 Flux，触发数据流
            } catch (Exception e) {
                log.error("流式请求启动失败", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
