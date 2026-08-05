package com.agentmesh.core.registry;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Agent 间 API Key 鉴权过滤器。
 * 拦截 /a2a/** 路径，校验 X-Agent-Key 请求头。
 * 仅当 agentmesh.auth.enabled=true 时生效。
 */
@Slf4j
@RequiredArgsConstructor
public class AgentAuthFilter extends OncePerRequestFilter {

    private final AgentAuthProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain)
            throws ServletException, IOException {

        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String requestKey = request.getHeader("X-Agent-Key");
        if (requestKey == null || requestKey.isEmpty()) {
            log.warn("[Auth] 请求缺少 X-Agent-Key: {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized: missing X-Agent-Key header\"}");
            return;
        }

        if (!requestKey.equals(properties.getApiKey())) {
            log.warn("[Auth] API Key 校验失败: {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized: invalid agent key\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 仅拦截 A2A 协议端点
        return !path.startsWith("/a2a/");
    }
}