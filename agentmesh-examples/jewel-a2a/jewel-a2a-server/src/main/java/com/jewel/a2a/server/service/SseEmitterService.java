package com.jewel.a2a.server.service;

import com.jewel.a2a.common.dto.TaskEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 连接管理：缓存 emitter，按 taskId 推送
 */
@Slf4j
@Service
public class SseEmitterService {

    /** 超时时间：120s，与远程超时对齐 */
    private static final long TIMEOUT = 120_000L;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 创建 SSE 连接
     */
    public SseEmitter createEmitter(String taskId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        emitter.onCompletion(() -> {
            log.info("SSE 连接完成: taskId={}", taskId);
            emitters.remove(taskId);
        });
        emitter.onTimeout(() -> {
            log.info("SSE 连接超时: taskId={}", taskId);
            emitters.remove(taskId);
        });
        emitter.onError(e -> {
            log.error("SSE 连接异常: taskId={}", taskId, e);
            emitters.remove(taskId);
        });
        emitters.put(taskId, emitter);
        return emitter;
    }

    /**
     * 推送事件
     */
    public void sendEvent(String taskId, TaskEvent event) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("result")
                        .data(event));
            } catch (IOException e) {
                log.error("SSE 推送失败: taskId={}", taskId, e);
                emitters.remove(taskId);
            }
        }
    }

    /**
     * 推送完成事件并关闭连接
     */
    public void complete(String taskId, TaskEvent event) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("result")
                        .data(event));
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(event));
                emitter.complete();
            } catch (IOException e) {
                log.error("SSE 完成推送失败: taskId={}", taskId, e);
            } finally {
                emitters.remove(taskId);
            }
        }
    }

    /**
     * 异常完成：推送错误事件并关闭连接。
     * LLM 调用异常时必须调用此方法，防止 SseEmitter 连接泄漏。
     */
    public void completeWithError(String taskId, Throwable error) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter != null) {
            try {
                emitter.completeWithError(error);
            } finally {
                emitters.remove(taskId);
            }
        }
    }
}