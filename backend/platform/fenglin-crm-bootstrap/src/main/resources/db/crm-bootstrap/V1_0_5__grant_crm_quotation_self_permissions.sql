-- 员工报价线：创建与提交本人报价（不含审批）
--   复用既有权限点 crm:quotation:create（8322）、crm:quotation:submit（8324），
--   仅授予各租户 EMPLOYEE 角色；crm:quotation:approve（8325）不授。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8322, 'crm:quotation:create', '报价创建', 'API', 'crm', 60, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8324, 'crm:quotation:submit', '报价提交审批', 'API', 'crm', 62, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO hub_role_permission (id, tenant_id, role_id, permission_code, created_at, deleted, version)
SELECT (extract(epoch FROM clock_timestamp()) * 1000)::bigint * 1000 + row_number() OVER (),
       r.tenant_id,
       r.id,
       p.code,
       now(),
       0,
       0
FROM hub_role r
CROSS JOIN (VALUES ('crm:quotation:create'), ('crm:quotation:submit')) AS p(code)
WHERE r.code = 'EMPLOYEE' AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
