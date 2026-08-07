package com.agentmesh.core.collaboration;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * 跨 Agent 共享上下文。
 * 使用 ConcurrentHashMap + compute/CAS 实现无锁并发，避免 ReadWriteLock 的性能开销。
 *
 * 使用场景：
 * - Supervisor 将任务拆解结果写入共享上下文，Worker 读取
 * - Agent A 产出中间数据，Agent B 消费
 * - 全局状态（如"当前阶段"、"已完成的子任务数"）
 *
 * 生命周期：与协作流程绑定（collaborationId），流程结束后调用 clear() 清理。
 *
 * 权限控制：通过 key 前缀约定（见设计文档 4.5 节），put() 方法内置权限校验。
 */
@Slf4j
public class SharedContext {

    /** 协作流程 ID（与生命周期绑定） */
    private final String collaborationId;

    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

    public SharedContext(String collaborationId) {
        this.collaborationId = collaborationId;
        log.debug("[SharedContext] collaborationId={} 创建共享上下文", collaborationId);
    }

    public String getCollaborationId() {
        return collaborationId;
    }

    /**
     * 写入共享数据（带权限校验）。
     * 权限规则：
     * - supervisor/ 前缀仅 supervisor 角色可写入
     * - worker/{agentId}/ 前缀仅对应 Worker 可写入
     * - shared/ 前缀所有 Agent 可写入（全局共享，谨慎使用）
     * - system/ 前缀仅框架可写入，Agent 直接调用 put() 会被拒绝
     * - 未在已知前缀白名单中的 key 默认拒绝写入
     *
     * @param key     数据 key（必须以 supervisor/ | worker/ | shared/ | system/ 开头）
     * @param value   数据值
     * @param agentId 写入者 Agent ID
     * @param role    写入者角色
     * @throws SecurityException 无权限写入时抛出
     */
    public void put(String key, Object value, String agentId, String role) {
        // 归一化：空白 role 等同于 null
        String normalizedRole = (role == null || role.isBlank()) ? null : role;

        // 已知前缀白名单校验
        if (!key.startsWith("supervisor/") && !key.startsWith("worker/")
                && !key.startsWith("shared/") && !key.startsWith("system/")) {
            log.warn("[SharedContext] collaborationId={} key={} 不在已知前缀白名单中，拒绝写入",
                    collaborationId, key);
            throw new SecurityException("未知 key 前缀，拒绝写入: " + key);
        }
        // system/ 前缀仅框架可写
        if (key.startsWith("system/")) {
            log.warn("[SharedContext] collaborationId={} Agent {} 尝试写入 system/ 前缀 key: {}",
                    collaborationId, agentId, key);
            throw new SecurityException("system/ 前缀仅框架可写入，Agent " + agentId + " 无权写入 " + key);
        }
        // 受保护前缀仅允许已设置角色的 Agent 写入
        if (normalizedRole == null) {
            if (key.startsWith("supervisor/") || key.startsWith("worker/")) {
                log.warn("[SharedContext] collaborationId={} Agent {} (role=null) 无权限写入受保护前缀 key: {}",
                        collaborationId, agentId, key);
                throw new SecurityException("Agent " + agentId + " (role=null) 无权限写入受保护前缀 " + key);
            }
        }
        // supervisor/ 前缀仅 supervisor 角色可写
        if (key.startsWith("supervisor/") && !"supervisor".equals(normalizedRole)) {
            log.warn("[SharedContext] collaborationId={} Agent {} (role={}) 无权限写入 supervisor/ 前缀 key: {}",
                    collaborationId, agentId, normalizedRole, key);
            throw new SecurityException("Agent " + agentId + " (role=" + normalizedRole + ") 无权限写入 " + key);
        }
        // worker/ 前缀仅对应 Worker 可写
        if (key.startsWith("worker/") && !key.startsWith("worker/" + agentId + "/")) {
            log.warn("[SharedContext] collaborationId={} Agent {} 无权限写入其他 Worker 的 key: {}",
                    collaborationId, agentId, key);
            throw new SecurityException("Agent " + agentId + " 无权限写入 " + key);
        }
        store.put(key, value);
        log.debug("[SharedContext] collaborationId={} put: key={} by={} role={}",
                collaborationId, key, agentId, normalizedRole);
    }

    /** 读取共享数据 */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = store.get(key);
        if (value == null) {
            log.debug("[SharedContext] collaborationId={} get: key={} -> null", collaborationId, key);
            return Optional.empty();
        }
        log.debug("[SharedContext] collaborationId={} get: key={} -> found", collaborationId, key);
        return Optional.of((T) value);
    }

    /** 读取所有共享数据（快照） */
    public Map<String, Object> snapshot() {
        return Map.copyOf(store);
    }

    /** 删除指定 key */
    public void remove(String key) {
        store.remove(key);
        log.debug("[SharedContext] collaborationId={} remove: key={}", collaborationId, key);
    }

    /** 清空上下文 */
    public void clear() {
        store.clear();
        log.info("[SharedContext] collaborationId={} 上下文已清空", collaborationId);
    }

    /**
     * 原子更新：使用 compute 实现无锁 CAS。
     * 如果当前值等于 expected，则更新为 newValue，返回 true；
     * 否则不更新，返回 false。
     */
    public boolean compareAndSet(String key, Object expected, Object newValue) {
        boolean[] result = {false};
        store.compute(key, (k, current) -> {
            boolean matched = java.util.Objects.equals(current, expected);
            result[0] = matched;
            return matched ? newValue : current;
        });
        return result[0];
    }

    /**
     * 原子计算并更新：对 key 的当前值执行 remappingFunction，写入新值。
     * 使用 ConcurrentHashMap.compute() 保证原子性。
     */
    public Object compute(String key, BiFunction<String, Object, Object> remappingFunction) {
        return store.compute(key, remappingFunction);
    }
}
