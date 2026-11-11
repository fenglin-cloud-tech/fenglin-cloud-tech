-- ============================================================================
-- PLAT-20 阶段一（1/2）：作业属主最小权限 + RLS 放行
-- 目标位置：backend/platform/fenglin-hub-migrations/src/main/resources/db/migration/
--           V1_0_59__create_job_owner_grants_and_policies.sql
-- 版本依据：仓库 hub 迁移当前最大为 V1_0_58__grant_app_privileges.sql
--           执行前必须再查 flyway_schema_history 的实际最大值；若已 >= 59，本文件整体顺延
-- 可重复执行：角色存在性判重、GRANT/REVOKE 幂等、策略 DROP IF EXISTS 后重建
-- 事务性：PostgreSQL DDL 事务化，Flyway 单迁移失败会整体回滚
--
-- 重要口径（与上一版设计清单不同）：
--   fl_job_owner 按要求使用 NOBYPASSRLS。因此函数跨租户读取不能依赖 BYPASSRLS，
--   必须为本迁移第 4 节的 5 张表补 fl_job_owner 专用放行策略，否则函数恒定返回 0 行。
--   策略按函数实际需要拆分为 SELECT / INSERT / UPDATE（共 7 条），不做 FOR ALL。
--   该策略只作用于 fl_job_owner，不扩大 fl_app / fl_platform_role 的可见范围。
-- ============================================================================

SET search_path TO fenglin_hub, public;

-- ----------------------------------------------------------------------------
-- 1) NOLOGIN 属主角色幂等守卫（登录角色 fl_login_ddl 不在此创建，见 db/roles/003）
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fl_job_owner') THEN
        CREATE ROLE fl_job_owner NOLOGIN NOSUPERUSER NOBYPASSRLS
            NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fl_ddl_owner') THEN
        CREATE ROLE fl_ddl_owner NOLOGIN NOSUPERUSER NOBYPASSRLS
            NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
END
$$;

-- 2) 已有角色属性纠偏（幂等）
ALTER ROLE fl_job_owner WITH NOLOGIN NOSUPERUSER NOBYPASSRLS
    NOCREATEDB NOCREATEROLE NOREPLICATION;
ALTER ROLE fl_ddl_owner WITH NOLOGIN NOSUPERUSER NOBYPASSRLS
    NOCREATEDB NOCREATEROLE NOREPLICATION;

-- 3) schema 权限
--    fl_job_owner：只需 USAGE（函数体访问对象）
--    fl_ddl_owner：USAGE + CREATE（后续创建月分区；本轮不迁移表属主）
--    fl_app：显式回收 CREATE，作为“应用永不建表”的回归防线
GRANT USAGE ON SCHEMA fenglin_hub TO fl_job_owner, fl_ddl_owner;
GRANT CREATE ON SCHEMA fenglin_hub TO fl_ddl_owner;
REVOKE CREATE ON SCHEMA fenglin_hub FROM fl_app;

-- ----------------------------------------------------------------------------
-- 4) fl_job_owner 列级授权（只给函数体内实际读写的列）
-- ----------------------------------------------------------------------------
-- 4.1 审计导出任务：只读 id / tenant_id / status / deleted / created_at
GRANT SELECT (id, tenant_id, status, deleted, created_at)
    ON fenglin_hub.hub_audit_export_task TO fl_job_owner;

-- 4.2 审计链：只读 tenant_id / partition_key / deleted
GRANT SELECT (tenant_id, partition_key, deleted)
    ON fenglin_hub.hub_audit_chain TO fl_job_owner;

-- 4.3 搜索索引任务：只读 id / tenant_id / status / deleted / created_at
GRANT SELECT (id, tenant_id, status, deleted, created_at)
    ON fenglin_hub.hub_search_index_task TO fl_job_owner;

-- 4.4 消息：候选发现所需列 + 状态回写所需列
--     注意 version 必须可读（UPDATE 中 version = version + 1）
GRANT SELECT (id, tenant_id, receiver_id, channel, title, content, status, created_at, version)
    ON fenglin_hub.hub_message TO fl_job_owner;
