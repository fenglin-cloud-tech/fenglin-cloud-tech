-- PLAT-08：账号与员工绑定，以及普通员工固定角色
-- 绑定关系落在 hub_user.employee_id，指向 fenglin_talent.talent_employee.id。
-- 两库物理隔离，不建外键，由 IAM 绑定时调用云才内部接口校验员工归属。

ALTER TABLE fenglin_hub.hub_user
    ADD COLUMN IF NOT EXISTS employee_id bigint;

COMMENT ON COLUMN fenglin_hub.hub_user.employee_id
    IS '绑定的员工ID（fenglin_talent.talent_employee.id），未绑定为 NULL';

-- 同一租户内一个员工只能绑定一个账号；软删账号不占用
CREATE UNIQUE INDEX IF NOT EXISTS uq_hub_user_employee_id
    ON fenglin_hub.hub_user (tenant_id, employee_id)
    WHERE deleted = 0 AND employee_id IS NOT NULL;

-- 确保自助权限点存在（正常由 talent-bootstrap V1_0_6 写入，这里兜底）
INSERT INTO fenglin_hub.hub_permission (id, code, name, type, domain, sort, deleted)
SELECT COALESCE(max(id), 0) + 1,
       'talent:self:payroll:view',
       '查看本人薪资单',
       'API',
       'talent',
       10,
       0
  FROM fenglin_hub.hub_permission
ON CONFLICT (code) DO NOTHING;

-- 存量租户补建 EMPLOYEE 固定角色：只挂自助类权限，不下放管理权限
INSERT INTO fenglin_hub.hub_role
    (id, tenant_id, code, name, role_type, data_scope, builtin, status, created_at, updated_at, deleted, version)
SELECT base.max_id + ROW_NUMBER() OVER (ORDER BY t.id) AS id,
       t.id AS tenant_id,
       'EMPLOYEE' AS code,
       '普通员工' AS name,
       'BIZ' AS role_type,
       'SELF' AS data_scope,
       1 AS builtin,
       'ENABLED' AS status,
       now(),
       now(),
       0,
       0
  FROM fenglin_hub.hub_tenant t
 CROSS JOIN (SELECT COALESCE(max(id), 0) AS max_id FROM fenglin_hub.hub_role) base
 WHERE t.deleted = 0
ON CONFLICT (tenant_id, code) DO NOTHING;

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
   AND p.code LIKE 'talent:self:%'
 CROSS JOIN (SELECT COALESCE(max(id), 0) AS max_id FROM fenglin_hub.hub_role_permission) base
 WHERE r.code = 'EMPLOYEE'
   AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
