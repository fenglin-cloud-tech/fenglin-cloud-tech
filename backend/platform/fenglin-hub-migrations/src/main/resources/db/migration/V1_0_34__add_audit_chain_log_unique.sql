-- B1：审计哈希链补日志唯一约束（同一日志仅一条链记录），原普通索引由唯一约束替代
SET search_path TO fenglin_hub, public;

DROP INDEX IF EXISTS idx_hub_audit_chain_log;
ALTER TABLE hub_audit_chain ADD CONSTRAINT uk_hub_audit_chain_log UNIQUE (tenant_id, log_id);

COMMENT ON CONSTRAINT uk_hub_audit_chain_log ON hub_audit_chain IS '审计日志与链记录一一对应';
