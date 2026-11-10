package com.fenglin.common.security;

/**
 * 工作流中心权限点（单一来源）。
 *
 * <p>与 {@code WorkflowAdminController} / 工作流中心权限种子保持一致；
 * 各业务服务的流程自动部署必须使用 {@link #DEFINITION_DEPLOY}，
 * 否则首次提交审批会因 403 被吞成“流程定义不存在”。</p>
 */
public final class WorkflowPermissions {

    /** 发起审批实例。 */
    public static final String INSTANCE_START = "biz:workflow:instance:start";

    /** 部署流程定义（工作流中心管理端要求的管理权限点）。 */
    public static final String DEFINITION_DEPLOY = "tenant:workflow:def:manage";

    private WorkflowPermissions() {
    }
}
