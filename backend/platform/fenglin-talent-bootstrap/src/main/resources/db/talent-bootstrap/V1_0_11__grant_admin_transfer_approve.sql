-- 云才异动审批权限：补齐 talent:admin:transfer:approve（可重复执行）
-- 1) 该码此前只存在于控制器常量（TalentLifecycleController.TRANSFER_APPROVE），
--    权限目录未定义，导致管理员点「审批通过」403；
-- 2) 授予各租户 TENANT_ADMIN，与 transfer:create / transfer:effective 配套。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (10703, 'talent:admin:transfer:approve', '异动审批', 'API', 'talent', 93, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO hub_role_permission (id, tenant_id, role_id, permission_code, created_at, deleted, version)
SELECT (extract(epoch FROM clock_timestamp()) * 1000)::bigint * 1000 + row_number() OVER (),
       r.tenant_id,
       r.id,
       'talent:admin:transfer:approve',
       now(),
       0,
       0
FROM hub_role r
WHERE r.code = 'TENANT_ADMIN' AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
