package com.agentmesh.core.tool.marketplace.install;

import com.agentmesh.core.tool.Tool;
import com.agentmesh.core.tool.ToolRegistry;
import com.agentmesh.core.tool.marketplace.exception.ToolAlreadyInstalledException;
import com.agentmesh.core.tool.marketplace.execution.RemoteToolProxy;
import com.agentmesh.core.tool.marketplace.execution.ToolExecutionBridge;
import com.agentmesh.core.tool.marketplace.model.ToolExecutionDescriptor;
import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;
import com.agentmesh.core.tool.marketplace.service.ToolMarketplace;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 工具安装管理器。
 * 管理本地 Agent 已安装的工具，支持安装/卸载/升级。
 */
@Slf4j
public class ToolInstallManager {

    private final ToolRegistry toolRegistry;
    private final ToolMarketplace marketplace;
    private final ApplicationContext applicationContext;

    /**
     * 工具执行桥接器（可选依赖）。
     * Phase 1 部署时为 null，仅支持 LOCAL_BEAN 类型工具。
     * Phase 2+ 部署时注入，支持 REMOTE_RPC 和 MCP_ENDPOINT 类型。
     */
    private final ToolExecutionBridge executionBridge;

    /** 已安装工具记录：toolId -> 已安装版本 */
    private final Map<String, ToolVersion> installedTools = new ConcurrentHashMap<>();

    /** 反向依赖索引：depToolId -> Set<依赖于它的 toolId> */
    private final Map<String, Set<String>> reverseDependencyIndex = new ConcurrentHashMap<>();

    /** 本地依赖关系缓存：toolId -> Set<依赖的工具 ID> */
    private final Map<String, Set<String>> localDependencyCache = new ConcurrentHashMap<>();

    /** 安装/卸载锁 */
    private final ReentrantReadWriteLock installLock = new ReentrantReadWriteLock();

    // ========== Metrics ==========

    private final Counter installSuccessCounter;
    private final Counter installFailureCounter;
    private final Counter uninstallCounter;
    private final Counter upgradeSuccessCounter;
    private final Counter upgradeFailureCounter;
    private final Timer installTimer;

    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    public ToolInstallManager(ToolRegistry toolRegistry, ToolMarketplace marketplace,
                               ApplicationContext applicationContext,
                               ObjectProvider<ToolExecutionBridge> bridgeProvider,
                               MeterRegistry meterRegistry) {
        this.toolRegistry = toolRegistry;
        this.marketplace = marketplace;
        this.applicationContext = applicationContext;
        this.executionBridge = bridgeProvider.getIfAvailable();

        this.installSuccessCounter = Counter.builder("tool.install.count")
                .tag("result", "SUCCESS")
                .description("工具安装成功次数")
                .register(meterRegistry);
        this.installFailureCounter = Counter.builder("tool.install.count")
                .tag("result", "FAILURE")
                .description("工具安装失败次数")
                .register(meterRegistry);
        this.uninstallCounter = Counter.builder("tool.uninstall.count")
                .description("工具卸载次数")
                .register(meterRegistry);
        this.upgradeSuccessCounter = Counter.builder("tool.upgrade.count")
                .tag("result", "SUCCESS")
                .description("工具升级成功次数")
                .register(meterRegistry);
        this.upgradeFailureCounter = Counter.builder("tool.upgrade.count")
                .tag("result", "FAILURE")
                .description("工具升级失败次数")
                .register(meterRegistry);
        this.installTimer = Timer.builder("tool.install.duration")
                .description("工具安装耗时")
                .register(meterRegistry);
    }

