-- COL-01c：审批抄送表（单表独立迁移）
-- 关系：一条审批实例可抄送多个用户；同一实例对同一用户只保留一条有效记录。
SET search_path TO fenglin_collab, public;

CREATE TABLE collab_approval_cc (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    instance_id uuid NOT NULL REFERENCES collab_approval_instance (id),
    cc_user_id bigint NOT NULL,
    cc_type varchar(16) NOT NULL DEFAULT 'USER',
    read_at timestamptz,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_apv_cc_type CHECK (cc_type IN ('USER', 'ROLE', 'DEPT'))
);

CREATE UNIQUE INDEX uk_apv_cc ON collab_approval_cc (tenant_id, instance_id, cc_user_id)
    WHERE deleted = 0;
CREATE INDEX idx_apv_cc_user ON collab_approval_cc (tenant_id, cc_user_id, created_at DESC)
    WHERE deleted = 0;
CREATE INDEX idx_apv_cc_instance ON collab_approval_cc (tenant_id, instance_id)
    WHERE deleted = 0;

ALTER TABLE collab_approval_cc ENABLE ROW LEVEL SECURITY;
ALTER TABLE collab_approval_cc FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_collab_approval_cc_tenant ON collab_approval_cc;
CREATE POLICY p_collab_approval_cc_tenant ON collab_approval_cc
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

DROP TRIGGER IF EXISTS trg_collab_approval_cc_updated ON collab_approval_cc;
CREATE TRIGGER trg_collab_approval_cc_updated BEFORE UPDATE ON collab_approval_cc
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

GRANT SELECT, INSERT, UPDATE, DELETE ON collab_approval_cc TO fl_collab_app;

COMMENT ON TABLE collab_approval_cc IS '审批抄送（COL-01c）：发起时写入抄送人，仅抄送人本人可见';
COMMENT ON COLUMN collab_approval_cc.read_at IS '首次查看时间，空表示未读';
