-- 线索分配后需将状态推进为 ASSIGNED（crm_lead_status 字典已含该值，表约束遗漏）。
SET search_path TO fenglin_crm, public;

ALTER TABLE fenglin_crm.crm_lead DROP CONSTRAINT IF EXISTS ck_lead_status;
ALTER TABLE fenglin_crm.crm_lead ADD CONSTRAINT ck_lead_status
    CHECK (status IN ('NEW', 'ASSIGNED', 'FOLLOWING', 'CONVERTED', 'INVALID', 'NURTURING'));
