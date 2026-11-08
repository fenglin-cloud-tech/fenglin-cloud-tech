package com.fenglin.search.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Mapper
public interface SearchQueryLogMapper {

    @Insert("""
            INSERT INTO hub_search_query_log (id, tenant_id, user_id, keyword, domains, mode,
                                              hit_count, latency_ms, created_at, deleted)
            VALUES (#{id}, #{tenantId}, #{userId,jdbcType=BIGINT}, #{keyword}, #{domains}, #{mode},
                    #{hitCount}, #{latencyMs}, now(), 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("userId") Long userId,
               @Param("keyword") String keyword,
               @Param("domains") String domains,
               @Param("mode") String mode,
               @Param("hitCount") int hitCount,
               @Param("latencyMs") int latencyMs);

    @Select("""
            <script>
            SELECT keyword, count(*) AS cnt
              FROM hub_search_query_log
             WHERE deleted = 0
             <if test="from != null">AND created_at &gt;= #{from,jdbcType=TIMESTAMP}</if>
             <if test="to != null">AND created_at &lt; #{to,jdbcType=TIMESTAMP}</if>
             GROUP BY keyword ORDER BY cnt DESC, keyword ASC LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> hotKeywords(@Param("from") Instant from,
                                          @Param("to") Instant to,
                                          @Param("limit") int limit);

    @Select("""
            <script>
            SELECT keyword, count(*) AS cnt
              FROM hub_search_query_log
             WHERE deleted = 0 AND hit_count = 0
             <if test="from != null">AND created_at &gt;= #{from,jdbcType=TIMESTAMP}</if>
             <if test="to != null">AND created_at &lt; #{to,jdbcType=TIMESTAMP}</if>
             GROUP BY keyword ORDER BY cnt DESC, keyword ASC LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> zeroResultKeywords(@Param("from") Instant from,
                                                 @Param("to") Instant to,
                                                 @Param("limit") int limit);

    @Select("""
            <script>
            SELECT keyword, count(*) AS cnt
              FROM hub_search_query_log
             WHERE deleted = 0 AND hit_count &gt; 0 AND clicked_doc_id IS NULL
             <if test="from != null">AND created_at &gt;= #{from,jdbcType=TIMESTAMP}</if>
             <if test="to != null">AND created_at &lt; #{to,jdbcType=TIMESTAMP}</if>
             GROUP BY keyword ORDER BY cnt DESC, keyword ASC LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> zeroClickKeywords(@Param("from") Instant from,
                                                @Param("to") Instant to,
                                                @Param("limit") int limit);
}
