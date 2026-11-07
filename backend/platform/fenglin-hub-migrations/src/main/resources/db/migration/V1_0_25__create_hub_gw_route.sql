-- P0 文档缺口补表（C-12）：网关路由。平台级配置。待文档同步。

SET search_path TO fenglin_hub, public;

CREATE TABLE hub_gw_route (
    id         bigint       PRIMARY KEY,
    tenant_id  bigint       NOT NULL DEFAULT 0,
    route_id   varchar(64)  NOT NULL,
    uri        varchar(512) NOT NULL,
    predicates jsonb,
    filters    jsonb,
    sort_order integer      NOT NULL DEFAULT 0,
    enabled    smallint     NOT NULL DEFAULT 1,
    created_by bigint,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz  NOT NULL DEFAULT now(),
    deleted    smallint     NOT NULL DEFAULT 0,
    version    integer      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_hub_gw_route
    ON hub_gw_route (route_id) WHERE deleted = 0;
CREATE INDEX idx_hub_gw_route_enabled ON hub_gw_route (enabled, sort_order);
CREATE TRIGGER trg_hub_gw_route_updated_at BEFORE UPDATE ON hub_gw_route
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_gw_route TO fl_app, fl_platform_role;
COMMENT ON TABLE hub_gw_route IS '网关路由配置（C-12 待文档同步）';
