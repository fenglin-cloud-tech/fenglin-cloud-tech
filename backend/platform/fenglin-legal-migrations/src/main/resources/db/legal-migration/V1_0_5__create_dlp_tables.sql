-- G2 内部信息防泄漏：敏感规则/策略/通道/事件/处置/水印/审计/白名单/终端
SET search_path TO fenglin_legal, public;

CREATE TABLE legal_sensitive_rule (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    rule_no varchar(32) NOT NULL,
    name varchar(128) NOT NULL,
    rule_type varchar(24) NOT NULL,
    expression text,
    data_level varchar(8) NOT NULL,
    category varchar(32),
    threshold numeric(6,4),
    priority int DEFAULT 100,
    enabled boolean NOT NULL DEFAULT true,
    remark text,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_rule_threshold CHECK (threshold IS NULL OR (threshold >= 0 AND threshold <= 1))
);
COMMENT ON TABLE legal_sensitive_rule IS '敏感识别规则';
COMMENT ON COLUMN legal_sensitive_rule.rule_type IS '类型：KEYWORD/REGEX/FINGERPRINT/MODEL/CLASSIFICATION';
COMMENT ON COLUMN legal_sensitive_rule.data_level IS '数据级别：PUBLIC/INTERNAL/SECRET/CONFIDENTIAL';

CREATE TABLE legal_dlp_policy (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    policy_no varchar(32) NOT NULL,
    name varchar(128) NOT NULL,
    data_level varchar(8) NOT NULL,
    rule_ids jsonb NOT NULL,
    scope_org_ids jsonb,
    scope_role_ids jsonb,
    action varchar(24) NOT NULL,
    channel_codes jsonb,
    effective_from timestamptz,
    effective_to timestamptz,
    priority int DEFAULT 100,
    enabled boolean NOT NULL DEFAULT true,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 1
);
COMMENT ON TABLE legal_dlp_policy IS 'DLP 策略';
COMMENT ON COLUMN legal_dlp_policy.action IS '动作：ALLOW/WARN/AUDIT/BLOCK/APPROVE/ENCRYPT/WATERMARK';
COMMENT ON COLUMN legal_dlp_policy.version IS '公共乐观锁版本，同时作为策略版本（缓存失效依据），文档同名业务字段合并至此列';

