package com.fenglin.crm.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Mapper
public interface ActivityMapper {

    String VIEW_COLUMNS = " id AS \"activityId\", biz_type AS \"bizType\", biz_id AS \"bizId\", "
            + "activity_type AS \"activityType\", subject AS \"subject\", content AS \"content\", "
            + "duration_min AS \"durationMin\", result AS \"result\", next_plan AS \"nextPlan\", "
            + "(EXTRACT(EPOCH FROM occurred_at) * 1000)::bigint AS \"occurredMs\", owner_id AS \"ownerId\" ";

    @Insert("INSERT INTO crm_activity (id, tenant_id, biz_type, biz_id, activity_type, subject, content, "
            + "duration_min, result, next_plan, occurred_at, owner_id, created_by, updated_by) "
            + "VALUES (#{id}, #{tenantId}, #{bizType}, #{bizId}, #{activityType}, #{subject}, #{content}, "
            + "#{durationMin}, #{result}, #{nextPlan}, #{occurredAt}, #{ownerId}, #{operatorId}, #{operatorId})")
    int insert(@Param("id") long id, @Param("tenantId") long tenantId, @Param("bizType") String bizType,
               @Param("bizId") long bizId, @Param("activityType") String activityType,
               @Param("subject") String subject, @Param("content") String content,
               @Param("durationMin") Integer durationMin, @Param("result") String result,
               @Param("nextPlan") String nextPlan, @Param("occurredAt") Instant occurredAt,
               @Param("ownerId") Long ownerId, @Param("operatorId") long operatorId);

    @Select("SELECT " + VIEW_COLUMNS + " FROM crm_activity WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND biz_type = #{bizType} AND biz_id = #{bizId} ORDER BY occurred_at DESC LIMIT #{limit}")
    List<Map<String, Object>> listByBiz(@Param("tenantId") long tenantId, @Param("bizType") String bizType,
                                        @Param("bizId") long bizId, @Param("limit") int limit);
}
