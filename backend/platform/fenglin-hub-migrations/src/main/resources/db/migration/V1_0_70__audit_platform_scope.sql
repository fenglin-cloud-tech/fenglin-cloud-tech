-- 平台级审计作用域收口：
--   1) 平台级事件（无租户上下文）改用保留租户 -1，不再写 0；
--   2) 租户 RLS 策略删除 `tenant_id = 0` 旁路，0 / -1 行只对平台身份可见；
--   3) 存量 tenant_id = 0 的审计行迁移到 -1，并同步重算哈希链，保持可校验。
-- 代价：父表 + 4 个分区 + 链表各重建 1 条策略（ALTER POLICY 级别，不重写表数据）；
--       仅迁移 tenant_id = 0 的行（本地仅有 1 条合成自检行），不影响其他租户数据。
-- 回滚：见文件末尾注释。

SET search_path TO fenglin_hub, public;

-- 1) 内容哈希复算函数：与 AuditChainHasher.content 保持同一规则（\u001F 分隔、NULL 用 \u0000）
CREATE FUNCTION pg_temp.audit_content_hash(
    p_id bigint, p_tenant_id bigint, p_log_no text, p_event_type text, p_module text, p_action text,
    p_operator_id bigint, p_operator_name text, p_target_type text, p_target_id text,
    p_request_id text, p_trace_id text, p_client_type text, p_result text, p_risk_level text,
    p_detail jsonb, p_occurred_at timestamptz) RETURNS text
LANGUAGE sql IMMUTABLE AS $$
    SELECT encode(sha256(
        convert_to(p_id::text, 'UTF8') || decode('1f', 'hex') ||
        convert_to(p_tenant_id::text, 'UTF8') || decode('1f', 'hex') ||
        convert_to(p_log_no, 'UTF8') || decode('1f', 'hex') ||
        convert_to(p_event_type, 'UTF8') || decode('1f', 'hex') ||
        convert_to(p_module, 'UTF8') || decode('1f', 'hex') ||
        convert_to(p_action, 'UTF8') || decode('1f', 'hex') ||
        CASE WHEN p_operator_id IS NULL THEN decode('00', 'hex') ELSE convert_to(p_operator_id::text, 'UTF8') END
            || decode('1f', 'hex') ||
        CASE WHEN p_operator_name IS NULL THEN decode('00', 'hex') ELSE convert_to(p_operator_name, 'UTF8') END
            || decode('1f', 'hex') ||
        CASE WHEN p_target_type IS NULL THEN decode('00', 'hex') ELSE convert_to(p_target_type, 'UTF8') END
            || decode('1f', 'hex') ||
        CASE WHEN p_target_id IS NULL THEN decode('00', 'hex') ELSE convert_to(p_target_id, 'UTF8') END
            || decode('1f', 'hex') ||
        CASE WHEN p_request_id IS NULL THEN decode('00', 'hex') ELSE convert_to(p_request_id, 'UTF8') END
            || decode('1f', 'hex') ||
        CASE WHEN p_trace_id IS NULL THEN decode('00', 'hex') ELSE convert_to(p_trace_id, 'UTF8') END
            || decode('1f', 'hex') ||
        CASE WHEN p_client_type IS NULL THEN decode('00', 'hex') ELSE convert_to(p_client_type, 'UTF8') END
            || decode('1f', 'hex') ||
        convert_to(p_result, 'UTF8') || decode('1f', 'hex') ||
        convert_to(p_risk_level, 'UTF8') || decode('1f', 'hex') ||
        CASE WHEN p_detail IS NULL THEN decode('00', 'hex')
             ELSE convert_to(replace(replace(p_detail::text, ', ', ','), ': ', ':'), 'UTF8') END
            || decode('1f', 'hex') ||
        convert_to(
            CASE
                WHEN extract(microsecond FROM p_occurred_at)::bigint % 1000000 = 0
                    THEN to_char(p_occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
                WHEN extract(microsecond FROM p_occurred_at)::bigint % 1000 = 0
                    THEN to_char(p_occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')
                ELSE to_char(p_occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
            END, 'UTF8')),
        'hex');
$$;

-- 2) 迁移前自检：哈希可复算、链序号不冲突
DO $$
DECLARE
    v_mismatch bigint;
    v_conflict bigint;
BEGIN
    SELECT count(*) INTO v_mismatch
      FROM hub_audit_log l
      JOIN hub_audit_chain c ON c.tenant_id = l.tenant_id AND c.log_id = l.id
     WHERE l.tenant_id = 0
       AND c.content_hash <> pg_temp.audit_content_hash(l.id, l.tenant_id, l.log_no, l.event_type,
               l.module, l.action, l.operator_id, l.operator_name, l.target_type, l.target_id,
               l.request_id, l.trace_id, l.client_type, l.result, l.risk_level, l.detail, l.occurred_at);
    IF v_mismatch > 0 THEN
        RAISE EXCEPTION '存量 tenant_id=0 审计行内容哈希无法复算（% 行），请人工确认 detail 规范化后再迁移', v_mismatch;
    END IF;

    SELECT count(*) INTO v_conflict
      FROM hub_audit_chain z
      JOIN hub_audit_chain p ON p.partition_key = z.partition_key
     WHERE z.tenant_id = 0 AND p.tenant_id = -1;
    IF v_conflict > 0 THEN
        RAISE EXCEPTION '保留租户 -1 在同一分区已有链记录，迁移会破坏序号连续性，需人工处理';
    END IF;
END $$;

-- 3) 迁移 tenant_id = 0 的日志与链记录，并重算哈希
CREATE TEMP TABLE tmp_audit_scope_migration ON COMMIT DROP AS
SELECT l.id        AS log_id,
       c.id        AS chain_id,
       c.prev_hash AS prev_hash,
       pg_temp.audit_content_hash(l.id, -1, l.log_no, l.event_type, l.module, l.action,
               l.operator_id, l.operator_name, l.target_type, l.target_id,
               l.request_id, l.trace_id, l.client_type, l.result, l.risk_level,
               l.detail, l.occurred_at) AS new_content_hash
  FROM hub_audit_log l
  JOIN hub_audit_chain c ON c.tenant_id = l.tenant_id AND c.log_id = l.id
 WHERE l.tenant_id = 0;

UPDATE hub_audit_chain c
   SET tenant_id = -1,
       content_hash = t.new_content_hash,
       chain_hash = encode(sha256(convert_to(c.prev_hash, 'UTF8') || convert_to(t.new_content_hash, 'UTF8')), 'hex'),
       updated_at = now()
  FROM tmp_audit_scope_migration t
 WHERE c.id = t.chain_id;

-- 未入链的 0 行只需改归属，不存在哈希依赖
UPDATE hub_audit_log SET tenant_id = -1 WHERE tenant_id = 0;

-- 4) 重建租户策略：删除 tenant_id = 0 旁路（父表 + 所有分区 + 链表）
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT c.relname
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'fenglin_hub'
           AND c.relname LIKE 'hub_audit_log%'
           AND c.relkind IN ('r', 'p')
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS p_hub_audit_log_tenant ON fenglin_hub.%I', r.relname);
        EXECUTE format('CREATE POLICY p_hub_audit_log_tenant ON fenglin_hub.%I '
                       'USING (tenant_id = fenglin_hub.current_tenant_id()) '
                       'WITH CHECK (tenant_id = fenglin_hub.current_tenant_id())', r.relname);
    END LOOP;
