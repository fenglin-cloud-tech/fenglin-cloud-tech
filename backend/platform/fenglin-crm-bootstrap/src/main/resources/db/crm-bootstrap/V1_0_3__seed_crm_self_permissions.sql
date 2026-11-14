-- 云销员工自助权限（可重复执行）
--   crm:lead:self:view         GET /leads/my、/leads/my/{id}                    仅 owner_id = 当前用户
--   crm:customer:self:view     GET /customers/my、/customers/my/{id}            仅 owner_id = 当前用户
--   crm:opportunity:self:view  GET /opportunities/my、/my/{id}、/my/{id}/stage-logs
--   crm:quotation:self:view    GET /quotations/my、/quotations/my/{id}          仅 owner_id = 当前用户
--   crm:contract:self:view     GET /contracts/my、/my/{id}、/my/{id}/changes    仅 owner_id = 当前用户
--   crm:payment:self:view      GET /payment-plans/reminders/my                  仅本人负责合同的计划
-- 授予各租户 TENANT_ADMIN、EMPLOYEE 与 PLATFORM_ADMIN 角色。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (10655, 'crm:lead:self:view', '我的线索查看', 'API', 'crm', 10, 0),
       (10656, 'crm:customer:self:view', '我的客户查看', 'API', 'crm', 20, 0),
       (10657, 'crm:opportunity:self:view', '我的商机查看', 'API', 'crm', 30, 0),
       (10658, 'crm:quotation:self:view', '我的报价查看', 'API', 'crm', 40, 0),
       (10659, 'crm:contract:self:view', '我的合同查看', 'API', 'crm', 50, 0),
       (10660, 'crm:payment:self:view', '我的回款提醒查看', 'API', 'crm', 60, 0)
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
JOIN hub_permission p ON p.domain = 'crm' AND p.deleted = 0
WHERE r.code IN ('TENANT_ADMIN', 'EMPLOYEE', 'PLATFORM_ADMIN') AND r.deleted = 0
  AND p.code IN ('crm:lead:self:view', 'crm:customer:self:view', 'crm:opportunity:self:view',
                 'crm:quotation:self:view', 'crm:contract:self:view', 'crm:payment:self:view')
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
