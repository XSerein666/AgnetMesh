package com.jewel.a2a.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jewel.a2a.repository.entity.ConversationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会话 Mapper
 */
@Mapper
public interface ConversationMapper extends BaseMapper<ConversationEntity> {

    @Select("SELECT * FROM conversation WHERE session_id = #{sessionId}")
    ConversationEntity findBySessionId(@Param("sessionId") String sessionId);
}