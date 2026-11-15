-- OPEN-04：开放应用审核意见与审核时间回显
-- 背景：审核接口已接收 reason，但此前未落库，开发者被拒后看不到原因。
-- 本迁移只加两个可空列，不回填历史数据；历史单据保持 NULL，前端显示为“—”。
-- 回滚：ALTER TABLE hub_open_app DROP COLUMN review_comment, DROP COLUMN reviewed_at;
SET search_path TO fenglin_hub, public;

ALTER TABLE hub_open_app
    ADD COLUMN IF NOT EXISTS review_comment varchar(500),
    ADD COLUMN IF NOT EXISTS reviewed_at   timestamptz;

COMMENT ON COLUMN hub_open_app.review_comment IS '最近一次审核意见（通过/驳回均可填写；历史数据为 NULL）';
COMMENT ON COLUMN hub_open_app.reviewed_at IS '最近一次审核时间；历史数据为 NULL';
