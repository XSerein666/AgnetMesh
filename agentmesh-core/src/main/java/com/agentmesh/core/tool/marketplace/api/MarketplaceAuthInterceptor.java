package com.agentmesh.core.tool.marketplace.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 市场 API 权限拦截器。
 * 根据请求方法和路径匹配所需的权限级别。
 */
@Slf4j
public class MarketplaceAuthInterceptor implements HandlerInterceptor {

    private final MarketplacePermissionEvaluator evaluator;

    public MarketplaceAuthInterceptor(MarketplacePermissionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        // 公开接口：GET 请求且路径不包含 install/health 汇总
        if ("GET".equalsIgnoreCase(method) && !path.endsWith("/tools/health")
                && !path.contains("/installed")) {
            return true;
        }

        String apiKey = request.getHeader("X-API-Key");

        // ADMIN 接口
        if (path.contains("/review") || (path.endsWith("/tools") && "DELETE".equalsIgnoreCase(method))
                || path.contains("/categories") && "POST".equalsIgnoreCase(method)
                || path.endsWith("/tools/health")) {
            evaluator.requireAdmin(apiKey);
            return true;
        }

        // PUBLISHER 接口
        evaluator.requirePublisher(apiKey);
        return true;
    }
}