END $$;

DROP POLICY IF EXISTS p_hub_audit_chain_tenant ON hub_audit_chain;
CREATE POLICY p_hub_audit_chain_tenant ON hub_audit_chain
    USING (tenant_id = fenglin_hub.current_tenant_id())
    WITH CHECK (tenant_id = fenglin_hub.current_tenant_id());

-- 5) 迁移后自检：无 0 行、无 0 旁路策略
DO $$
DECLARE
    v_bad_rows bigint;
    v_bad_policies bigint;
BEGIN
    SELECT count(*) INTO v_bad_rows FROM hub_audit_log WHERE tenant_id = 0;
    IF v_bad_rows > 0 THEN
        RAISE EXCEPTION '迁移后仍有 % 条 tenant_id=0 审计行', v_bad_rows;
    END IF;

    SELECT count(*) INTO v_bad_policies
      FROM pg_policies
     WHERE schemaname = 'fenglin_hub'
       AND tablename LIKE 'hub_audit%'
       AND policyname LIKE '%_tenant'
       AND (coalesce(qual, '') LIKE '%tenant_id = 0%' OR coalesce(with_check, '') LIKE '%tenant_id = 0%');
    IF v_bad_policies > 0 THEN
        RAISE EXCEPTION '迁移后仍有 % 条策略放行 tenant_id=0', v_bad_policies;
    END IF;
END $$;

-- 回滚（Flyway 无下行迁移，需要时手工执行）：
--   DROP POLICY IF EXISTS p_hub_audit_log_tenant ON fenglin_hub.<表或分区>;
--   CREATE POLICY p_hub_audit_log_tenant ON fenglin_hub.<表或分区>
--       USING (tenant_id = 0 OR tenant_id = fenglin_hub.current_tenant_id())
--       WITH CHECK (tenant_id = 0 OR tenant_id = fenglin_hub.current_tenant_id());
--   链表同理；
--   UPDATE hub_audit_log SET tenant_id = 0 WHERE tenant_id = -1;（链表需按新 tenant_id 重算 content_hash/chain_hash，
--   否则该租户链校验会失败）
