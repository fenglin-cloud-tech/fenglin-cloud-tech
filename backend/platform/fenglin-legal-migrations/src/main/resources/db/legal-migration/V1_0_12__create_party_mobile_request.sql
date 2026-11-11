-- LEG-03：相对方联系人手机号明文查看申请/审批
-- 背景：legal_contract_party.contact_mobile_enc 仅回显后四位，此前无明文查看通道；
--       本表记录“申请-审批-放行”链路，审批通过后申请人方可解密查看，每次查看写审计。
SET search_path TO fenglin_legal, public;

CREATE TABLE IF NOT EXISTS legal_party_mobile_request (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    party_id bigint NOT NULL,
    reason varchar(500) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    applied_by bigint NOT NULL,
    decided_by bigint,
    decided_at timestamptz,
    decision_comment varchar(500),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_party_mobile_request IS '相对方手机号明文查看申请（申请-审批-放行）';
COMMENT ON COLUMN legal_party_mobile_request.status IS '状态：PENDING/APPROVED/REJECTED';
COMMENT ON COLUMN legal_party_mobile_request.reason IS '申请理由，必填';
COMMENT ON COLUMN legal_party_mobile_request.decision_comment IS '审批意见，驳回时必填';

-- 同一申请人对同一相对方只允许一条待审批申请
CREATE UNIQUE INDEX IF NOT EXISTS uk_legal_party_mobile_request_pending
    ON legal_party_mobile_request (tenant_id, party_id, applied_by)
    WHERE status = 'PENDING' AND deleted = 0;
CREATE INDEX IF NOT EXISTS idx_legal_party_mobile_request_applicant
    ON legal_party_mobile_request (tenant_id, applied_by, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_legal_party_mobile_request_review
    ON legal_party_mobile_request (tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_legal_party_mobile_request_party
    ON legal_party_mobile_request (tenant_id, party_id, applied_by, status);

CREATE TRIGGER trg_legal_party_mobile_request_updated
    BEFORE UPDATE ON legal_party_mobile_request
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE legal_party_mobile_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE legal_party_mobile_request FORCE ROW LEVEL SECURITY;
CREATE POLICY p_legal_party_mobile_request_tenant ON legal_party_mobile_request
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

GRANT SELECT, INSERT, UPDATE, DELETE ON legal_party_mobile_request TO fl_legal_app;
