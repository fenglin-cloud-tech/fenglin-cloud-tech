-- G1：履约节点、到期提醒、合同附件
SET search_path TO fenglin_legal, public;

CREATE TABLE legal_performance_node (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    contract_id bigint NOT NULL,
    node_type varchar(24) NOT NULL,
    name varchar(200) NOT NULL,
    plan_date date,
    actual_date date,
    amount numeric(18,2),
    owner_id bigint,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    evidence_ref varchar(255),
    remark text,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_performance_node IS '履约节点';
COMMENT ON COLUMN legal_performance_node.node_type IS '类型：DELIVERY/ACCEPTANCE/PAYMENT/INVOICE/WARRANTY/OTHER';
COMMENT ON COLUMN legal_performance_node.status IS '状态：PENDING/IN_PROGRESS/DONE/OVERDUE/CANCELED';

CREATE TABLE legal_expiry_reminder (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    contract_id bigint NOT NULL,
    offset_days int NOT NULL,
    trigger_date date NOT NULL,
    channel varchar(32),
    receiver_ids jsonb,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    sent_at timestamptz,
    handle_action varchar(16),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_expiry_reminder IS '合同到期提醒（T-90/60/30/15/7）';
COMMENT ON COLUMN legal_expiry_reminder.status IS '状态：PENDING/SENT/FAILED/HANDLED';
COMMENT ON COLUMN legal_expiry_reminder.handle_action IS '处理动作：RENEW/TERMINATE/ARCHIVE/IGNORE';

CREATE TABLE legal_contract_attachment (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    contract_id bigint NOT NULL,
    file_name varchar(255) NOT NULL,
    file_ref varchar(255) NOT NULL,
    file_hash varchar(64),
    file_size bigint,
    attach_type varchar(24),
    watermark boolean DEFAULT false,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_contract_attachment IS '合同附件';
COMMENT ON COLUMN legal_contract_attachment.attach_type IS '类型：MAIN/ANNEX/SCAN/OTHER';