GRANT UPDATE (status, sent_at, updated_at, version)
    ON fenglin_hub.hub_message TO fl_job_owner;

-- 4.5 消息回执：计数读 + 插入
GRANT SELECT (id, message_id, tenant_id)
    ON fenglin_hub.hub_message_receipt TO fl_job_owner;
GRANT INSERT (id, tenant_id, message_id, status, retry_count, error_msg,
              created_at, updated_at, deleted, version)
    ON fenglin_hub.hub_message_receipt TO fl_job_owner;

-- ----------------------------------------------------------------------------
-- 5) fl_job_owner 的 RLS 放行策略（NOBYPASSRLS 前提）
--    只放行函数实际用到的 5 张表，共 7 条；命令类型与函数语句一一对应：
--
--    表                          命令    服务的函数 / 覆盖语句
--    hub_audit_export_task       SELECT  job_list_pending_audit_exports：PENDING 任务发现
--    hub_audit_chain             SELECT  job_list_audit_chain_tenants：按月租户列表
--    hub_search_index_task       SELECT  job_list_pending_search_index_tasks：PENDING 任务发现
--    hub_message                 SELECT  job_claim_failed_ws_messages：候选查询
--                                        job_complete_message_retry：按 id 回查 tenant/channel/status
--    hub_message                 UPDATE  job_complete_message_retry：FAILED -> SENT 状态回写
--    hub_message_receipt         SELECT  job_claim_failed_ws_messages：回执计数子查询
--                                        job_complete_message_retry：回执计数
--    hub_message_receipt         INSERT  job_complete_message_retry：回执写入
--
--    不放行 DELETE；不放行 FOR ALL；实际可执行动作仍受第 4 节列级权限约束。
-- ----------------------------------------------------------------------------

-- 5.0 清理旧草稿的 FOR ALL 策略名（若曾被手工执行过；本迁移未执行时为空操作）
DROP POLICY IF EXISTS p_hub_audit_export_task_job_owner ON fenglin_hub.hub_audit_export_task;
DROP POLICY IF EXISTS p_hub_audit_chain_job_owner        ON fenglin_hub.hub_audit_chain;
DROP POLICY IF EXISTS p_hub_search_index_task_job_owner  ON fenglin_hub.hub_search_index_task;
DROP POLICY IF EXISTS p_hub_message_job_owner            ON fenglin_hub.hub_message;
DROP POLICY IF EXISTS p_hub_message_receipt_job_owner    ON fenglin_hub.hub_message_receipt;

-- 5.1 审计导出任务：仅 SELECT（job_list_pending_audit_exports）
DROP POLICY IF EXISTS p_hub_audit_export_task_job_owner_select ON fenglin_hub.hub_audit_export_task;
CREATE POLICY p_hub_audit_export_task_job_owner_select ON fenglin_hub.hub_audit_export_task
    FOR SELECT TO fl_job_owner USING (true);

-- 5.2 审计链：仅 SELECT（job_list_audit_chain_tenants）
DROP POLICY IF EXISTS p_hub_audit_chain_job_owner_select ON fenglin_hub.hub_audit_chain;
CREATE POLICY p_hub_audit_chain_job_owner_select ON fenglin_hub.hub_audit_chain
    FOR SELECT TO fl_job_owner USING (true);

-- 5.3 搜索索引任务：仅 SELECT（job_list_pending_search_index_tasks）
DROP POLICY IF EXISTS p_hub_search_index_task_job_owner_select ON fenglin_hub.hub_search_index_task;
CREATE POLICY p_hub_search_index_task_job_owner_select ON fenglin_hub.hub_search_index_task
    FOR SELECT TO fl_job_owner USING (true);

-- 5.4 消息：SELECT（候选发现 + 状态回写前回查）
DROP POLICY IF EXISTS p_hub_message_job_owner_select ON fenglin_hub.hub_message;
CREATE POLICY p_hub_message_job_owner_select ON fenglin_hub.hub_message
    FOR SELECT TO fl_job_owner USING (true);

