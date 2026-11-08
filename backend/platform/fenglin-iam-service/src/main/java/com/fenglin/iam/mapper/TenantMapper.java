package com.fenglin.iam.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TenantMapper {

    @Select("SELECT code FROM hub_tenant WHERE id = #{id} AND deleted = 0")
    String findCodeById(@Param("id") long id);
}
