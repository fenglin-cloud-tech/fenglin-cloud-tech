-- 云才员工自助薪资单权限（可重复执行）
-- 1) 新增权限点 talent:self:payroll:view（id 8697，延续 V1_0_5 已占用的 8696 之后）；
--    该权限只允许访问 /payroll/slips/my*，服务端按登录用户收敛员工维度，
--    与管理端 talent:admin:payroll:* 解耦；金额是否明文仍由 talent:payroll:amount:view 单独控制。
-- 2) 同步授予各租户 TENANT_ADMIN 角色（与既有薪酬权限保持一致）；
--    普通员工角色需由租户管理员在角色授权中勾选本权限。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8697, 'talent:self:payroll:view', '我的薪资单查看', 'API', 'talent', 424, 0)
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
JOIN hub_permission p ON p.domain = 'talent' AND p.deleted = 0
WHERE r.code = 'TENANT_ADMIN' AND r.deleted = 0
  AND p.code = 'talent:self:payroll:view'
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
