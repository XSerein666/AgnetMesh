package com.jewel.a2a.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工艺知识库实体
 */
@Data
@TableName("craft_knowledge")
public class CraftKnowledgeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String content;
    private String embedding;   // pgvector VECTOR(1024) 映射为字符串
    private String category;    // craft_standard / design_case
    private String metadata;    // JSONB 映射为字符串

    @TableField(exist = false)
    private Double similarity;  // 检索结果相似度（非表字段）

    private LocalDateTime createdAt;
}