-- PLAT-10：权限版本号（pv），用于 JWT 瘦身后的网关权限缓存失效
-- 说明：pv 随用户角色/角色权限/账号状态变更递增；JWT 只携带 pv，不再携带全量权限点。
-- 旧令牌（带 permissions、无 pv）在兼容期内仍由网关直接读取 claim，无需数据迁移。

ALTER TABLE fenglin_hub.hub_user
    ADD COLUMN IF NOT EXISTS permission_version bigint NOT NULL DEFAULT 0;

COMMENT ON COLUMN fenglin_hub.hub_user.permission_version
    IS '权限版本号：角色/权限/状态变更时递增，网关按 (tenant,user,pv) 缓存权限点';
