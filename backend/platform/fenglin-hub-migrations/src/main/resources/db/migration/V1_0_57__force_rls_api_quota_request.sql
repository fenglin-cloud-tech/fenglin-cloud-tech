-- OPEN-03 修正：hub_api_quota_request 此前仅 ENABLE RLS 未 FORCE。
-- 服务以表属主 fenglin 连接数据库，ENABLE 状态下属主绕过 RLS，租户隔离形同虚设。
SET search_path TO fenglin_hub, public;

ALTER TABLE hub_api_quota_request FORCE ROW LEVEL SECURITY;
