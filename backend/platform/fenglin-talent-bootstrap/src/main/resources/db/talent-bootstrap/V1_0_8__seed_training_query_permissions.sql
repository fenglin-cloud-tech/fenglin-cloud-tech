-- 云才培训：班次查询 + 我的报名/学习记录查询权限（可重复执行）
-- 1) talent:course:session:list（id 10638）：员工报名前按课程选择班次；
-- 2) talent:self:training:view（id 10639）：本人报名/学习记录，服务端按账号绑定的员工维度收敛；
-- 3) 授予各租户 TENANT_ADMIN 与 EMPLOYEE 角色。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (10638, 'talent:course:session:list', '培训班级查询', 'API', 'talent', 434, 0),
       (10639, 'talent:self:training:view', '我的培训记录查看', 'API', 'talent', 435, 0)
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
  AND p.code IN ('talent:course:session:list', 'talent:self:training:view')
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
