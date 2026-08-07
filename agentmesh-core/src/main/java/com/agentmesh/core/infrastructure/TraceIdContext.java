package com.agentmesh.core.infrastructure;

import org.slf4j.MDC;

/**
 * traceId 持有者（仅用于 servlet 线程，不在 reactor 线程中操作）。
 *
 * 设计约束：
 * - set(null) 等价于 clear()，不会往 MDC 写入 "" 或 "null"
 * - get() 未设置时返回 null，不是 ""
 * - 不在 reactor 线程（WebClient 回调、Flux operator）中调用 set/get/clear
 * - reactor 链中 traceId 走显式参数传递，不依赖 ThreadLocal
 */
public class TraceIdContext {

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static void set(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            CONTEXT.set(traceId);
            MDC.put("traceId", traceId);
        } else {
            clear();
        }
    }

    public static String get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
        MDC.remove("traceId");
    }
}
