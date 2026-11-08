-- CRM 业务域前置数据（可重复执行）
-- 说明：
-- 1) 权限点与字典项属于平台库 fenglin_hub（IAM），不在 CRM 独立库中重复建模；
-- 2) 全部使用 ON CONFLICT DO NOTHING，重复执行不会产生脏数据；
-- 3) crm_quotation_approval 流程定义需要 Flowable 部署，SQL 无法完成，由 CRM 服务首次提单时
--    调用工作流中心管理接口自动部署（见 HttpWorkflowClient#deployQuotationProcess）。
SET search_path TO fenglin_hub, public;

-- ---------- 权限点 ----------
INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8310, 'crm:lead:create', '线索创建', 'API', 'crm', 10, 0),
       (8311, 'crm:lead:view', '线索查看', 'API', 'crm', 11, 0),
       (8312, 'crm:lead:assign', '线索分配', 'API', 'crm', 12, 0),
       (8313, 'crm:lead:transfer', '线索转客户', 'API', 'crm', 13, 0),
       (8314, 'crm:customer:create', '客户建档', 'API', 'crm', 20, 0),
       (8315, 'crm:customer:view', '客户查看', 'API', 'crm', 21, 0),
       (8316, 'crm:customer:manage', '客户归属变更', 'API', 'crm', 22, 0),
       (8317, 'crm:contact:view', '联系人查看', 'API', 'crm', 23, 0),
       (8318, 'crm:contact:manage', '联系人维护', 'API', 'crm', 24, 0),
       (8319, 'crm:opportunity:create', '商机创建', 'API', 'crm', 30, 0),
       (8320, 'crm:opportunity:view', '商机查看', 'API', 'crm', 31, 0),
       (8321, 'crm:opportunity:manage', '商机阶段推进', 'API', 'crm', 32, 0),
       (8322, 'crm:quotation:create', '报价创建', 'API', 'crm', 40, 0),
       (8323, 'crm:quotation:view', '报价查看', 'API', 'crm', 41, 0),
       (8324, 'crm:quotation:submit', '报价提交审批', 'API', 'crm', 42, 0),
       (8325, 'crm:quotation:approve', '报价审批回调', 'API', 'crm', 43, 0),
       (8326, 'crm:contract:view', '合同查看', 'API', 'crm', 50, 0),
       (8327, 'crm:contract:manage', '合同及回款维护', 'API', 'crm', 51, 0),
       (8328, 'crm:activity:create', '跟进记录创建', 'API', 'crm', 60, 0),
       (8329, 'crm:activity:view', '跟进记录查看', 'API', 'crm', 61, 0),
       (8330, 'crm:report:sales', '销售漏斗报表', 'API', 'crm', 70, 0)
ON CONFLICT (code) DO NOTHING;

-- ---------- 字典类型（全局） ----------
INSERT INTO hub_dict_type (id, code, name, scope, status, deleted)
VALUES (8360, 'crm_lead_source', '线索来源', 'PLATFORM', 1, 0),
       (8361, 'crm_lead_status', '线索状态', 'PLATFORM', 1, 0),
       (8362, 'crm_lead_grade', '线索等级', 'PLATFORM', 1, 0),
       (8363, 'crm_opportunity_stage', '商机阶段', 'PLATFORM', 1, 0),
       (8364, 'crm_quotation_status', '报价状态', 'PLATFORM', 1, 0),
       (8365, 'crm_contract_status', '合同状态', 'PLATFORM', 1, 0),
       (8366, 'crm_payment_plan_status', '回款计划状态', 'PLATFORM', 1, 0),
       (8367, 'crm_collection_flag', '催收标记', 'PLATFORM', 1, 0)
ON CONFLICT (code) DO NOTHING;

-- ---------- 字典项（tenant_id=0 平台级，所有租户只读可见） ----------
INSERT INTO hub_dict_item (id, tenant_id, dict_code, item_value, item_label, sort, status, deleted)
VALUES (8401, 0, 'crm_lead_source', 'WEBSITE', '官网', 1, 1, 0),
       (8402, 0, 'crm_lead_source', 'OUTBOUND', '外呼', 2, 1, 0),
       (8403, 0, 'crm_lead_source', 'REFERRAL', '转介绍', 3, 1, 0),
       (8404, 0, 'crm_lead_source', 'EVENT', '市场活动', 4, 1, 0),
       (8405, 0, 'crm_lead_source', 'PARTNER', '渠道伙伴', 5, 1, 0),
       (8411, 0, 'crm_lead_status', 'NEW', '待分配', 1, 1, 0),
       (8412, 0, 'crm_lead_status', 'ASSIGNED', '已分配', 2, 1, 0),
       (8413, 0, 'crm_lead_status', 'FOLLOWING', '跟进中', 3, 1, 0),
       (8414, 0, 'crm_lead_status', 'CONVERTED', '已转客户', 4, 1, 0),
       (8415, 0, 'crm_lead_status', 'INVALID', '无效', 5, 1, 0),
       (8421, 0, 'crm_lead_grade', 'A', 'A 级', 1, 1, 0),
       (8422, 0, 'crm_lead_grade', 'B', 'B 级', 2, 1, 0),
       (8423, 0, 'crm_lead_grade', 'C', 'C 级', 3, 1, 0),
       (8431, 0, 'crm_opportunity_stage', 'S1', '初步接洽', 1, 1, 0),
       (8432, 0, 'crm_opportunity_stage', 'S2', '需求确认', 2, 1, 0),
       (8433, 0, 'crm_opportunity_stage', 'S3', '方案报价', 3, 1, 0),
       (8434, 0, 'crm_opportunity_stage', 'S4', '商务谈判', 4, 1, 0),
       (8435, 0, 'crm_opportunity_stage', 'WON', '赢单', 5, 1, 0),
       (8436, 0, 'crm_opportunity_stage', 'LOST', '输单', 6, 1, 0),
       (8441, 0, 'crm_quotation_status', 'DRAFT', '草稿', 1, 1, 0),
       (8442, 0, 'crm_quotation_status', 'PENDING', '审批中', 2, 1, 0),
       (8443, 0, 'crm_quotation_status', 'APPROVED', '已审批', 3, 1, 0),
       (8444, 0, 'crm_quotation_status', 'REJECTED', '已驳回', 4, 1, 0),
       (8445, 0, 'crm_quotation_status', 'CONVERTED', '已转合同', 5, 1, 0),
       (8451, 0, 'crm_contract_status', 'ACTIVE', '生效中', 1, 1, 0),
       (8452, 0, 'crm_contract_status', 'EXPIRED', '已到期', 2, 1, 0),
       (8453, 0, 'crm_contract_status', 'TERMINATED', '已终止', 3, 1, 0),
       (8461, 0, 'crm_payment_plan_status', 'PENDING', '待回款', 1, 1, 0),
       (8462, 0, 'crm_payment_plan_status', 'PARTIAL', '部分回款', 2, 1, 0),
       (8463, 0, 'crm_payment_plan_status', 'PAID', '已回款', 3, 1, 0),
       (8464, 0, 'crm_payment_plan_status', 'OVERDUE', '已逾期', 4, 1, 0),
       (8471, 0, 'crm_collection_flag', 'NONE', '未催收', 1, 1, 0),
       (8472, 0, 'crm_collection_flag', 'REMINDED', '已提醒', 2, 1, 0),
       (8473, 0, 'crm_collection_flag', 'PROMISED', '承诺回款', 3, 1, 0),
       (8474, 0, 'crm_collection_flag', 'ESCALATED', '已升级', 4, 1, 0)
ON CONFLICT (tenant_id, dict_code, item_value) DO NOTHING;
