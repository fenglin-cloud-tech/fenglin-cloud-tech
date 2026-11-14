-- 云才员工自助档案权限（可重复执行）
-- talent:self:profile:view（id 10642）：只守护 GET /employees/my 与 GET /skills/employees/my，
-- 服务端按登录账号绑定的员工维度收敛，不接受调用方传 employeeId；不覆盖管理端 /employees/{id}。
-- 授予各租户 TENANT_ADMIN 与 EMPLOYEE 角色。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (10642, 'talent:self:profile:view', '我的档案查看', 'API', 'talent', 436, 0)
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
WHERE r.code IN ('TENANT_ADMIN', 'EMPLOYEE') AND r.deleted = 0
  AND p.code = 'talent:self:profile:view'
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
