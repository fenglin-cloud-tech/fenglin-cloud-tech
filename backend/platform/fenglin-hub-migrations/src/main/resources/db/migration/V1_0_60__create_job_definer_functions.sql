-- ============================================================================
-- PLAT-20 阶段一（2/2）：5 个 SECURITY DEFINER 窄函数
-- 目标位置：backend/platform/fenglin-hub-migrations/src/main/resources/db/migration/
--           V1_0_60__create_job_definer_functions.sql
-- 前置：V1_0_59 已应用（角色、列级授权、fl_job_owner RLS 放行策略）
-- 可重复执行：全部 CREATE OR REPLACE；属主 / REVOKE / GRANT 幂等
-- 安全约束：
--   1) 属主统一 fl_job_owner，固定 SET search_path = pg_catalog, fenglin_hub
--   2) 函数体无动态 SQL，入参不接收 SQL 片段 / 表名 / tenant_id
--   3) REVOKE ALL FROM PUBLIC 后仅授 EXECUTE 给 fl_app
--   4) 只返回调用方后续处理必需的最小列
-- 失败回退：PostgreSQL DDL 事务化，单迁移失败整体回滚；
--           已被 Flyway 记录后的回退见文件末尾“回滚”段。
-- ============================================================================

SET search_path TO fenglin_hub, public;

-- ----------------------------------------------------------------------------
-- 1) 审计导出：跨租户发现 PENDING 任务，只返回 id + tenant_id
--    替代 AuditExportJob.pendingTasks() 的裸连接查询
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fenglin_hub.job_list_pending_audit_exports(p_limit integer DEFAULT 10)
RETURNS TABLE (task_id bigint, tenant_id bigint)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, fenglin_hub
AS $$
    SELECT t.id, t.tenant_id
      FROM fenglin_hub.hub_audit_export_task t
     WHERE t.status = 'PENDING'
       AND t.deleted = 0
     ORDER BY t.created_at, t.id
     LIMIT LEAST(GREATEST(COALESCE(p_limit, 10), 1), 100)
$$;

ALTER FUNCTION fenglin_hub.job_list_pending_audit_exports(integer) OWNER TO fl_job_owner;
REVOKE ALL ON FUNCTION fenglin_hub.job_list_pending_audit_exports(integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fenglin_hub.job_list_pending_audit_exports(integer) TO fl_app;

COMMENT ON FUNCTION fenglin_hub.job_list_pending_audit_exports(integer)
    IS 'PLAT-20：跨租户发现待导出审计任务，仅返回 task_id/tenant_id；上限 100';

-- ----------------------------------------------------------------------------
-- 2) 审计链校验：只返回当月有链记录的租户 id
--    替代 AuditChainVerifyJob.tenantsWithChain() 的裸连接查询
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fenglin_hub.job_list_audit_chain_tenants(p_partition_key varchar)
RETURNS TABLE (tenant_id bigint)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, fenglin_hub
AS $$
    SELECT DISTINCT c.tenant_id
      FROM fenglin_hub.hub_audit_chain c
     WHERE c.partition_key = p_partition_key
       AND c.deleted = 0
       AND c.tenant_id > 0
     ORDER BY c.tenant_id
$$;

ALTER FUNCTION fenglin_hub.job_list_audit_chain_tenants(character varying) OWNER TO fl_job_owner;
REVOKE ALL ON FUNCTION fenglin_hub.job_list_audit_chain_tenants(character varying) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fenglin_hub.job_list_audit_chain_tenants(character varying) TO fl_app;

COMMENT ON FUNCTION fenglin_hub.job_list_audit_chain_tenants(character varying)
    IS 'PLAT-20：按月分区键返回待校验租户，仅返回 tenant_id 单列';

-- ----------------------------------------------------------------------------
-- 3) 搜索索引任务：跨租户发现 PENDING 任务，只返回 id + tenant_id
--    替代 SearchIndexTaskJob.pendingTasks() 的裸连接查询
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fenglin_hub.job_list_pending_search_index_tasks(p_limit integer DEFAULT 10)
RETURNS TABLE (task_id bigint, tenant_id bigint)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, fenglin_hub
AS $$
    SELECT t.id, t.tenant_id
      FROM fenglin_hub.hub_search_index_task t
     WHERE t.status = 'PENDING'
       AND t.deleted = 0
     ORDER BY t.created_at, t.id
     LIMIT LEAST(GREATEST(COALESCE(p_limit, 10), 1), 100)
