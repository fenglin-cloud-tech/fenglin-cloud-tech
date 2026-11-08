-- IM 域：会话、会话成员、消息、已读、表情回复
-- 偏差标注：文档要求 collab_message 按月分区，但唯一约束 (tenant_id, conversation_id, client_msg_id) 不含分区键，
-- PostgreSQL 分区表无法满足；首版采用普通表 + 会话序号索引，分区/归档列后续演进（G-12/性能阶段）。
SET search_path TO fenglin_collab, public;

CREATE TABLE collab_conversation (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    conv_type varchar(16) NOT NULL,
    title varchar(128),
    avatar_file_id uuid,
    owner_id bigint,
    member_count int NOT NULL DEFAULT 0,
    announcement text,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    last_message_at timestamptz,
    last_message_id bigint,
    source varchar(16) NOT NULL DEFAULT 'INTERNAL',
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_conv_type CHECK (conv_type IN ('SINGLE', 'GROUP', 'TOPIC')),
    CONSTRAINT ck_conv_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DISSOLVED')),
    CONSTRAINT ck_conv_source CHECK (source IN ('INTERNAL', 'EXTERNAL'))
);

CREATE TABLE collab_conversation_member (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    conversation_id uuid NOT NULL REFERENCES collab_conversation (id),
    member_id bigint NOT NULL,
    role varchar(16) NOT NULL DEFAULT 'MEMBER',
    join_at timestamptz NOT NULL DEFAULT now(),
    mute smallint NOT NULL DEFAULT 0,
    pinned smallint NOT NULL DEFAULT 0,
    last_read_message_id bigint,
    member_status varchar(16) NOT NULL DEFAULT 'NORMAL',
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_conv_member_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    CONSTRAINT ck_conv_member_status CHECK (member_status IN ('NORMAL', 'QUIT', 'KICKED'))
);

CREATE TABLE collab_message (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    conversation_id uuid NOT NULL REFERENCES collab_conversation (id),
    sender_id bigint NOT NULL,
    msg_type varchar(24) NOT NULL DEFAULT 'TEXT',
    content_cipher text,
    content_hash varchar(64),
    summary varchar(256),
    file_id uuid,
    card_type varchar(24),
    card_payload jsonb,
    reply_to_id bigint,
    client_msg_id varchar(64) NOT NULL,
    seq bigint NOT NULL,
    revoked smallint NOT NULL DEFAULT 0,
    revoked_at timestamptz,
    -- 扩展：接口 API-IM-008 请求体含撤回原因，数据库文档未定义字段
    revoked_reason varchar(128),
    sent_at timestamptz NOT NULL DEFAULT now(),
    mention_ids bigint[],
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_msg_type CHECK (msg_type IN ('TEXT', 'RICH', 'IMAGE', 'FILE', 'AUDIO', 'VIDEO', 'CARD', 'SYSTEM')),
    CONSTRAINT uk_msg_idem UNIQUE (tenant_id, conversation_id, client_msg_id)
);

CREATE TABLE collab_message_read (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    conversation_id uuid NOT NULL,
    message_id bigint NOT NULL,
    reader_id bigint NOT NULL,
    read_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT uk_msg_read UNIQUE (tenant_id, message_id, reader_id)
);

CREATE TABLE collab_message_reaction (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    message_id bigint NOT NULL,
    user_id bigint NOT NULL,
    emoji varchar(32) NOT NULL,
    reacted_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT uk_msg_reaction UNIQUE (tenant_id, message_id, user_id, emoji)
);

COMMENT ON TABLE collab_message IS '消息主表（正文 AES-256-GCM 密文 + 租户盐哈希，禁止明文检索）';
COMMENT ON COLUMN collab_message.revoked_reason IS '扩展：文档未定义撤回原因字段，按接口 API-IM-008 最小补齐';
