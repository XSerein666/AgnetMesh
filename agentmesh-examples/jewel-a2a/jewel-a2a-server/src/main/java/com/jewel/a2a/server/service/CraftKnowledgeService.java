package com.jewel.a2a.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewel.a2a.repository.entity.CraftKnowledgeEntity;
import com.jewel.a2a.repository.mapper.CraftKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工艺知识库管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CraftKnowledgeService {

    private final CraftKnowledgeMapper mapper;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 语义检索相关知识
     */
    public List<CraftKnowledgeEntity> search(String query, double threshold, int limit) {
        List<Double> vector = embeddingService.embed(query);
        String vectorStr = vector.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
        return mapper.searchSimilar(vectorStr, threshold, limit);
    }

    /**
     * 向量化并入库
     */
    public void insert(String title, String content, String category, Object metadata) {
        List<Double> vector = embeddingService.embed(content);
        String vectorStr = vector.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));

        CraftKnowledgeEntity entity = new CraftKnowledgeEntity();
        entity.setTitle(title);
        entity.setContent(content);
        entity.setEmbedding(vectorStr);
        entity.setCategory(category);
        try {
            entity.setMetadata(objectMapper.writeValueAsString(metadata));
        } catch (JsonProcessingException e) {
            entity.setMetadata("{}");
        }
        mapper.insertWithVector(entity);
        log.info("[CraftKnowledgeService] 知识入库: {}", title);
    }

    /**
     * 将检索结果格式化为上下文文本
     */
    public String buildContext(List<CraftKnowledgeEntity> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【参考知识】\n");
        for (int i = 0; i < results.size(); i++) {
            CraftKnowledgeEntity r = results.get(i);
            sb.append(i + 1).append(". [").append(r.getCategory()).append("] ")
              .append(r.getTitle()).append("\n")
              .append("   ").append(r.getContent()).append("\n");
        }
        return sb.toString();
    }
}
