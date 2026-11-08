-- A2：refresh token 过期清理扫描索引（按租户 + 过期时间），配合 TokenJanitor 分批删除
SET search_path TO fenglin_hub, public;

CREATE INDEX idx_hub_auth_token_expire ON hub_auth_token (tenant_id, expires_at);

COMMENT ON INDEX idx_hub_auth_token_expire IS 'refresh token 过期清理扫描索引（P1 遗留 C-12 收口）';
