-- ============================================================================
-- PLAT-20 修复迁移：job_complete_message_retry
-- 背景：V1_0_60 中 RETURNS TABLE 的输出参数 tenant_id 与 UPDATE 目标表的
--       tenant_id 同名，PL/pgSQL 解析为歧义：
--       ERROR: column reference "tenant_id" is ambiguous
-- 处理：仅新增前向迁移，CREATE OR REPLACE 函数体；不改已应用的 V1_0_60。
--   1) UPDATE 目标表加别名 m，所有列引用限定到 m
--   2) 增补“调用方租户上下文”一致性守卫（见下）
--      - 函数签名不接收 tenant_id，租户一律由 p_message_id 回查 hub_message 得到
--      - 若会话显式设置了 app.tenant_id（租户内请求上下文）且与消息所属租户不一致，
--        直接返回空集、零写入；作业连接（未设置 app.tenant_id）行为不变
-- 可重复执行：CREATE OR REPLACE + 幂等授权
-- ============================================================================

SET search_path TO fenglin_hub, public;

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
    v_tenant_id     bigint;
    v_channel       varchar(16);
    v_status        varchar(16);
    v_retry         integer;
    v_caller_tenant bigint;
    v_marked        boolean := false;
BEGIN
    v_caller_tenant := NULLIF(current_setting('app.tenant_id', true), '')::bigint;

    SELECT m.tenant_id, m.channel, m.status
      INTO v_tenant_id, v_channel, v_status
      FROM fenglin_hub.hub_message m
     WHERE m.id = p_message_id;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    -- 调用方租户上下文与消息所属租户不一致：零写入返回空集
    IF v_caller_tenant IS NOT NULL AND v_caller_tenant <> v_tenant_id THEN
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
        UPDATE fenglin_hub.hub_message m
           SET status = 'SENT', sent_at = now(), updated_at = now(), version = m.version + 1
         WHERE m.id = p_message_id
           AND m.tenant_id = v_tenant_id
           AND m.status = 'FAILED'
           AND m.channel = 'WS';
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
    IS 'PLAT-20：回执写入与 FAILED->SENT 状态回写同事务；tenant_id 由消息行内回查，不接受入参；会话租户上下文不符时零写入';

-- 迁移内自检：函数属性与授权不得被 CREATE OR REPLACE 带偏
DO $$
DECLARE
    v_bad integer;
BEGIN
    SELECT count(*)
      INTO v_bad
      FROM pg_proc p
      JOIN pg_namespace n ON n.oid = p.pronamespace
     WHERE n.nspname = 'fenglin_hub'
       AND p.proname = 'job_complete_message_retry'
       AND (NOT p.prosecdef
            OR p.proowner <> 'fl_job_owner'::regrole
            OR NOT ('search_path=pg_catalog, fenglin_hub' = ANY (p.proconfig)));

    IF v_bad <> 0 THEN
        RAISE EXCEPTION 'PLAT-20: job_complete_message_retry 属性不符合 SECURITY DEFINER / fl_job_owner / 固定 search_path';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM pg_proc p
          CROSS JOIN LATERAL aclexplode(COALESCE(p.proacl, acldefault('f', p.proowner))) a
         WHERE p.oid = 'fenglin_hub.job_complete_message_retry(bigint,bigint,varchar,varchar,boolean)'::regprocedure
           AND a.grantee = 0
           AND a.privilege_type = 'EXECUTE'
    ) THEN
        RAISE EXCEPTION 'PLAT-20: job_complete_message_retry 仍对 PUBLIC 开放 EXECUTE';
    END IF;
END
$$;

-- ============================================================================
-- 回退：不应回退到 V1_0_60 的歧义版本；如确需撤销，走新的前向迁移。
-- ============================================================================
