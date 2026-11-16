-- LEG-02 方案 B：签署人 PII 明文查看高敏权限（纯数据迁移，无 DDL）
-- 1) 新增 legal:signer:pii:view（10667），仅用于 POST /contract-signatories/{id}/pii/reveal；
-- 2) 接口必填 reason，解密后写 HIGH 级审计 signer.pii.view（操作人/签署人 id/理由/时间/来源 IP/租户）；
-- 3) 不做二次审批、不建请求表；初始只授 TENANT_ADMIN（当前无独立法务负责人角色）；
-- 4) 说明：V1_0_1 中的 legal:sign:pii:view（10034）历史上从未被接口或角色使用，保留不动，
--    不参与本接口鉴权，避免新旧两套码混用。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (10667, 'legal:signer:pii:view', '签署人 PII 明文查看（高敏）', 'API', 'legal', 87, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO hub_role_permission (id, tenant_id, role_id, permission_code, created_at, deleted, version)
SELECT (extract(epoch FROM clock_timestamp()) * 1000)::bigint * 1000 + row_number() OVER (),
       r.tenant_id,
       r.id,
       'legal:signer:pii:view',
       now(),
       0,
       0
FROM hub_role r
WHERE r.code = 'TENANT_ADMIN' AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
