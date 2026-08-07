package com.agentmesh.core.tool.marketplace.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 市场权限校验器。
 * 根据 X-API-Key 请求头判断调用方角色。
 */
@Slf4j
@Component
public class MarketplacePermissionEvaluator {

    @Value("${spring.agentmesh.marketplace.api-keys.publisher:}")
    private String publisherKeys;

    @Value("${spring.agentmesh.marketplace.api-keys.admin:}")
    private String adminKeys;

    public enum Role { ANONYMOUS, PUBLISHER, ADMIN }

    /**
     * 从请求头中解析角色。
     */
    public Role resolveRole(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Role.ANONYMOUS;
        }
        Set<String> adminKeySet = new HashSet<>(Arrays.asList(adminKeys.split(",")));
        if (adminKeySet.contains(apiKey)) {
            return Role.ADMIN;
        }
        Set<String> publisherKeySet = new HashSet<>(Arrays.asList(publisherKeys.split(",")));
        if (publisherKeySet.contains(apiKey)) {
            return Role.PUBLISHER;
        }
        return Role.ANONYMOUS;
    }

    /**
     * 校验是否有 PUBLISHER 及以上权限。
     */
    public void requirePublisher(String apiKey) {
        Role role = resolveRole(apiKey);
        if (role == Role.ANONYMOUS) {
            throw new SecurityException("需要 PUBLISHER 或 ADMIN 权限");
        }
    }

    /**
     * 校验是否有 ADMIN 权限。
     */
    public void requireAdmin(String apiKey) {
        if (resolveRole(apiKey) != Role.ADMIN) {
            throw new SecurityException("需要 ADMIN 权限");
        }
    }
}
