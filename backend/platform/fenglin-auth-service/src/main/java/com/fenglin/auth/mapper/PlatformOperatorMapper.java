package com.fenglin.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fenglin.auth.entity.PlatformOperatorEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * OPEN-06：平台运维白名单查询。全局表，不使用租户条件。
 */
@Mapper
public interface PlatformOperatorMapper extends BaseMapper<PlatformOperatorEntity> {
}