-- 5.5 消息：UPDATE（仅 job_complete_message_retry 的 FAILED -> SENT）
DROP POLICY IF EXISTS p_hub_message_job_owner_update ON fenglin_hub.hub_message;
CREATE POLICY p_hub_message_job_owner_update ON fenglin_hub.hub_message
    FOR UPDATE TO fl_job_owner USING (true) WITH CHECK (true);

-- 5.6 消息回执：SELECT（两处回执计数）
DROP POLICY IF EXISTS p_hub_message_receipt_job_owner_select ON fenglin_hub.hub_message_receipt;
CREATE POLICY p_hub_message_receipt_job_owner_select ON fenglin_hub.hub_message_receipt
    FOR SELECT TO fl_job_owner USING (true);

-- 5.7 消息回执：INSERT（仅 job_complete_message_retry 回执写入）
DROP POLICY IF EXISTS p_hub_message_receipt_job_owner_insert ON fenglin_hub.hub_message_receipt;
CREATE POLICY p_hub_message_receipt_job_owner_insert ON fenglin_hub.hub_message_receipt
    FOR INSERT TO fl_job_owner WITH CHECK (true);

-- ----------------------------------------------------------------------------
-- 6) 迁移内自检：违反安全不变量直接失败（不静默放行）
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    v_policy_count integer;
    v_policy_tables text;
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_roles
         WHERE rolname IN ('fl_job_owner', 'fl_ddl_owner')
           AND (rolsuper OR rolbypassrls OR rolcanlogin)
    ) THEN
        RAISE EXCEPTION 'PLAT-20: fl_job_owner / fl_ddl_owner 必须是 NOLOGIN NOSUPERUSER NOBYPASSRLS';
    END IF;

    IF has_schema_privilege('fl_app', 'fenglin_hub', 'CREATE') THEN
        RAISE EXCEPTION 'PLAT-20: fl_app 不应拥有 fenglin_hub 的 CREATE 权限';
    END IF;

    IF NOT has_schema_privilege('fl_job_owner', 'fenglin_hub', 'USAGE') THEN
        RAISE EXCEPTION 'PLAT-20: fl_job_owner 缺少 fenglin_hub USAGE';
    END IF;

    -- 策略自证：恰好 7 条、覆盖恰好 5 张表、命令类型与函数需求一一对应
    SELECT count(*), string_agg(DISTINCT tablename, ',' ORDER BY tablename)
      INTO v_policy_count, v_policy_tables
      FROM pg_policies
     WHERE schemaname = 'fenglin_hub'
       AND policyname LIKE '%\_job\_owner\_%' ESCAPE '\';

    IF v_policy_count <> 7 THEN
        RAISE EXCEPTION 'PLAT-20: 期望 7 条 fl_job_owner 策略，实际 %', v_policy_count;
    END IF;

    IF v_policy_tables IS DISTINCT FROM
       'hub_audit_chain,hub_audit_export_task,hub_message,hub_message_receipt,hub_search_index_task' THEN
        RAISE EXCEPTION 'PLAT-20: 策略覆盖表不符合预期，实际 %', v_policy_tables;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM (VALUES
                    ('hub_audit_export_task', 'SELECT'),
                    ('hub_audit_chain',       'SELECT'),
                    ('hub_search_index_task', 'SELECT'),
                    ('hub_message',           'SELECT'),
                    ('hub_message',           'UPDATE'),
                    ('hub_message_receipt',   'SELECT'),
                    ('hub_message_receipt',   'INSERT')
               ) AS expected(tbl, cmd)
         WHERE NOT EXISTS (
                SELECT 1
                  FROM pg_policies p
                 WHERE p.schemaname = 'fenglin_hub'
                   AND p.tablename  = expected.tbl
                   AND p.cmd        = expected.cmd
                   AND p.policyname LIKE '%\_job\_owner\_%' ESCAPE '\'
         )
    ) THEN
        RAISE EXCEPTION 'PLAT-20: 策略命令类型与函数需求不匹配';
    END IF;
END
$$;

