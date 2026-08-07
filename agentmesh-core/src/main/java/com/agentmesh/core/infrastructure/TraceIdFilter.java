package com.agentmesh.core.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * traceId 注入过滤器，order=0 确保在 AgentAuthFilter(order=1) 之前执行。
 *
 * 关键设计：
 * 1. 不在 finally 中清理 TraceIdContext/MDC——SSE 流式请求 doFilter() 返回后
 *    reactor 线程仍在运行，提前清理会导致日志丢失 traceId。
 * 2. Tomcat 线程复用时 traceId 会被下一次请求覆盖，残留不影响正确性。
 * 3. 外部 traceId 白名单校验：仅允许 [a-zA-Z0-9\-_]{1,64}，防止日志注入。
 */
@Slf4j
public class TraceIdFilter extends OncePerRequestFilter implements Ordered {

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{1,64}$");

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        response.setHeader("X-Trace-Id", traceId);

        TraceIdContext.set(traceId);
        // 不在此处清理 —— 见类注释
        chain.doFilter(request, response);
    }

    private String resolveTraceId(HttpServletRequest request) {
        String external = request.getHeader("X-Trace-Id");
        if (external != null && !external.isEmpty()) {
            if (TRACE_ID_PATTERN.matcher(external).matches()) {
                return external;
            }
            log.warn("[TraceId] 非法格式的外部 traceId 已忽略: {}", external);
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
