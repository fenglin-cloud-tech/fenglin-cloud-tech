-- 对齐 crm_lead_source 字典与后端校验：以 01_丰麟云销_03 数据库设计 4.1（source 取值）
-- 及 LeadService.SOURCES 白名单为准：MANUAL/IMPORT/FORM/API/PARTNER/OPINION。
-- V1_0_1 中的 WEBSITE/OUTBOUND/REFERRAL/EVENT 不属于后端可接受值，先清理再补齐。
DELETE FROM hub_dict_item
WHERE tenant_id = 0
  AND dict_code = 'crm_lead_source'
  AND item_value IN ('WEBSITE', 'OUTBOUND', 'REFERRAL', 'EVENT');

INSERT INTO hub_dict_item (id, tenant_id, dict_code, item_value, item_label, sort, status, deleted)
VALUES (8401, 0, 'crm_lead_source', 'MANUAL', '手工录入', 1, 1, 0),
       (8402, 0, 'crm_lead_source', 'IMPORT', '批量导入', 2, 1, 0),
       (8403, 0, 'crm_lead_source', 'FORM', '表单收集', 3, 1, 0),
       (8404, 0, 'crm_lead_source', 'API', '接口接入', 4, 1, 0),
       (8405, 0, 'crm_lead_source', 'PARTNER', '渠道伙伴', 5, 1, 0),
       (8406, 0, 'crm_lead_source', 'OPINION', '舆情线索', 6, 1, 0)
ON CONFLICT (tenant_id, dict_code, item_value) DO NOTHING;
