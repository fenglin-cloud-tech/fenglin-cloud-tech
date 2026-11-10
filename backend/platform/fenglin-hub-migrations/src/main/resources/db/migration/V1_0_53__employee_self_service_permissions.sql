-- PLAT-08 补充：员工自助查看请假与考勤的权限点
-- 说明：/leave/requests/my 与 /attendance/my 原先只接受管理侧 view 权限，
-- 而管理侧权限同时保护可按 employeeId 查询他人的接口。为保持 EMPLOYEE 角色
-- 只挂自助权限，这里新增独立的自助查看权限点，并授予存量租户的 EMPLOYEE 角色。

INSERT INTO fenglin_hub.hub_permission (id, code, name, type, domain, sort, deleted)
SELECT COALESCE(max(id), 0) + 1, 'talent:self:leave:view', '查看本人请假记录', 'API', 'talent', 11, 0
  FROM fenglin_hub.hub_permission
ON CONFLICT (code) DO NOTHING;

INSERT INTO fenglin_hub.hub_permission (id, code, name, type, domain, sort, deleted)
SELECT COALESCE(max(id), 0) + 1, 'talent:self:attendance:view', '查看本人考勤记录', 'API', 'talent', 12, 0
  FROM fenglin_hub.hub_permission
ON CONFLICT (code) DO NOTHING;

INSERT INTO fenglin_hub.hub_role_permission
    (id, tenant_id, role_id, permission_code, created_at, updated_at, deleted, version)
SELECT base.max_id + ROW_NUMBER() OVER (ORDER BY r.tenant_id, p.code) AS id,
       r.tenant_id,
       r.id AS role_id,
       p.code AS permission_code,
       now(),
       now(),
       0,
       0
  FROM fenglin_hub.hub_role r
  JOIN fenglin_hub.hub_permission p
    ON p.deleted = 0
   AND p.code IN ('talent:self:leave:view', 'talent:self:attendance:view')
 CROSS JOIN (SELECT COALESCE(max(id), 0) AS max_id FROM fenglin_hub.hub_role_permission) base
 WHERE r.code = 'EMPLOYEE'
   AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
