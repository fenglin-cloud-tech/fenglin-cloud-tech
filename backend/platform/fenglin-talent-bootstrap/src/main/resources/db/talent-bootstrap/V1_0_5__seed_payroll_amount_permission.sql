-- 云才薪酬高敏字段级权限（可重复执行）
-- 1) 新增权限点 talent:payroll:amount:view（id 8696，延续 V1_0_4 已占用的 8693~8695 之后）；
--    该权限与页面级 talent:admin:payroll:* 解耦：持有 list/manage/approve 仅能调用接口，
--    金额字段明文返回必须额外持有本权限，否则服务端统一置 null 并返回 amount_masked=true。
-- 2) 同步授予各租户 TENANT_ADMIN 角色（与既有薪酬管理权限保持一致）。
--    本仓库未预置独立 HR 角色；企业自定义 HR 角色需由租户管理员通过角色授权另行勾选本权限。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8696, 'talent:payroll:amount:view', '薪酬金额明文查看', 'API', 'talent', 423, 0)
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
  AND p.code = 'talent:payroll:amount:view'
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
