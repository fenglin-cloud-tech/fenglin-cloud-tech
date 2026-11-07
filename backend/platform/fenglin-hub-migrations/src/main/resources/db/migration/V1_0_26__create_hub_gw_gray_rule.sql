-- P0 文档缺口补表（C-12）：网关灰度规则。平台级配置。待文档同步。

SET search_path TO fenglin_hub, public;

CREATE TABLE hub_gw_gray_rule (
    id           bigint       PRIMARY KEY,
    tenant_id    bigint       NOT NULL DEFAULT 0,
    route_id     varchar(64)  NOT NULL,
    match_header varchar(64),
    match_value  varchar(256),
    weight       integer      NOT NULL DEFAULT 0,
    enabled      smallint     NOT NULL DEFAULT 1,
    created_by   bigint,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   bigint,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    deleted      smallint     NOT NULL DEFAULT 0,
    version      integer      NOT NULL DEFAULT 0
);
CREATE INDEX idx_hub_gw_gray_rule_route ON hub_gw_gray_rule (route_id, enabled);
CREATE TRIGGER trg_hub_gw_gray_rule_updated_at BEFORE UPDATE ON hub_gw_gray_rule
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_gw_gray_rule TO fl_app, fl_platform_role;
COMMENT ON TABLE hub_gw_gray_rule IS '网关灰度规则（C-12 待文档同步）';
