-- CRM-09 方案 A：报价审批流程完成后回调 CRM 回写报价状态。
-- 用途：工作流中心在 crm_quotation_approval 实例结束时（PROCESS_COMPLETED）调用 CRM 内部回调，
--       由报价状态机决定 APPROVED / REJECTED，替代管理端直调 /internal/quotations/{id}/approval。
-- 范围：为演示租户 9001 播种一条回调配置；其它租户按需写入同一 process_key 的记录。
-- 回滚：DELETE FROM hub_wf_callback WHERE process_key = 'crm_quotation_approval' AND tenant_id = 9001;
SET search_path TO fenglin_hub, public;

INSERT INTO hub_wf_callback (id, tenant_id, process_key, callback_url, events, timeout_ms, max_retry,
                             enabled, created_by, created_at, updated_by, updated_at, deleted, version)
SELECT 2121884000000000001, 9001, 'crm_quotation_approval',
       'http://localhost:18095/api/crm/v1/internal/quotations/approval/callback',
       'PROCESS_COMPLETED', 3000, 3, 1, 9101, now(), 9101, now(), 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM hub_wf_callback
    WHERE tenant_id = 9001 AND process_key = 'crm_quotation_approval' AND deleted = 0
);

COMMENT ON TABLE hub_wf_callback IS '工作流回调配置（C-12 待文档同步）；CRM-09 起 crm_quotation_approval 回调 CRM 回写报价状态';
