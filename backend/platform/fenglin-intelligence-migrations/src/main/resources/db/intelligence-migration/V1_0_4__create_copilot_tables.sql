-- H2 Copilot/Agent 域：会话、消息、引用、智能体定义、执行记录、问答反馈
SET search_path TO fenglin_intelligence, public;

CREATE TABLE ai_conversation (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    title varchar(256) NOT NULL,
    user_id bigint NOT NULL,
    type varchar(16) NOT NULL DEFAULT 'CHAT',
    kb_ids bigint[],
    model_code varchar(64),
    message_count int NOT NULL DEFAULT 0,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    pinned boolean NOT NULL DEFAULT false,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_conversation IS 'AI 会话';
COMMENT ON COLUMN ai_conversation.type IS 'CHAT/RAG/AGENT/BI';
COMMENT ON COLUMN ai_conversation.status IS 'ACTIVE/ARCHIVED';

CREATE TABLE ai_message (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    conversation_id bigint NOT NULL,
    role varchar(16) NOT NULL,
    content text NOT NULL,
    content_type varchar(16) NOT NULL DEFAULT 'TEXT',
    token_in int,
    token_out int,
    model_code varchar(64),
    latency_ms int,
    ttft_ms int,
    guardrail_hits jsonb,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_message IS '会话消息（不可删，留痕）';
COMMENT ON COLUMN ai_message.role IS 'USER/ASSISTANT/SYSTEM/TOOL';
COMMENT ON COLUMN ai_message.content_type IS 'TEXT/MARKDOWN/CHART/TABLE';

CREATE TABLE ai_message_citation (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    message_id bigint NOT NULL,
    document_id bigint NOT NULL,
    chunk_id bigint NOT NULL,
    score numeric(8,4),
    snippet text,
    page_no int,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_message_citation IS '消息引用（不可删，回答可溯源）';

CREATE TABLE ai_agent_definition (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    code varchar(64) NOT NULL,
    name varchar(128) NOT NULL,
    description text,
    nodes jsonb NOT NULL,
    tools jsonb,
    model_code varchar(64),
    version_no int NOT NULL DEFAULT 1,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    max_steps int NOT NULL DEFAULT 20,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_agent_definition IS '智能体定义（编排由本域负责，模型调用仍走平台网关）';
COMMENT ON COLUMN ai_agent_definition.status IS 'DRAFT/PUBLISHED/DISABLED';
COMMENT ON COLUMN ai_agent_definition.nodes IS '编排节点：LLM/TOOL/CONDITION/LOOP/RAG/OUTPUT';

CREATE TABLE ai_agent_execution (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    agent_id bigint NOT NULL,
    conversation_id bigint,
    trigger_user_id bigint,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    input_json jsonb,
    output_json jsonb,
    steps jsonb,
    step_count int NOT NULL DEFAULT 0,
    token_total int,
    error_msg text,
    trace_id varchar(64),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_agent_execution IS '智能体执行记录（不可删，留痕）';
COMMENT ON COLUMN ai_agent_execution.status IS 'PENDING/RUNNING/SUCCESS/FAILED/CANCELED';

CREATE TABLE ai_qa_feedback (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    message_id bigint NOT NULL,
    user_id bigint NOT NULL,
    feedback_type varchar(16) NOT NULL,
    rating smallint,
    comment text,
    corrected_doc_id bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_feedback_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)
);
COMMENT ON TABLE ai_qa_feedback IS '问答反馈（不可删，用于召回优化）';
COMMENT ON COLUMN ai_qa_feedback.feedback_type IS 'LIKE/DISLIKE/CORRECTION';
