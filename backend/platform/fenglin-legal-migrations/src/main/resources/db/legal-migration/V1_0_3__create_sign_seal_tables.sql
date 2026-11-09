-- G1：印章/证书、用印申请、用印记录、签署记录、存证记录
SET search_path TO fenglin_legal, public;

CREATE TABLE legal_seal (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    seal_no varchar(32) NOT NULL,
    name varchar(128) NOT NULL,
    seal_type varchar(16) NOT NULL,
    subject_name varchar(200),
    image_ref varchar(255),
    cert_serial varchar(128),
    cert_algorithm varchar(16),
    cert_not_before timestamptz,
    cert_not_after timestamptz,
    provider_code varchar(32),
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    keeper_id bigint,
    remark text,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_seal IS '印章/证书（私钥由 KMS 托管，不落库）';
COMMENT ON COLUMN legal_seal.seal_type IS '类型：ELECTRONIC/PHYSICAL';
COMMENT ON COLUMN legal_seal.status IS '状态：ACTIVE/DISABLED/REVOKED/EXPIRED';
COMMENT ON COLUMN legal_seal.cert_algorithm IS '证书算法：SM2/RSA';

CREATE TABLE legal_seal_application (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    apply_no varchar(32) NOT NULL,
    contract_id bigint NOT NULL,
    contract_version_id bigint NOT NULL,
    seal_id bigint NOT NULL,
    seal_type varchar(16) NOT NULL,
    copies int NOT NULL DEFAULT 1,
    purpose varchar(255),
    position_desc varchar(255),
    applicant_id bigint NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    approval_proc_id varchar(64),
    expected_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_seal_application IS '用印申请';
COMMENT ON COLUMN legal_seal_application.status IS '状态：PENDING/APPROVED/REJECTED/EXECUTED/CANCELED';

CREATE TABLE legal_seal_record (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    record_no varchar(32) NOT NULL,
    seal_application_id bigint NOT NULL,
    contract_id bigint NOT NULL,
    seal_id bigint NOT NULL,
    contract_version_id bigint NOT NULL,
    version_hash varchar(64) NOT NULL,
    copies int NOT NULL DEFAULT 1,
    image_ref varchar(255),
    executor_id bigint NOT NULL,
    executed_at timestamptz NOT NULL,
    chain_id varchar(64),
    seq_no bigint,
    prev_hash varchar(64),
    block_hash varchar(64) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_seal_record_info CHECK (image_ref IS NOT NULL OR seal_id IS NOT NULL)
);
COMMENT ON TABLE legal_seal_record IS '用印记录（不可删；哈希链 + WORM）';
COMMENT ON COLUMN legal_seal_record.version_hash IS '用印时文档哈希（防审A盖B）';

CREATE TABLE legal_sign_record (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    sign_task_no varchar(32) NOT NULL,
    contract_id bigint NOT NULL,
    contract_version_id bigint NOT NULL,
    signatory_id bigint NOT NULL,
    provider_code varchar(32),
    provider_task_id varchar(128),
    real_name_verified boolean DEFAULT false,
    will_confirmed boolean DEFAULT false,
    auth_mode varchar(24),
    tsa_token varchar(255),
    doc_hash varchar(64) NOT NULL,
    sign_data text,
    sign_ip inet,
    sign_device varchar(255),
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    signed_at timestamptz,
    chain_id varchar(64),
    prev_hash varchar(64),
    block_hash varchar(64) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_sign_record IS '签署记录（不可删；文档要求按 tenant_id + signed_at 分区，此处普通表+索引）';
COMMENT ON COLUMN legal_sign_record.auth_mode IS '认证方式：SMS/FACE/CA_CERT/MFA';
COMMENT ON COLUMN legal_sign_record.status IS '状态：PENDING/SIGNED/REJECTED/EXPIRED/REVOKED';

CREATE TABLE legal_evidence (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    evidence_no varchar(32) NOT NULL,
    contract_id bigint NOT NULL,
    sign_record_id bigint,
    evidence_type varchar(24) NOT NULL,
    content_hash varchar(64) NOT NULL,
    hash_algorithm varchar(16) DEFAULT 'SHA256',
    worm_ref varchar(255),
    provider_evidence_id varchar(128),
    cert_ref varchar(255),
    chain_id varchar(64),
    prev_hash varchar(64),
    block_hash varchar(64) NOT NULL,
    verified boolean DEFAULT false,
    verified_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_evidence_algo CHECK (hash_algorithm IN ('SHA256', 'SM3'))
);
COMMENT ON TABLE legal_evidence IS '存证记录（不可删；长期留存）';
COMMENT ON COLUMN legal_evidence.evidence_type IS '类型：CONTRACT_DOC/SIGN_DATA/AUTH_RECORD/TIMESTAMP/SEAL';
