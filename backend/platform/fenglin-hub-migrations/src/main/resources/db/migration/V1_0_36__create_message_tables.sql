-- P2 消息中心：消息（按月分区）、模板、回执、订阅偏好。
-- 订阅偏好属于建设方案 C-12 缺口兜底新增表；其余三张按数据库设计 4.32~4.34。

SET search_path TO fenglin_hub, public;

CREATE TABLE hub_message (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    message_no VARCHAR(40) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    template_code VARCHAR(64),
    receiver_id BIGINT NOT NULL,
    title VARCHAR(256),
    content TEXT,
    biz_type VARCHAR(64),
    biz_id VARCHAR(64),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted SMALLINT NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_message PRIMARY KEY (id, created_at),
    CONSTRAINT uk_hub_message_no UNIQUE (message_no, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE hub_message_default PARTITION OF hub_message DEFAULT;

CREATE INDEX idx_hub_message_recv ON hub_message (tenant_id, receiver_id, status, created_at DESC);
CREATE INDEX idx_hub_message_dedup
    ON hub_message (tenant_id, template_code, biz_id, receiver_id, created_at DESC);
CREATE INDEX idx_hub_message_template ON hub_message (tenant_id, template_code);

CREATE TABLE hub_message_template (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    title_tpl VARCHAR(256),
    content_tpl TEXT,
    variables VARCHAR(512),
    locale VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted SMALLINT NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_message_template PRIMARY KEY (id),
    CONSTRAINT uk_hub_message_template UNIQUE (tenant_id, code)
);

CREATE INDEX idx_hub_message_template_tenant ON hub_message_template (tenant_id, status);

CREATE TABLE hub_message_receipt (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    channel_msg_id VARCHAR(128),
    status VARCHAR(16) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_msg VARCHAR(512),
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted SMALLINT NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_message_receipt PRIMARY KEY (id)
);

CREATE INDEX idx_hub_message_receipt_msg ON hub_message_receipt (tenant_id, message_id);

ALTER TABLE hub_message ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_message_template ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_message_receipt ENABLE ROW LEVEL SECURITY;

CREATE POLICY p_hub_message_tenant ON hub_message
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_message_template_tenant ON hub_message_template
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_message_receipt_tenant ON hub_message_receipt
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_message TO fl_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_message_template TO fl_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_message_receipt TO fl_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_message TO fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_message_template TO fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_message_receipt TO fl_platform_role;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8391, 'tenant:message:template:manage', '消息模板管理', 'API', 'message', 1, 0),
       (8392, 'tenant:message:channel:manage', '消息通道管理', 'API', 'message', 2, 0),
       (8393, 'biz:message:view', '消息查看', 'API', 'message', 3, 0),
       (8394, 'biz:message:preference:manage', '消息偏好管理', 'API', 'message', 4, 0)
ON CONFLICT (code) DO NOTHING;
