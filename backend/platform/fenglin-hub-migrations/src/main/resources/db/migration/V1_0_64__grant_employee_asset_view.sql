-- SVC-04 补充：把既有权限点 service:asset:view 授予员工角色
-- 背景：员工自助归还（POST /api/service/v1/assets/{id}/return-self）与门户“我的资产”
--       都以 service:asset:view 鉴权；此前该权限只授予 TENANT_ADMIN，员工调用返回 403。
-- 说明：权限点本身由云服 bootstrap（V1_0_1__seed_service_prerequisites.sql）定义，
--       这里只做幂等补植 + 授予存量租户的 EMPLOYEE 角色，重复执行不新增、不修改已有行。

INSERT INTO fenglin_hub.hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (9930, 'service:asset:view', '资产查看', 'API', 'service', 49, 0)
ON CONFLICT DO NOTHING;

INSERT INTO fenglin_hub.hub_role_permission
    (id, tenant_id, role_id, permission_code, created_at, updated_at, deleted, version)
SELECT base.max_id + ROW_NUMBER() OVER (ORDER BY r.tenant_id) AS id,
       r.tenant_id,
       r.id AS role_id,
       'service:asset:view' AS permission_code,
       now(),
       now(),
       0,
       0
  FROM fenglin_hub.hub_role r
  JOIN fenglin_hub.hub_permission p
    ON p.deleted = 0
   AND p.code = 'service:asset:view'
 CROSS JOIN (SELECT COALESCE(max(id), 0) AS max_id FROM fenglin_hub.hub_role_permission) base
 WHERE r.code = 'EMPLOYEE'
   AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