CREATE TABLE legal_dlp_channel (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    policy_id bigint,
    channel_code varchar(32) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    default_action varchar(24),
    params jsonb,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_dlp_channel IS 'DLP 通道配置';
COMMENT ON COLUMN legal_dlp_channel.channel_code IS '通道：USB/PRINT/CLIPBOARD/SCREENSHOT/NETDISK/EMAIL/IM/WEB_UPLOAD';

CREATE TABLE legal_dlp_event (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    event_no varchar(32) NOT NULL,
    event_type varchar(24) NOT NULL,
    channel_code varchar(32) NOT NULL,
    user_id bigint,
    org_id bigint,
    terminal_id bigint,
    data_level varchar(8),
    file_name varchar(255),
    file_fingerprint varchar(64),
    matched_rule_ids jsonb,
    matched_snippet text,
    action_taken varchar(24) NOT NULL,
    release_id bigint,
    risk_score numeric(6,2),
    status varchar(16) NOT NULL DEFAULT 'NEW',
    occurred_at timestamptz NOT NULL,
    source varchar(16),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_event_risk CHECK (risk_score IS NULL OR (risk_score >= 0 AND risk_score <= 100))
);
COMMENT ON TABLE legal_dlp_event IS 'DLP 外发/泄漏事件（不可删；文档要求按 tenant_id + occurred_at 分区，此处普通表+索引）';
COMMENT ON COLUMN legal_dlp_event.event_type IS '类型：OUTBOUND/BLOCKED/RELEASE_APPLY/SUSPECTED_LEAK';
COMMENT ON COLUMN legal_dlp_event.action_taken IS '动作：ALLOW/WARN/BLOCK/APPROVE';
COMMENT ON COLUMN legal_dlp_event.status IS '状态：NEW/TRIAGING/INVESTIGATING/HANDLED/FALSE_POSITIVE/ESCALATED';
COMMENT ON COLUMN legal_dlp_event.source IS '来源：AGENT/GATEWAY/API';

CREATE TABLE legal_dlp_event_handle (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    event_id bigint NOT NULL,
    handler_id bigint,
    action varchar(24) NOT NULL,
    conclusion varchar(1000),
    rectify_item varchar(1000),
    attachment_ref varchar(255),
    sla_deadline timestamptz,
    acted_at timestamptz NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_dlp_event_handle IS 'DLP 事件处置记录（不可删，留痕）';
COMMENT ON COLUMN legal_dlp_event_handle.action IS '动作：TRIAGE/INVESTIGATE/CONFIRM/FALSE_POSITIVE/ESCALATE/CLOSE';

CREATE TABLE legal_watermark_record (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    watermark_no varchar(32) NOT NULL,
    biz_type varchar(24) NOT NULL,
    biz_id bigint,
    watermark_type varchar(16) NOT NULL,
    embed_code varchar(128) NOT NULL,
    user_id bigint,
    terminal_id bigint,
    ip inet,
    file_hash varchar(64),
    trace_status varchar(16) DEFAULT 'NONE',
    traced_event_id bigint,
    applied_at timestamptz NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_watermark_record IS '水印记录（不可删，留痕）';
COMMENT ON COLUMN legal_watermark_record.watermark_type IS '类型：VISIBLE/INVISIBLE/BOTH';
COMMENT ON COLUMN legal_watermark_record.trace_status IS '溯源状态：NONE/TRACED';

CREATE TABLE legal_dlp_audit_log (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    log_id varchar(64) NOT NULL,
    action varchar(32) NOT NULL,
    operator_id bigint,
    target_type varchar(32),
    target_id bigint,
    detail jsonb,
    result varchar(16),
    occurred_at timestamptz NOT NULL,
    chain_id varchar(64) NOT NULL,
    seq_no bigint NOT NULL,
    prev_hash varchar(64),
    block_hash varchar(64) NOT NULL,
    worm_ref varchar(255),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_dlp_audit_log IS 'DLP 审计日志（不可删；哈希链 + WORM；文档要求月分区，此处普通表+索引）';
COMMENT ON COLUMN legal_dlp_audit_log.action IS '动作：RULE_CONFIG/POLICY_CONFIG/CHANNEL_CONFIG/WHITELIST/EVENT_HANDLE/DECIDE/WATERMARK/RELEASE';

CREATE TABLE legal_dlp_whitelist (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    name varchar(128) NOT NULL,
    channel_code varchar(32) NOT NULL,
    match_type varchar(24) NOT NULL,
    match_value varchar(512) NOT NULL,
    data_level varchar(8),
    reason varchar(500),
    approval_record_id bigint,
    effective_from timestamptz NOT NULL,
    effective_to timestamptz NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_whitelist_window CHECK (effective_to > effective_from)
);
COMMENT ON TABLE legal_dlp_whitelist IS 'DLP 白名单/例外（命中不拦截但留审计）';
COMMENT ON COLUMN legal_dlp_whitelist.match_type IS '匹配类型：ORG/ROLE/USER/FILE/URL';
COMMENT ON COLUMN legal_dlp_whitelist.status IS '状态：ACTIVE/EXPIRED/REVOKED';

CREATE TABLE legal_dlp_terminal (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    terminal_no varchar(32) NOT NULL,
    hostname varchar(128),
    mac_hash varchar(64),
    os_type varchar(24),
    os_version varchar(64),
    user_id bigint,
    agent_version varchar(32),
    policy_version int,
    last_heartbeat_at timestamptz,
    status varchar(16) NOT NULL DEFAULT 'OFFLINE',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_dlp_terminal IS 'DLP 受管终端';
COMMENT ON COLUMN legal_dlp_terminal.mac_hash IS 'MAC 地址 SHA-256(地址+租户盐)';
COMMENT ON COLUMN legal_dlp_terminal.status IS '状态：ONLINE/OFFLINE/UNREGISTERED/BLOCKED';
