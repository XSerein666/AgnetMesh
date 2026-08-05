package com.agentmesh.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库管理服务
 * 配置通过构造函数注入，KnowledgeMapper 可选（有 DB 时注入，无 DB 时使用内存存储）
 */
@Slf4j
public class KnowledgeService {

    private final EmbeddingService embeddingService;
    private final RagConfig config;
    private final KnowledgeMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 内存存储（无 DB 模式下降级使用）
    private final List<KnowledgeEntity> memoryStore = new ArrayList<>();
    private final List<double[]> memoryVectors = new ArrayList<>();

    /**
     * 无 DB 模式：使用内存存储
     */
    public KnowledgeService(EmbeddingService embeddingService, RagConfig config) {
        this(embeddingService, config, null);
    }

    /**
     * 完整模式：带 DB 查询能力
     */
    public KnowledgeService(EmbeddingService embeddingService, RagConfig config, KnowledgeMapper mapper) {
        this.embeddingService = embeddingService;
        this.config = config;
        this.mapper = mapper;
    }

    /**
     * 语义检索相关知识
     */
    public List<KnowledgeEntity> search(String query) {
        return search(query, config.getSimilarityThreshold(), config.getTopK());
    }

    /**
     * 语义检索相关知识（指定参数）
     */
    public List<KnowledgeEntity> search(String query, double threshold, int limit) {
        if (mapper != null) {
            // DB 模式
            List<Double> vector = embeddingService.embed(query);
            String vectorStr = vector.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",", "[", "]"));
            return mapper.searchSimilar(vectorStr, threshold, limit);
        }

        // 内存模式
        if (memoryStore.isEmpty()) {
            log.debug("[KnowledgeService] 知识库为空，返回空结果");
            return List.of();
        }

        List<Double> queryVector = embeddingService.embed(query);
        double[] queryVec = queryVector.stream().mapToDouble(Double::doubleValue).toArray();

        List<KnowledgeEntity> results = new ArrayList<>();
        for (int i = 0; i < memoryStore.size(); i++) {
            double similarity = cosineSimilarity(queryVec, memoryVectors.get(i));
            if (similarity >= threshold) {
                KnowledgeEntity src = memoryStore.get(i);
                KnowledgeEntity result = new KnowledgeEntity();
                result.setTitle(src.getTitle());
                result.setContent(src.getContent());
                result.setCategory(src.getCategory());
                result.setSimilarity(similarity);
                results.add(result);
            }
        }

        results.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        if (results.size() > limit) {
            results = results.subList(0, limit);
        }
        log.info("[KnowledgeService] 内存检索: query={}, 命中={}条", query, results.size());
        return results;
    }

    /**
     * 向量化并入库
     */
    public void insert(String title, String content, String category, Object metadata) {
        List<Double> vector = embeddingService.embed(content);

        KnowledgeEntity entity = new KnowledgeEntity();
        entity.setTitle(title);
        entity.setContent(content);
        entity.setCategory(category);
        try {
            entity.setMetadata(objectMapper.writeValueAsString(metadata));
        } catch (JsonProcessingException e) {
            entity.setMetadata("{}");
        }

        if (mapper != null) {
            // DB 模式
            String vectorStr = vector.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",", "[", "]"));
            entity.setEmbedding(vectorStr);
            mapper.insertWithVector(entity);
        } else {
            // 内存模式
            entity.setSimilarity(1.0);
            memoryStore.add(entity);
            memoryVectors.add(vector.stream().mapToDouble(Double::doubleValue).toArray());
        }
        log.info("[KnowledgeService] 知识入库: {}", title);
    }

    /**
     * 获取内存存储中的文档数量
     */
    public int getMemoryStoreSize() {
        return memoryStore.size();
    }

    /**
     * 将检索结果格式化为上下文文本
     */
    public String buildContext(List<KnowledgeEntity> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【参考知识】\n");
        for (int i = 0; i < results.size(); i++) {
            KnowledgeEntity r = results.get(i);
            sb.append(i + 1).append(". [").append(r.getCategory()).append("] ")
              .append(r.getTitle()).append("\n")
              .append("   ").append(r.getContent()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 余弦相似度
     */
    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}