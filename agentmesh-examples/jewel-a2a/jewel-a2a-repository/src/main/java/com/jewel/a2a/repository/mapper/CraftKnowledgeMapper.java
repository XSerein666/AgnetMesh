package com.jewel.a2a.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jewel.a2a.repository.entity.CraftKnowledgeEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工艺知识库 Mapper
 */
@Mapper
public interface CraftKnowledgeMapper extends BaseMapper<CraftKnowledgeEntity> {

    @Select("SELECT COUNT(*) FROM craft_knowledge WHERE title = #{title}")
    int countByTitle(@Param("title") String title);

    @Select("SELECT id, title, content, category, metadata, created_at, " +
            "1 - (embedding <=> CAST(#{queryVector} AS vector)) AS similarity " +
            "FROM craft_knowledge " +
            "WHERE 1 - (embedding <=> CAST(#{queryVector} AS vector)) >= #{threshold} " +
            "ORDER BY embedding <=> CAST(#{queryVector} AS vector) " +
            "LIMIT #{limit}")
    List<CraftKnowledgeEntity> searchSimilar(
            @Param("queryVector") String queryVector,
            @Param("threshold") double threshold,
            @Param("limit") int limit);

    @Insert("INSERT INTO craft_knowledge (title, content, embedding, category, metadata) " +
            "VALUES (#{title}, #{content}, CAST(#{embedding} AS vector), #{category}, CAST(#{metadata} AS jsonb))")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWithVector(CraftKnowledgeEntity entity);
}