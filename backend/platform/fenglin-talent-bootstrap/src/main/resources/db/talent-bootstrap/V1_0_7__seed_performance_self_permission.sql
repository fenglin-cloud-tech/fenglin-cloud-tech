-- 云才员工自助绩效权限 + 入转调离列表查询权限（可重复执行）
-- 1) talent:self:performance:view（id 8698）：只允许访问 /performance/my*，服务端按账号绑定的员工维度收敛；
-- 2) talent:admin:onboarding:list / transfer:list / resignation:list（id 8699~8701）：
--    入转调离历史单据查询，供管理端列表回显；
-- 3) 同步授予各租户 TENANT_ADMIN 角色。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8698, 'talent:self:performance:view', '我的绩效查看', 'API', 'talent', 433, 0),
       (8699, 'talent:admin:onboarding:list', '入职记录查询', 'API', 'talent', 83, 0),
       (8700, 'talent:admin:transfer:list', '异动记录查询', 'API', 'talent', 92, 0),
       (8701, 'talent:admin:resignation:list', '离职记录查询', 'API', 'talent', 102, 0)
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
  AND p.code IN ('talent:self:performance:view', 'talent:admin:onboarding:list',
                 'talent:admin:transfer:list', 'talent:admin:resignation:list')
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
