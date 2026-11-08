package com.fenglin.workflow.service;

/**
 * 流程状态变更事件，事务提交后触发业务回调（与业务流程解耦）。
 */
public record WorkflowStateChangedEvent(long tenantId, long instanceId, String action) {
}
