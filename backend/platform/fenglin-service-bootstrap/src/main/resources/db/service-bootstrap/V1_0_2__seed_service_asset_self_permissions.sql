-- 云服员工自助资产权限（可重复执行）
--   service:asset:self:view    GET /assets/my、GET /assets/reminders/my
--                              服务端固定 owner_id = 当前登录用户，不接受调用方传 ownerId
--   service:asset:self:return  POST /assets/{id}/return-self
--                              服务端校验借用人为当前登录用户后才允许归还
-- 授予各租户 TENANT_ADMIN、EMPLOYEE 与 PLATFORM_ADMIN 角色。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (10649, 'service:asset:self:view', '我的资产查看', 'API', 'service', 80, 0),
       (10650, 'service:asset:self:return', '我的资产归还', 'API', 'service', 81, 0)
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
JOIN hub_permission p ON p.domain = 'service' AND p.deleted = 0
WHERE r.code IN ('TENANT_ADMIN', 'EMPLOYEE', 'PLATFORM_ADMIN') AND r.deleted = 0
  AND p.code IN ('service:asset:self:view', 'service:asset:self:return')
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
