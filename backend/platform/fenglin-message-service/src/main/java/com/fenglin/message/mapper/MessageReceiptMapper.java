package com.fenglin.message.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MessageReceiptMapper {

    @Insert("""
            INSERT INTO hub_message_receipt (id, tenant_id, message_id, channel_msg_id, status,
                                             retry_count, error_msg, created_at, updated_at, deleted, version)
            VALUES (#{id}, #{tenantId}, #{messageId}, #{channelMsgId,jdbcType=VARCHAR}, #{status},
                    #{retryCount}, #{errorMsg,jdbcType=VARCHAR}, now(), now(), 0, 0)
            """)
    int insert(@Param("id") long id,
               @Param("tenantId") long tenantId,
               @Param("messageId") long messageId,
               @Param("channelMsgId") String channelMsgId,
               @Param("status") String status,
               @Param("retryCount") int retryCount,
               @Param("errorMsg") String errorMsg);
}
