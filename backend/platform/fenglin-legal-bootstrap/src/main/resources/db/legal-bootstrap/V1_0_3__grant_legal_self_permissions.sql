-- 云法员工自助权限授权（可重复执行）
-- 法务服务已在 ContractService / SealSignService / DlpService 内按 permissions 收敛 owner：
--   legal:contract:view:self  -> 我的合同（owner_id = 当前用户）
--   legal:sign:self:execute   -> 我的签署任务（party_type=INTERNAL AND party_id=当前用户）
--   legal:seal:apply          -> 用印申请（applicant_id = 当前用户）
--   legal:dlp:release:apply   -> 数据放行（user_id = 当前用户）
--   legal:evidence:view       -> 我的存证（本人合同下证据）
-- 本次只补授 EMPLOYEE / PLATFORM_ADMIN（TENANT_ADMIN 已有），不授予 evidence:verify。
SET search_path TO fenglin_hub, public;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (10661, 'legal:contract:view:self', '我的合同查看', 'API', 'legal', 10, 0),
       (10662, 'legal:sign:self:execute', '我的签署任务', 'API', 'legal', 20, 0),
       (10663, 'legal:seal:apply', '用印申请', 'API', 'legal', 30, 0),
       (10664, 'legal:dlp:release:apply', '数据放行申请', 'API', 'legal', 40, 0),
       (10665, 'legal:evidence:view', '我的存证查看', 'API', 'legal', 50, 0)
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
WHERE r.code IN ('TENANT_ADMIN', 'EMPLOYEE', 'PLATFORM_ADMIN') AND r.deleted = 0
  AND p.code IN ('legal:contract:view:self', 'legal:sign:self:execute', 'legal:seal:apply',
                 'legal:dlp:release:apply', 'legal:evidence:view')
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
