-- G1：合同模板/模板版本/模板变量/合同主表/合同版本/条款/相对方/签署方/审批记录
SET search_path TO fenglin_legal, public;

CREATE TABLE legal_contract_template (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    template_no varchar(32) NOT NULL,
    name varchar(200) NOT NULL,
    category varchar(32) NOT NULL,
    current_version_id bigint,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    applied_org_ids jsonb,
    visible_scope varchar(16) DEFAULT 'TENANT',
    owner_id bigint,
    remark text,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_contract_template IS '合同模板';
COMMENT ON COLUMN legal_contract_template.category IS '分类：PURCHASE/SALES/LABOR/NDA/LEASE/OTHER';
COMMENT ON COLUMN legal_contract_template.status IS '状态：DRAFT/PUBLISHED/DISABLED';
COMMENT ON COLUMN legal_contract_template.visible_scope IS '可见范围：TENANT/ORG/CUSTOM';

CREATE TABLE legal_template_version (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    template_id bigint NOT NULL,
    version_no int NOT NULL,
    content_ref varchar(255),
    content_hash varchar(64) NOT NULL,
    change_summary varchar(500),
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    published_at timestamptz,
    published_by bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_template_version IS '模板版本';
COMMENT ON COLUMN legal_template_version.status IS '状态：DRAFT/PUBLISHED/ARCHIVED';

CREATE TABLE legal_template_variable (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    template_version_id bigint NOT NULL,
    var_key varchar(64) NOT NULL,
    var_label varchar(128) NOT NULL,
    data_type varchar(16) NOT NULL,
    required boolean NOT NULL DEFAULT false,
    default_value varchar(512),
    value_source varchar(32),
    options jsonb,
    sort_no int DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_template_variable IS '模板变量';
COMMENT ON COLUMN legal_template_variable.data_type IS '类型：TEXT/NUMBER/DATE/SELECT/PARTY';
COMMENT ON COLUMN legal_template_variable.value_source IS '取值来源：MANUAL/CONTRACT_FIELD/SYSTEM/EXTERNAL';

CREATE TABLE legal_contract (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    contract_no varchar(32) NOT NULL,
    name varchar(200) NOT NULL,
    category varchar(32) NOT NULL,
    template_id bigint,
    template_version_id bigint,
    current_version_id bigint,
    party_a_id bigint,
    party_b_id bigint,
    amount numeric(18,2),
    currency varchar(8) DEFAULT 'CNY',
    start_date date,
    end_date date,
    sign_date date,
    owner_id bigint,
    org_id bigint,
    risk_level varchar(8) DEFAULT 'LOW',
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    seal_status varchar(16) DEFAULT 'NONE',
    sign_status varchar(16) DEFAULT 'NONE',
    archive_hash varchar(64),
    archived_at timestamptz,
    chain_id varchar(64),
    remark text,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_contract_amount CHECK (amount IS NULL OR amount >= 0)
);
COMMENT ON TABLE legal_contract IS '合同主表';
COMMENT ON COLUMN legal_contract.category IS '类型：PURCHASE/SALES/LABOR/NDA/LEASE/OTHER';
COMMENT ON COLUMN legal_contract.status IS '状态：DRAFT/APPROVING/APPROVED/SEALING/SIGNING/SIGNED/EFFECTIVE/TERMINATED/EXPIRED/ARCHIVED';
COMMENT ON COLUMN legal_contract.seal_status IS '用印状态：NONE/PENDING/SEALED';
COMMENT ON COLUMN legal_contract.sign_status IS '签署状态：NONE/SIGNING/SIGNED';
COMMENT ON COLUMN legal_contract.risk_level IS '相对方风险：LOW/MEDIUM/HIGH';

CREATE TABLE legal_contract_version (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    contract_id bigint NOT NULL,
    version_no int NOT NULL,
    content_ref varchar(255),
    content_hash varchar(64) NOT NULL,
    variable_values jsonb,
    change_summary varchar(500),
    diff_digest varchar(64),
    created_stage varchar(16),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_contract_version IS '合同版本（不可删，留痕）';
COMMENT ON COLUMN legal_contract_version.created_stage IS '产生阶段：DRAFT/APPROVE/SEAL/SIGN/ARCHIVE';

CREATE TABLE legal_contract_clause (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    contract_id bigint NOT NULL,
    contract_version_id bigint NOT NULL,
    clause_no varchar(32) NOT NULL,
    title varchar(200),
    content text,
    is_mandatory boolean DEFAULT false,
    risk_tag varchar(32),
    sort_no int DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_clause_mandatory CHECK (is_mandatory IN (true, false))
);
COMMENT ON TABLE legal_contract_clause IS '合同条款（不可删，留痕）';
COMMENT ON COLUMN legal_contract_clause.risk_tag IS '风险标签（如：免责条款/无限责任）';

CREATE TABLE legal_contract_party (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    name varchar(200) NOT NULL,
    uscc varchar(32),
    uscc_hash varchar(64),
    contact_name varchar(64),
    contact_mobile_enc text,
    contact_mobile_hash varchar(64),
    contact_mobile_tail varchar(4),
    email varchar(128),
    legal_rep varchar(64),
    address varchar(255),
    risk_level varchar(8) DEFAULT 'LOW',
    status varchar(16) DEFAULT 'ACTIVE',
    remark text,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_contract_party IS '合同相对方';
COMMENT ON COLUMN legal_contract_party.uscc_hash IS '统一社会信用代码 SHA-256(代码+租户盐)';
COMMENT ON COLUMN legal_contract_party.contact_mobile_enc IS '联系人手机号密文 AES-256-GCM';
COMMENT ON COLUMN legal_contract_party.contact_mobile_hash IS '联系人手机号哈希';
COMMENT ON COLUMN legal_contract_party.status IS '状态：ACTIVE/BLACKLIST';

CREATE TABLE legal_signatory (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    contract_id bigint NOT NULL,
    party_type varchar(16) NOT NULL,
    party_id bigint,
    signer_name varchar(64) NOT NULL,
    signer_id_no_enc text,
    signer_id_hash varchar(64),
    signer_mobile_enc text,
    signer_mobile_hash varchar(64),
    signer_mobile_tail varchar(4),
    seal_mode varchar(16) NOT NULL,
    sign_order int NOT NULL DEFAULT 1,
    sign_mode varchar(8) DEFAULT 'SEQ',
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    auth_evidence_ref varchar(255),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_signatory_order CHECK (sign_order >= 1)
);
COMMENT ON TABLE legal_signatory IS '签署方（不可删，留痕）';
COMMENT ON COLUMN legal_signatory.party_type IS '主体类型：INTERNAL/EXTERNAL';
COMMENT ON COLUMN legal_signatory.seal_mode IS '签章方式：HANDWRITE/COMPANY_SEAL/LEGAL_REP';
COMMENT ON COLUMN legal_signatory.sign_mode IS '签署模式：SEQ/PARALLEL';
COMMENT ON COLUMN legal_signatory.status IS '状态：PENDING/VIEWED/SIGNED/REJECTED/EXPIRED';
COMMENT ON COLUMN legal_signatory.signer_id_no_enc IS '身份证号密文 AES-256-GCM';
COMMENT ON COLUMN legal_signatory.signer_mobile_enc IS '手机号密文 AES-256-GCM';

CREATE TABLE legal_approval_record (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    biz_type varchar(24) NOT NULL,
    biz_id bigint NOT NULL,
    proc_inst_id varchar(64),
    task_id varchar(64),
    node_name varchar(128),
    approver_id bigint,
    action varchar(16) NOT NULL,
    comment varchar(1000),
    acted_at timestamptz,
    contract_version_id bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_approval_record IS '审批记录（不可删，留痕；记录审批版本防审A签B）';
COMMENT ON COLUMN legal_approval_record.biz_type IS '业务类型：CONTRACT/SEAL/DLP_RELEASE';
COMMENT ON COLUMN legal_approval_record.action IS '动作：APPROVE/REJECT/TRANSFER/ADD_SIGN';