-- ----------------------------------------------------------------------------
-- 7) 自证查询（手工执行，一条 SQL 列出本迁移创建的 7 条策略）
--    期望：5 张表 / 7 条；cmd 只出现 SELECT、UPDATE、INSERT，不出现 DELETE/ALL
-- ----------------------------------------------------------------------------
-- SELECT tablename,
--        policyname,
--        cmd,
--        array_to_string(roles, ',') AS roles
--   FROM pg_policies
--  WHERE schemaname = 'fenglin_hub'
--    AND policyname LIKE '%\_job\_owner\_%' ESCAPE '\'
--  ORDER BY tablename, cmd;
--
-- 期望结果（按 tablename, cmd 排序）：
--   hub_audit_chain           | p_hub_audit_chain_job_owner_select          | SELECT | fl_job_owner
--   hub_audit_export_task     | p_hub_audit_export_task_job_owner_select    | SELECT | fl_job_owner
--   hub_message               | p_hub_message_job_owner_select              | SELECT | fl_job_owner
--   hub_message               | p_hub_message_job_owner_update              | UPDATE | fl_job_owner
--   hub_message_receipt       | p_hub_message_receipt_job_owner_insert      | INSERT | fl_job_owner
--   hub_message_receipt       | p_hub_message_receipt_job_owner_select      | SELECT | fl_job_owner
--   hub_search_index_task     | p_hub_search_index_task_job_owner_select    | SELECT | fl_job_owner

-- ============================================================================
-- 回滚（手工，按需执行；在 V1_0_60 尚未应用时）
--   DROP POLICY IF EXISTS p_hub_audit_export_task_job_owner_select ON fenglin_hub.hub_audit_export_task;
--   DROP POLICY IF EXISTS p_hub_audit_chain_job_owner_select       ON fenglin_hub.hub_audit_chain;
--   DROP POLICY IF EXISTS p_hub_search_index_task_job_owner_select ON fenglin_hub.hub_search_index_task;
--   DROP POLICY IF EXISTS p_hub_message_job_owner_select           ON fenglin_hub.hub_message;
--   DROP POLICY IF EXISTS p_hub_message_job_owner_update           ON fenglin_hub.hub_message;
--   DROP POLICY IF EXISTS p_hub_message_receipt_job_owner_select   ON fenglin_hub.hub_message_receipt;
--   DROP POLICY IF EXISTS p_hub_message_receipt_job_owner_insert   ON fenglin_hub.hub_message_receipt;
--   -- 旧草稿 FOR ALL 策略名（如曾手工执行过）
--   DROP POLICY IF EXISTS p_hub_audit_export_task_job_owner ON fenglin_hub.hub_audit_export_task;
--   DROP POLICY IF EXISTS p_hub_audit_chain_job_owner        ON fenglin_hub.hub_audit_chain;
--   DROP POLICY IF EXISTS p_hub_search_index_task_job_owner  ON fenglin_hub.hub_search_index_task;
--   DROP POLICY IF EXISTS p_hub_message_job_owner            ON fenglin_hub.hub_message;
--   DROP POLICY IF EXISTS p_hub_message_receipt_job_owner    ON fenglin_hub.hub_message_receipt;
--   REVOKE ALL ON fenglin_hub.hub_audit_export_task   FROM fl_job_owner;
--   REVOKE ALL ON fenglin_hub.hub_audit_chain         FROM fl_job_owner;
--   REVOKE ALL ON fenglin_hub.hub_search_index_task   FROM fl_job_owner;
--   REVOKE ALL ON fenglin_hub.hub_message             FROM fl_job_owner;
--   REVOKE ALL ON fenglin_hub.hub_message_receipt     FROM fl_job_owner;
--   REVOKE CREATE ON SCHEMA fenglin_hub FROM fl_ddl_owner;
--   REVOKE USAGE  ON SCHEMA fenglin_hub FROM fl_job_owner, fl_ddl_owner;
-- 若 59 已被 Flyway 记录：先手工执行回滚，再新增一个前向迁移修正，
-- 不要直接改已应用迁移的 checksum，也不要用 flyway repair 掩盖。
-- ============================================================================
