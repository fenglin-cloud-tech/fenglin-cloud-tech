-- COL-07 员工自助：创建自己的文档分享链接
--   biz:collab:doc:self:share  POST /docs/{docId}/share-link（仅 owner 本人；链接仍限同租户且带有效期）
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (10663, 'biz:collab:doc:self:share', '我的文档分享链接', 'API', 'collab', 34, 0)
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
JOIN hub_permission p ON p.deleted = 0
WHERE r.code = 'EMPLOYEE' AND r.deleted = 0
  AND p.code = 'biz:collab:doc:self:share'
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
