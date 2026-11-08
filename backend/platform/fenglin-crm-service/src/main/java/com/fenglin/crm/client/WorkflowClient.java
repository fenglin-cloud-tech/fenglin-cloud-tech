package com.fenglin.crm.client;

import java.math.BigDecimal;

/**
 * 工作流中心客户端：报价审批复用 HUB-API-501 启动实例接口，不重复实现审批引擎。
 */
public interface WorkflowClient {

    /**
     * 提交报价审批，返回工作流实例号（用于回写 approve_instance_id）。
     */
    String startQuotationApproval(String permissions, String requestId,
                                  String quotationNo, long quotationId, long customerId,
                                  BigDecimal totalAmount, BigDecimal discountRate);
}
