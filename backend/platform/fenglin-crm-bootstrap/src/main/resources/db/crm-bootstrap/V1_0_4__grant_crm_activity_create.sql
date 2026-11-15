-- D5：员工在门户新增跟进记录（可重复执行）
--   复用既有权限点 crm:activity:create（8328，跟进记录创建），不新增权限码，
--   仅授予各租户 EMPLOYEE 角色；TENANT_ADMIN/PLATFORM_ADMIN 维持现状。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8328, 'crm:activity:create', '跟进记录创建', 'API', 'crm', 70, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO hub_role_permission (id, tenant_id, role_id, permission_code, created_at, deleted, version)
SELECT (extract(epoch FROM clock_timestamp()) * 1000)::bigint * 1000 + row_number() OVER (),
       r.tenant_id,
       r.id,
       'crm:activity:create',
       now(),
       0,
       0
FROM hub_role r
WHERE r.code = 'EMPLOYEE' AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
