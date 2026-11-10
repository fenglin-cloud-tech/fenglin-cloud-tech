-- 幂等授权：把 8 个业务域（云客 crm / 云协 collab / 云才 talent / 云财 finance /
-- 云效 devops / 云服 service / 云法 legal / 云智 ai）的权限点授予所有租户的 TENANT_ADMIN 角色。
--
-- 背景：各域 bootstrap 先于历史数据回灌执行，回灌会整体覆盖 hub_role_permission，
--       bootstrap 新授予的权限在回灌后消失，只能手工重跑。重建/回灌完成后执行本脚本补齐。
-- 幂等：按 (tenant_id, role_id, permission_code) 唯一键去重，重复执行不新增、不修改已有行；
--       平台级权限（platform:*）属于平台运营角色，不授予租户管理员。
BEGIN;

LOCK TABLE fenglin_hub.hub_role_permission IN SHARE ROW EXCLUSIVE MODE;

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
 AND p.domain IN ('crm', 'collab', 'talent', 'finance', 'devops', 'service', 'legal', 'ai')
 AND p.code NOT LIKE 'platform:%'
CROSS JOIN (SELECT COALESCE(max(id), 0) AS max_id FROM fenglin_hub.hub_role_permission) base
WHERE r.code = 'TENANT_ADMIN'
  AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;

COMMIT;
