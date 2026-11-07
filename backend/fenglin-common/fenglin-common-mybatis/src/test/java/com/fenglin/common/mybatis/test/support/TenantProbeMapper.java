package com.fenglin.common.mybatis.test.support;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TenantProbeMapper {

    @Select("SELECT id FROM tenant_probe ORDER BY id")
    List<Long> findIds();

    @Select("SELECT pg_backend_pid()")
    int backendPid();

    @Select("SELECT current_setting('app.tenant_id', true)")
    String currentTenantSetting();
}
