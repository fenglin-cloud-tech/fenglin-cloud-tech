-- 云财员工工时：授予 EMPLOYEE 项目查看权限（可重复执行）
-- biz:finance:project:view 为 FinanceProjectController 实际校验码，
-- 门户「我的工时」项目下拉依赖该码；此前仅 TENANT_ADMIN 持有。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_role_permission (id, tenant_id, role_id, permission_code, created_at, deleted, version)
SELECT (extract(epoch FROM clock_timestamp()) * 1000)::bigint * 1000 + row_number() OVER (),
       r.tenant_id,
       r.id,
       'biz:finance:project:view',
       now(),
       0,
       0
FROM hub_role r
JOIN hub_permission p ON p.code = 'biz:finance:project:view' AND p.deleted = 0
WHERE r.code = 'EMPLOYEE' AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
