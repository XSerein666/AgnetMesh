package com.jewel.a2a.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jewel.a2a.repository.entity.TaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务 Mapper
 */
@Mapper
public interface TaskMapper extends BaseMapper<TaskEntity> {
}
