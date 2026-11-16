-- 修复 DLP 审计链唯一索引缺 tenant_id：多租户场景下各租户均从 seq_no=1 起链，
-- 原 uk_audit_chain (chain_id, seq_no) 会让第二个租户首条审计直接 23505 失败（接口 500）。
-- 表级影响：legal_dlp_audit_log 为 DLP 规则配置等低频事件表，重建索引仅短暂阻塞写入。
-- 回滚（仅单租户数据安全）：DROP INDEX fenglin_legal.uk_audit_chain;
--                       CREATE UNIQUE INDEX uk_audit_chain ON fenglin_legal.legal_dlp_audit_log (chain_id, seq_no);
SET search_path TO fenglin_legal, public;

DROP INDEX IF EXISTS uk_audit_chain;
CREATE UNIQUE INDEX uk_audit_chain ON legal_dlp_audit_log (tenant_id, chain_id, seq_no);
