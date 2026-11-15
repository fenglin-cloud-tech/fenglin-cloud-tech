-- 云法 DLP 放行：拆分只读权限 legal:dlp:release:view（可重复执行）
-- 背景：GET /api/legal/v1/dlp/releases、/dlp/releases/{id}、/dlp/releases/{id}/progress
--       原先靠 legal:dlp:release:approve 或 legal:dlp:release:apply 兜底，
--       读接口被写权限码绑定：想给员工看本人放行记录就得同时给出申请/审批能力。
-- 本次：新增只读码 legal:dlp:release:view；控制器读路径只认 view（approve 仍可看全部）。
--       apply 只用于 POST /dlp/releases 提交申请，不再兼作查看。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (10666, 'legal:dlp:release:view', '数据放行查看', 'API', 'legal', 42, 0)
ON CONFLICT (code) DO NOTHING;

-- 授 EMPLOYEE / TENANT_ADMIN / PLATFORM_ADMIN（与 V1_0_3 自助授权口径一致）；
-- 另外补齐任何已持有放行审批码的角色，保证改造后审批人仍能查看列表。
INSERT INTO hub_role_permission (id, tenant_id, role_id, permission_code, created_at, deleted, version)
SELECT (extract(epoch FROM clock_timestamp()) * 1000)::bigint * 1000 + row_number() OVER (),
       r.tenant_id,
       r.id,
       'legal:dlp:release:view',
       now(),
       0,
       0
FROM hub_role r
WHERE r.deleted = 0
  AND (r.code IN ('TENANT_ADMIN', 'EMPLOYEE', 'PLATFORM_ADMIN')
       OR EXISTS (SELECT 1 FROM hub_role_permission rp
                  WHERE rp.tenant_id = r.tenant_id AND rp.role_id = r.id
                    AND rp.permission_code = 'legal:dlp:release:approve' AND rp.deleted = 0))
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