$$;

ALTER FUNCTION fenglin_hub.job_list_pending_search_index_tasks(integer) OWNER TO fl_job_owner;
REVOKE ALL ON FUNCTION fenglin_hub.job_list_pending_search_index_tasks(integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fenglin_hub.job_list_pending_search_index_tasks(integer) TO fl_app;

COMMENT ON FUNCTION fenglin_hub.job_list_pending_search_index_tasks(integer)
    IS 'PLAT-20：跨租户发现待执行索引任务，仅返回 task_id/tenant_id；上限 100';

-- ----------------------------------------------------------------------------
-- 4) 消息重试：候选发现（只读）
--    替代 MessageRetryJob.retry() 中的跨租户 SELECT
--    限定：固定 FAILED + WS + 近 1 天 + 回执数小于阈值 + 上限 100
--    返回列即 dispatch 所需列，不返回整行
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fenglin_hub.job_claim_failed_ws_messages(
    p_max_retry integer,
    p_limit     integer DEFAULT 100)
RETURNS TABLE (
    message_id  bigint,
    tenant_id   bigint,
    receiver_id bigint,
    channel     varchar(16),
    title       varchar(256),
    content     text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, fenglin_hub
AS $$
    SELECT m.id, m.tenant_id, m.receiver_id, m.channel, m.title, m.content
      FROM fenglin_hub.hub_message m
     WHERE m.status = 'FAILED'
       AND m.channel = 'WS'
       AND m.created_at > now() - interval '1 day'
       AND (SELECT count(*)
              FROM fenglin_hub.hub_message_receipt r
             WHERE r.message_id = m.id
               AND r.tenant_id = m.tenant_id) < p_max_retry
     ORDER BY m.created_at, m.id
     LIMIT LEAST(GREATEST(COALESCE(p_limit, 100), 1), 100)
$$;

ALTER FUNCTION fenglin_hub.job_claim_failed_ws_messages(integer, integer) OWNER TO fl_job_owner;
REVOKE ALL ON FUNCTION fenglin_hub.job_claim_failed_ws_messages(integer, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fenglin_hub.job_claim_failed_ws_messages(integer, integer) TO fl_app;

COMMENT ON FUNCTION fenglin_hub.job_claim_failed_ws_messages(integer, integer)
    IS 'PLAT-20：跨租户领取失败 WS 消息候选，仅返回发送与状态回写所需列；上限 100';

-- ----------------------------------------------------------------------------
-- 5) 消息重试：回执写入 + 状态回写（单事务）
--    替代 attempts() + insertReceipt() + UPDATE ... WHERE id=? 三条裸连接语句
--    限定：
--      - tenant_id 不接收入参，必须由 p_message_id 回查 hub_message 得到
--      - 回执 tenant_id 使用回查值
--      - UPDATE WHERE 同时带 id / tenant_id / status='FAILED' / channel='WS'
--      - 消息不存在或已不是 FAILED/WS：零写入，返回空集
--      - 仅允许 FAILED -> SENT 单向流转
--      - retry_count 由函数内真实回执数 + 1 计算
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fenglin_hub.job_complete_message_retry(
    p_message_id     bigint,
    p_receipt_id     bigint,
    p_receipt_status varchar,
    p_error          varchar,
    p_mark_sent      boolean)
RETURNS TABLE (tenant_id bigint, retry_count integer, marked_sent boolean)
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, fenglin_hub
AS $$
DECLARE
    v_tenant_id bigint;
    v_channel   varchar(16);
    v_status    varchar(16);
    v_retry     integer;
    v_marked    boolean := false;
BEGIN
    SELECT m.tenant_id, m.channel, m.status
      INTO v_tenant_id, v_channel, v_status
      FROM fenglin_hub.hub_message m
     WHERE m.id = p_message_id;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    IF v_status <> 'FAILED' OR v_channel <> 'WS' THEN
        RETURN;
    END IF;

    SELECT count(*) INTO v_retry
      FROM fenglin_hub.hub_message_receipt r
     WHERE r.message_id = p_message_id
       AND r.tenant_id = v_tenant_id;

    INSERT INTO fenglin_hub.hub_message_receipt
        (id, tenant_id, message_id, status, retry_count, error_msg,
         created_at, updated_at, deleted, version)
    VALUES
        (p_receipt_id, v_tenant_id, p_message_id, p_receipt_status,
         v_retry + 1, p_error, now(), now(), 0, 0);

    IF p_mark_sent THEN
        UPDATE fenglin_hub.hub_message
           SET status = 'SENT', sent_at = now(), updated_at = now(), version = version + 1
         WHERE id = p_message_id
           AND tenant_id = v_tenant_id
           AND status = 'FAILED'
           AND channel = 'WS';
        v_marked := FOUND;
    END IF;

    RETURN QUERY SELECT v_tenant_id, v_retry + 1, v_marked;
END
$$;

ALTER FUNCTION fenglin_hub.job_complete_message_retry(bigint, bigint, varchar, varchar, boolean)
    OWNER TO fl_job_owner;
REVOKE ALL ON FUNCTION fenglin_hub.job_complete_message_retry(bigint, bigint, varchar, varchar, boolean)
    FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fenglin_hub.job_complete_message_retry(bigint, bigint, varchar, varchar, boolean)
    TO fl_app;

COMMENT ON FUNCTION fenglin_hub.job_complete_message_retry(bigint, bigint, varchar, varchar, boolean)
    IS 'PLAT-20：回执写入与 FAILED->SENT 状态回写同事务；tenant_id 由消息行内回查，不接受入参';

-- ----------------------------------------------------------------------------
-- 6) 迁移内自检：5 个函数必须 SECURITY DEFINER 且属主为 fl_job_owner
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_total integer;
    v_bad   integer;
