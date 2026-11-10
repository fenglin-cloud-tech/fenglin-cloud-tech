-- 收敛平台管理员角色（PLATFORM_ADMIN）的授权口径：只保留"代码中真实校验"的平台权限点。
--
-- 背景：V1_0_48 把当时库中全部 platform:* 一次性授予该角色，但其中两条并无真实能力：
--   1) platform:billing:manage           —— 全仓无任何 Controller/Service 校验，计费管理能力未实现；
--   2) platform:workflow:engine:manage   —— 工作流引擎管理实际校验的是 tenant:workflow:*，
--                                           该平台权限点已属旧命名/废弃。
-- 保留的 9 条均有真实校验点：
--   platform:openapi:app:review / key:manage / quota:manage / calllog:view -> OpenApiAdminController
--   platform:tenant:manage                                               -> TenantController
--   platform:audit:export / audit:alert:manage                           -> AuditController
--   platform:ai:model:manage                                             -> AiAdminController
--   platform:search:index:manage                                         -> SearchAdminController
--
-- 幂等：重复执行时多余授权已为 deleted=1（UPDATE 0 行），保留项已存在（INSERT 0 行）。

-- 1. 回收多余授权（只动 PLATFORM_ADMIN 角色，软删，不物理删除数据）
UPDATE fenglin_hub.hub_role_permission rp
   SET deleted = 1,
       updated_at = now()
 WHERE rp.deleted = 0
   AND rp.permission_code NOT IN (
       'platform:ai:model:manage',
       'platform:audit:alert:manage',
       'platform:audit:export',
       'platform:openapi:app:review',
       'platform:openapi:calllog:view',
       'platform:openapi:key:manage',
       'platform:openapi:quota:manage',
       'platform:search:index:manage',
       'platform:tenant:manage'
   )
   AND rp.role_id IN (
       SELECT r.id FROM fenglin_hub.hub_role r
        WHERE r.code = 'PLATFORM_ADMIN' AND r.deleted = 0
   );

-- 2. 补齐保留项（覆盖"角色已建但授权不全"的历史租户）
INSERT INTO fenglin_hub.hub_role_permission
    (id, tenant_id, role_id, permission_code, created_at, updated_at, deleted)
SELECT base.max_id + ROW_NUMBER() OVER (ORDER BY r.tenant_id, p.code) AS id,
       r.tenant_id,
       r.id AS role_id,
       p.code AS permission_code,
       now(),
       now(),
       0
FROM fenglin_hub.hub_role r
JOIN fenglin_hub.hub_permission p
  ON p.deleted = 0
 AND p.code IN (
       'platform:ai:model:manage',
       'platform:audit:alert:manage',
       'platform:audit:export',
       'platform:openapi:app:review',
       'platform:openapi:calllog:view',
       'platform:openapi:key:manage',
       'platform:openapi:quota:manage',
       'platform:search:index:manage',
       'platform:tenant:manage'
   )
CROSS JOIN (SELECT COALESCE(max(id), 0) AS max_id FROM fenglin_hub.hub_role_permission) base
WHERE r.code = 'PLATFORM_ADMIN'
  AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
