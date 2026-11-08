package com.fenglin.ai.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface AiMeteringMapper {

    @Insert("""
            INSERT INTO hub_ai_metering (id, tenant_id, host_product, capability, mode, stat_date,
                                         call_count, token_in, token_out, cost, created_by, created_at,
                                         updated_by, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{hostProduct}, #{capability}, #{mode}, #{statDate},
                    #{callCount}, #{tokenIn}, #{tokenOut}, #{cost}, #{operatorId}, now(),
                    #{operatorId}, now(), 0, 0)
            ON CONFLICT (tenant_id, host_product, capability, mode, stat_date) WHERE deleted = 0
            DO UPDATE SET call_count = hub_ai_metering.call_count + EXCLUDED.call_count,
                          token_in = hub_ai_metering.token_in + EXCLUDED.token_in,
                          token_out = hub_ai_metering.token_out + EXCLUDED.token_out,
                          cost = hub_ai_metering.cost + EXCLUDED.cost,
                          updated_at = now(),
                          version = hub_ai_metering.version + 1
            """)
    int upsert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("hostProduct") String hostProduct,
               @Param("capability") String capability,
               @Param("mode") String mode,
               @Param("statDate") LocalDate statDate,
               @Param("callCount") long callCount,
               @Param("tokenIn") long tokenIn,
               @Param("tokenOut") long tokenOut,
               @Param("cost") BigDecimal cost,
               @Param("operatorId") Long operatorId);

    @Select("""
            <script>
            SELECT host_product, capability, mode, call_count, token_in, token_out, cost, stat_date
              FROM hub_ai_metering
             WHERE tenant_id = #{tenantId} AND deleted = 0
            <if test="mode != null and mode != ''"> AND mode = #{mode}</if>
            <if test="hostProduct != null and hostProduct != ''"> AND host_product = #{hostProduct}</if>
            <if test="from != null"> AND stat_date &gt;= #{from}</if>
            <if test="to != null"> AND stat_date &lt;= #{to}</if>
             ORDER BY stat_date DESC, host_product, capability
            </script>
            """)
    List<Map<String, Object>> list(@Param("tenantId") long tenantId,
                                   @Param("mode") String mode,
                                   @Param("hostProduct") String hostProduct,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);
}