BEGIN
    SELECT count(*)
      INTO v_total
      FROM pg_proc p
      JOIN pg_namespace n ON n.oid = p.pronamespace
     WHERE n.nspname = 'fenglin_hub'
       AND p.proname IN (
           'job_list_pending_audit_exports',
           'job_list_audit_chain_tenants',
           'job_list_pending_search_index_tasks',
           'job_claim_failed_ws_messages',
           'job_complete_message_retry');

    SELECT count(*)
      INTO v_bad
      FROM pg_proc p
      JOIN pg_namespace n ON n.oid = p.pronamespace
     WHERE n.nspname = 'fenglin_hub'
       AND p.proname IN (
           'job_list_pending_audit_exports',
           'job_list_audit_chain_tenants',
           'job_list_pending_search_index_tasks',
           'job_claim_failed_ws_messages',
           'job_complete_message_retry')
       AND (NOT p.prosecdef OR p.proowner <> 'fl_job_owner'::regrole);

    IF v_total <> 5 THEN
        RAISE EXCEPTION 'PLAT-20: 期望 5 个作业函数，实际找到 %', v_total;
    END IF;

    IF v_bad > 0 THEN
        RAISE EXCEPTION 'PLAT-20: 存在未按 SECURITY DEFINER / fl_job_owner 落地的函数，数量=%', v_bad;
    END IF;
END
$$;

-- ============================================================================
-- 回滚（手工；函数无数据，DROP 即可）
--   DROP FUNCTION IF EXISTS fenglin_hub.job_list_pending_audit_exports(integer);
--   DROP FUNCTION IF EXISTS fenglin_hub.job_list_audit_chain_tenants(character varying);
--   DROP FUNCTION IF EXISTS fenglin_hub.job_list_pending_search_index_tasks(integer);
--   DROP FUNCTION IF EXISTS fenglin_hub.job_claim_failed_ws_messages(integer, integer);
--   DROP FUNCTION IF EXISTS fenglin_hub.job_complete_message_retry(bigint, bigint, varchar, varchar, boolean);
--
-- 若签名需要变更（参数名/类型/返回列变化）：
--   必须先 DROP 旧签名再 CREATE，不能只靠 CREATE OR REPLACE（会变成重载而不是替换）。
-- 若 60 已被 Flyway 记录：
--   先手工执行上述回滚，再新增前向迁移修正；不要修改已应用迁移的 checksum，
--   也不要用 flyway repair 掩盖问题。
-- ============================================================================
