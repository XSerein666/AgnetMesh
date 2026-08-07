package com.agentmesh.core.tool.marketplace.repository;

import com.agentmesh.core.tool.marketplace.model.ToolExecutionDescriptor;
import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 内存工具仓库实现。
 * 使用 CopyOnWriteArrayList 保证读写并发安全，读取操作获取快照避免 CME。
 */
@Slf4j
public class InMemoryToolRepository implements ToolRepository {

    /** key = toolId, value = 该工具的所有版本列表（CopyOnWriteArrayList 保证读操作并发安全） */
    private final Map<String, CopyOnWriteArrayList<ToolMetadata>> store = new ConcurrentHashMap<>();

    @Override
    public ToolMetadata save(ToolMetadata metadata) {
        store.compute(metadata.getToolId(), (id, versions) -> {
            if (versions == null) {
                versions = new CopyOnWriteArrayList<>();
            }
            List<ToolMetadata> temp = new ArrayList<>(versions);
            temp.removeIf(v -> v.getVersion().equals(metadata.getVersion()));
            temp.add(metadata);
            temp.sort((a, b) -> b.getVersion().compareTo(a.getVersion()));

            // 维护 availableVersions：收集所有版本号，同步到每个版本元数据中
            List<ToolVersion> allVersions = temp.stream()
                    .map(ToolMetadata::getVersion)
                    .distinct()
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
            temp.forEach(m -> m.setAvailableVersions(new ArrayList<>(allVersions)));

            return new CopyOnWriteArrayList<>(temp);
        });
        log.info("[ToolRepository] 保存工具: {} v{}", metadata.getToolId(), metadata.getVersion());
        return copy(metadata);
    }

    @Override
    public Optional<ToolMetadata> findById(String toolId) {
        List<ToolMetadata> versions = store.get(toolId);
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(copy(versions.get(0)));
    }

    @Override
    public Optional<ToolMetadata> findByIdAndVersion(String toolId, ToolVersion version) {
        List<ToolMetadata> versions = store.get(toolId);
        if (versions == null) {
            return Optional.empty();
        }
        return versions.stream()
                .filter(v -> v.getVersion().equals(version))
                .findFirst()
                .map(this::copy);
    }

    @Override
    public List<ToolMetadata> findAllVersions(String toolId) {
        List<ToolMetadata> versions = store.get(toolId);
        if (versions == null) {
            return List.of();
        }
        return versions.stream().map(this::copy).toList();
    }