    /**
     * 从市场安装工具（加锁入口）。
     */
    public ToolMetadata install(String toolId, ToolVersion version) {
        long start = System.nanoTime();
        installLock.writeLock().lock();
        try {
            ToolMetadata result = installInternal(toolId, version);
            installSuccessCounter.increment();
            log.info("[ToolInstallManager] 安装工具: {} v{}", toolId, result.getVersion());
            return result;
        } catch (Exception e) {
            installFailureCounter.increment();
            log.error("[ToolInstallManager] 安装失败: {} v{}", toolId, version, e);
            throw e;
        } finally {
            installLock.writeLock().unlock();
            installTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 内部安装方法（不加锁，供 validateDependencies 递归调用）。
     */
    private ToolMetadata installInternal(String toolId, ToolVersion version) {
        ToolVersion installed = installedTools.get(toolId);
        if (installed != null) {
            if (version == null || version.equals(installed)) {
                throw new ToolAlreadyInstalledException(toolId);
            }
            log.info("[ToolInstallManager] 检测到已安装旧版本，将升级: {} v{} → v{}", toolId, installed, version);
        }

        ToolMetadata metadata = (version != null)
                ? marketplace.getDetail(toolId, version)
                : marketplace.getDetail(toolId);

        if (metadata == null || metadata.getStatus() != ToolMetadata.ToolStatus.PUBLISHED) {
            throw new IllegalArgumentException("工具不可用: " + toolId
                    + (version != null ? " v" + version : ""));
        }

        validateDependencies(metadata, new HashSet<>());

        Tool<?, ?> proxyTool = createToolProxy(metadata);
        toolRegistry.register(proxyTool);

        ToolVersion installedVersion = metadata.getVersion();
        installedTools.put(toolId, installedVersion);

        if (metadata.getDependencies() != null) {
            for (String depId : metadata.getDependencies()) {
                reverseDependencyIndex
                        .computeIfAbsent(depId, k -> ConcurrentHashMap.newKeySet())
                        .add(toolId);
            }
            localDependencyCache.put(toolId, new HashSet<>(metadata.getDependencies()));
        }

        return metadata;
    }

    /**
     * 递归依赖检查 + 循环依赖检测。
     */
    private void validateDependencies(ToolMetadata metadata, Set<String> visited) {
        if (!visited.add(metadata.getToolId())) {
            throw new IllegalStateException("检测到循环依赖: " + visited);
        }
        if (metadata.getDependencies() != null) {
            for (String depId : metadata.getDependencies()) {
                if (!installedTools.containsKey(depId)) {
                    log.info("[ToolInstallManager] 自动安装依赖: {} -> {}", metadata.getToolId(), depId);
                    ToolMetadata depMeta = marketplace.getDetail(depId);
                    if (depMeta != null) {
                        validateDependencies(depMeta, visited);
                        try {
                            installInternal(depId, depMeta.getVersion());
                        } catch (Exception e) {
                            visited.remove(depId);
                            throw e;
                        }
                    } else {
                        throw new IllegalStateException("缺少依赖工具: " + depId + "，且市场中未找到");
                    }
                }
            }
        }
    }

    /**
     * 创建工具代理。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Tool<?, ?> createToolProxy(ToolMetadata metadata) {
        ToolExecutionDescriptor descriptor = metadata.getExecutionDescriptor();
        if (descriptor == null) {
            throw new IllegalStateException("工具缺少执行描述符: " + metadata.getToolId());
        }

        return switch (descriptor.getType()) {
            case LOCAL_BEAN -> {
                Object bean = applicationContext.getBean(descriptor.getBeanName());
                if (bean instanceof Tool<?, ?> tool) {
                    yield tool;
                }
                throw new IllegalStateException("Bean 不是 Tool 类型: " + descriptor.getBeanName());
            }
            case REMOTE_RPC, MCP_ENDPOINT -> {
                if (executionBridge == null) {
                    throw new IllegalStateException(
                            "无法安装远程工具 " + metadata.getToolId() + "：ToolExecutionBridge 未就绪（需要 Phase 2+）");
                }
                yield new RemoteToolProxy(metadata.getToolId(), metadata.getName(),
                        metadata.getDescription(), metadata.getInputSchema(),
                        descriptor, executionBridge);
            }
        };
    }

    /**
     * 卸载工具。
     */
    public void uninstall(String toolId) {
        installLock.writeLock().lock();
        try {
            Set<String> dependents = reverseDependencyIndex.get(toolId);
            if (dependents != null && !dependents.isEmpty()) {
                throw new IllegalStateException(
                        "工具被以下工具依赖，请先卸载: " + dependents);
            }

            toolRegistry.unregister(toolId);
            installedTools.remove(toolId);

            Set<String> deps = localDependencyCache.remove(toolId);
            if (deps != null) {
                for (String depId : deps) {
                    Set<String> set = reverseDependencyIndex.get(depId);
                    if (set != null) {
                        set.remove(toolId);
                        if (set.isEmpty()) {
                            reverseDependencyIndex.remove(depId);
                        }
                    }
                }
            }

            uninstallCounter.increment();
            log.info("[ToolInstallManager] 卸载工具: {}", toolId);
        } finally {
            installLock.writeLock().unlock();
        }
    }

    /**
     * 升级工具。
     */
    public ToolMetadata upgrade(String toolId, ToolVersion targetVersion) {
        installLock.writeLock().lock();
        try {
            ToolVersion oldVersion = installedTools.get(toolId);
            if (oldVersion == null) {
                throw new IllegalStateException("工具未安装: " + toolId);
            }

            ToolMetadata newMetadata = marketplace.getDetail(toolId, targetVersion);
            if (newMetadata == null) {
                throw new IllegalArgumentException("目标版本不存在: " + toolId + " v" + targetVersion);
            }

            ToolMetadata oldMetadata = marketplace.getDetail(toolId, oldVersion);
            try {
                Tool<?, ?> newProxy = createToolProxy(newMetadata);
                toolRegistry.register(newProxy);

                installedTools.put(toolId, targetVersion);

                if (newMetadata.getDependencies() != null) {
                    for (String depId : newMetadata.getDependencies()) {
                        reverseDependencyIndex
                                .computeIfAbsent(depId, k -> ConcurrentHashMap.newKeySet())
                                .add(toolId);
                    }
                }
                if (oldMetadata != null && oldMetadata.getDependencies() != null) {
                    for (String depId : oldMetadata.getDependencies()) {
                        Set<String> set = reverseDependencyIndex.get(depId);
                        if (set != null) {
                            set.remove(toolId);
                            if (set.isEmpty()) {
                            reverseDependencyIndex.remove(depId);
                        }
                        }
                    }
                }

                upgradeSuccessCounter.increment();
                log.info("[ToolInstallManager] 升级成功: {} v{} → v{}", toolId, oldVersion, targetVersion);
                return newMetadata;
            } catch (Exception e) {
                upgradeFailureCounter.increment();
                log.error("[ToolInstallManager] 升级失败，回滚到旧版本: {} v{}", toolId, oldVersion, e);
                if (oldMetadata != null) {
                    try {
                        Tool<?, ?> oldProxy = createToolProxy(oldMetadata);
                        toolRegistry.register(oldProxy);
                        installedTools.put(toolId, oldVersion);
                        log.info("[ToolInstallManager] 回滚成功: {} v{}", toolId, oldVersion);
                    } catch (Exception rollbackEx) {
                        installedTools.remove(toolId);
                        log.error("[ToolInstallManager] 回滚失败，工具 {} 已丢失，已清理安装记录", toolId, rollbackEx);
                        throw new IllegalStateException("升级失败且回滚失败，工具 " + toolId + " 已丢失", rollbackEx);
                    }
                }
                throw new IllegalStateException("升级失败: " + toolId, e);
            }
        } finally {
            installLock.writeLock().unlock();
        }
    }

    /**
     * 检查更新。
     */
    public List<ToolMetadata> checkUpdates() {
        return installedTools.entrySet().stream()
                .map(entry -> {
                    ToolMetadata latest = marketplace.getDetail(entry.getKey());
                    if (latest != null && latest.getVersion().compareTo(entry.getValue()) > 0) {
                        return latest;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 获取已安装工具列表。
     */
    public Map<String, ToolVersion> getInstalledTools() {
        return Map.copyOf(installedTools);
    }
}
