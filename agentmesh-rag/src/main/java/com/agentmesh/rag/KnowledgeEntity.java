package com.agentmesh.rag;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库实体
 */
@Data
@TableName("AgentMesh_knowledge")
public class KnowledgeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String content;
    private String embedding;   // pgvector VECTOR(1024) 映射为字符串
    private String category;
    private String metadata;    // JSONB 映射为字符串

    @TableField(exist = false)
    private Double similarity;  // 检索结果相似度（非表字段）

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}