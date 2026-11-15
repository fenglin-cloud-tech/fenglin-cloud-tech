-- FIN-02 方案 C：MDM 往来单位主数据权限
-- 授权口径：PLATFORM_ADMIN 拥有 view/manage/merge；TENANT_ADMIN 拥有 view/manage（业务域界面可建、不可裁决合并）
-- 幂等：NOT EXISTS 判重；回滚：按 permission_code 删除授权行，再删除权限行。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, parent_code, sort, created_at, deleted, version)
SELECT 10901, 'platform:mdm:partner:view', '往来单位主数据查看', 'API', 'mdm', NULL, 1, now(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM hub_permission WHERE code = 'platform:mdm:partner:view');

INSERT INTO hub_permission (id, code, name, type, domain, parent_code, sort, created_at, deleted, version)
SELECT 10902, 'platform:mdm:partner:manage', '往来单位主数据维护', 'API', 'mdm', NULL, 2, now(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM hub_permission WHERE code = 'platform:mdm:partner:manage');

INSERT INTO hub_permission (id, code, name, type, domain, parent_code, sort, created_at, deleted, version)
SELECT 10903, 'platform:mdm:partner:merge', '往来单位合并/撤销裁决', 'API', 'mdm', NULL, 3, now(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM hub_permission WHERE code = 'platform:mdm:partner:merge');

INSERT INTO hub_role_permission
    (id, tenant_id, role_id, permission_code, created_at, deleted, version)
SELECT 1090100000000000000 + row_number() OVER (ORDER BY r.id), r.tenant_id, r.id,
       'platform:mdm:partner:view', now(), 0, 0
FROM hub_role r
WHERE r.code IN ('PLATFORM_ADMIN', 'TENANT_ADMIN') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM hub_role_permission rp
                  WHERE rp.tenant_id = r.tenant_id AND rp.role_id = r.id
                    AND rp.permission_code = 'platform:mdm:partner:view' AND rp.deleted = 0);

INSERT INTO hub_role_permission
    (id, tenant_id, role_id, permission_code, created_at, deleted, version)
SELECT 1090200000000000000 + row_number() OVER (ORDER BY r.id), r.tenant_id, r.id,
       'platform:mdm:partner:manage', now(), 0, 0
FROM hub_role r
WHERE r.code IN ('PLATFORM_ADMIN', 'TENANT_ADMIN') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM hub_role_permission rp
                  WHERE rp.tenant_id = r.tenant_id AND rp.role_id = r.id
                    AND rp.permission_code = 'platform:mdm:partner:manage' AND rp.deleted = 0);

INSERT INTO hub_role_permission
    (id, tenant_id, role_id, permission_code, created_at, deleted, version)
SELECT 1090300000000000000 + row_number() OVER (ORDER BY r.id), r.tenant_id, r.id,
       'platform:mdm:partner:merge', now(), 0, 0
FROM hub_role r
WHERE r.code = 'PLATFORM_ADMIN' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM hub_role_permission rp
                  WHERE rp.tenant_id = r.tenant_id AND rp.role_id = r.id
                    AND rp.permission_code = 'platform:mdm:partner:merge' AND rp.deleted = 0);