    @Override
    public List<ToolMetadata> findAllPublished() {
        return store.values().stream()
                .flatMap(list -> List.copyOf(list).stream())
                .filter(m -> m.getStatus() == ToolMetadata.ToolStatus.PUBLISHED)
                .collect(Collectors.groupingBy(ToolMetadata::getToolId,
                        Collectors.maxBy((a, b) -> a.getVersion().compareTo(b.getVersion()))))
                .values().stream()
                .flatMap(Optional::stream)
                .map(this::copy)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolMetadata> findByCategory(String category) {
        return store.values().stream()
                .flatMap(list -> List.copyOf(list).stream())
                .filter(m -> m.getCategory() != null && category.equals(m.getCategory())
                        && m.getStatus() == ToolMetadata.ToolStatus.PUBLISHED)
                .map(this::copy)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolMetadata> findByTag(String tag) {
        return store.values().stream()
                .flatMap(list -> List.copyOf(list).stream())
                .filter(m -> m.getTags() != null && m.getTags().contains(tag)
                        && m.getStatus() == ToolMetadata.ToolStatus.PUBLISHED)
                .map(this::copy)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolMetadata> findByPublisher(String publisher) {
        return store.values().stream()
                .flatMap(list -> List.copyOf(list).stream())
                .filter(m -> publisher.equals(m.getPublisher()))
                .map(this::copy)
                .collect(Collectors.toList());
    }

    @Override
    public SearchResult search(String keyword, int offset, int limit) {
        List<String> tokens = tokenize(keyword);
        List<ToolMetadata> allMatched = store.values().stream()
                .flatMap(list -> List.copyOf(list).stream())
                .filter(m -> m.getStatus() == ToolMetadata.ToolStatus.PUBLISHED)
                .filter(m -> matchesAnyToken(m, tokens))
                .map(this::copy)
                .sorted(this::relevanceScore)
                .collect(Collectors.toList());

        long total = allMatched.size();
        List<ToolMetadata> page = allMatched.stream()
                .skip(offset)
                .limit(limit)
                .toList();
        return new SearchResult(page, total);
    }

    /**
     * 计算相关性得分。
     * 排序依据：安装量 + 评分（加权），安装量高、评分高的工具排在前面。
     */
    private int relevanceScore(ToolMetadata a, ToolMetadata b) {
        return Integer.compare(popularityScore(b), popularityScore(a));
    }

    private int popularityScore(ToolMetadata m) {
        int score = 0;
        if (m.getInstallCount() != null) {
            score += m.getInstallCount();
        }
        if (m.getAverageRating() != null) {
            score += (int) (m.getAverageRating() * 10);
        }
        return score;
    }

    /**
     * 简单分词：按空格和常见中文标点分割。
     */
    private List<String> tokenize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return Arrays.asList(keyword.toLowerCase().split("[\\s，,、。；;]+"));
    }

    private boolean matchesAnyToken(ToolMetadata m, List<String> tokens) {
        if (tokens.isEmpty()) {
            return true;
        }
        return tokens.stream().anyMatch(token ->
                (m.getName() != null && m.getName().toLowerCase().contains(token))
                || (m.getDescription() != null && m.getDescription().toLowerCase().contains(token))
                || (m.getTags() != null && m.getTags().stream().anyMatch(t -> t.toLowerCase().contains(token))));
    }

    /**
     * 防御性复制（深拷贝元数据，避免外部修改内部状态）。
     */
    private ToolMetadata copy(ToolMetadata original) {
        return ToolMetadata.builder()
                .toolId(original.getToolId())
                .name(original.getName())
                .description(original.getDescription())
                .category(original.getCategory())
                .tags(original.getTags() != null ? List.copyOf(original.getTags()) : null)
                .publisher(original.getPublisher())
                .version(original.getVersion())
                .availableVersions(original.getAvailableVersions() != null
                        ? List.copyOf(original.getAvailableVersions()) : null)
                .inputSchema(original.getInputSchema() != null
                        ? Map.copyOf(original.getInputSchema()) : null)
                .outputSchema(original.getOutputSchema() != null
                        ? Map.copyOf(original.getOutputSchema()) : null)
                .status(original.getStatus())
                .executionDescriptor(copyExecutionDescriptor(original.getExecutionDescriptor()))
                .averageRating(original.getAverageRating())
                .reviewCount(original.getReviewCount())
                .installCount(original.getInstallCount())
                .createdAt(original.getCreatedAt())
                .updatedAt(original.getUpdatedAt())
                .usageExample(original.getUsageExample())
                .dependencies(original.getDependencies() != null
                        ? List.copyOf(original.getDependencies()) : null)
                .build();
    }

    /**
     * 深拷贝 ToolExecutionDescriptor。
     */
    private ToolExecutionDescriptor copyExecutionDescriptor(ToolExecutionDescriptor original) {
        if (original == null) {
            return null;
        }
        return ToolExecutionDescriptor.builder()
                .type(original.getType())
                .beanName(original.getBeanName())
                .remoteEndpointUrl(original.getRemoteEndpointUrl())
                .mcpServerUrl(original.getMcpServerUrl())
                .mcpToolName(original.getMcpToolName())
                .inputSerialization(original.getInputSerialization())
                .outputSerialization(original.getOutputSerialization())
                .timeoutMillis(original.getTimeoutMillis())
                .retryCount(original.getRetryCount())
                .extraConfig(original.getExtraConfig() != null
                        ? Map.copyOf(original.getExtraConfig()) : null)
                .build();
    }

    @Override
    public void deleteById(String toolId) {
        store.remove(toolId);
        log.info("[ToolRepository] 删除工具: {}", toolId);
    }

    @Override
    public void deleteByIdAndVersion(String toolId, ToolVersion version) {
        store.computeIfPresent(toolId, (id, versions) -> {
            versions.removeIf(v -> v.getVersion().equals(version));
            if (versions.isEmpty()) {
                return null;
            }
            return versions;
        });
        log.info("[ToolRepository] 删除工具版本: {} v{}", toolId, version);
    }

    @Override
    public long count() {
        return store.values().stream().mapToLong(List::size).sum();
    }

    /**
     * 获取所有内部存储的版本数据（供 JsonFileToolRepository 序列化使用）。
     */
    Collection<List<ToolMetadata>> getAllVersionsInternal() {
        return store.values().stream()
                .map(v -> (List<ToolMetadata>) v)
                .toList();
    }

    @Override
    public void flush() { /* 内存实现无需 flush */ }

    @Override
    public void load() { /* 内存实现无需 load */ }
}
