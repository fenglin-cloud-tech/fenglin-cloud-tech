-- LEG-03：相对方手机号明文查看申请/审批权限（可重复执行）
-- 1) legal:party:pii:apply（10640）：发起明文查看申请、查看本人申请记录；
-- 2) legal:party:pii:approve（10641）：审批他人的明文查看申请；
-- 3) 明文查看仍要求 legal:party:pii:view（10025）且存在本人已批准的申请；
-- 4) 授予各租户 TENANT_ADMIN。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (10640, 'legal:party:pii:apply', '相对方 PII 明文查看申请', 'API', 'legal', 85, 0),
       (10641, 'legal:party:pii:approve', '相对方 PII 明文查看审批', 'API', 'legal', 86, 0)
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
JOIN hub_permission p ON p.domain = 'legal' AND p.deleted = 0
WHERE r.code = 'TENANT_ADMIN' AND r.deleted = 0
  AND p.code IN ('legal:party:pii:apply', 'legal:party:pii:approve')
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
