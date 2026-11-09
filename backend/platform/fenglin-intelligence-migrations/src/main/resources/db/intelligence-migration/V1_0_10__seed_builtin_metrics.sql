-- 内置指标口径（tenant_id=0 平台级只读模板，租户可按 metric_code 覆盖）
-- 文档 V1_0_16 的字典与默认提示词归平台库 fenglin_hub（见 bootstrap 模块）；
-- 本脚本只保留 BI 域内置指标，避免与平台 AI 中台的提示词/模型配置重复。
SET search_path TO fenglin_intelligence, public;

INSERT INTO ai_metric_definition
    (id, tenant_id, code, name, category, metric_type, formula, unit, grain, dimensions,
     status, version_no, created_by)
VALUES
    (900101, 0, 'sales.gmv', '成交总额', 'sales', 'ATOMIC', 'SUM(order_paid_amount)', '元', 'DAY',
     '["region","channel","product"]'::jsonb, 'PUBLISHED', 1, 0),
    (900102, 0, 'sales.order_count', '订单量', 'sales', 'ATOMIC', 'COUNT(DISTINCT order_id)', '单', 'DAY',
     '["region","channel","product"]'::jsonb, 'PUBLISHED', 1, 0),
    (900103, 0, 'sales.avg_order_value', '客单价', 'sales', 'DERIVED', 'sales.gmv / sales.order_count', '元', 'DAY',
     '["region","channel"]'::jsonb, 'PUBLISHED', 1, 0),
    (900104, 0, 'customer.new_count', '新增客户数', 'customer', 'ATOMIC', 'COUNT(DISTINCT customer_id)', '人', 'DAY',
     '["region","source"]'::jsonb, 'PUBLISHED', 1, 0),
    (900105, 0, 'finance.gross_margin', '毛利率', 'finance', 'DERIVED', '(revenue - cost) / revenue', '比率', 'MONTH',
     '["region","product"]'::jsonb, 'PUBLISHED', 1, 0)
ON CONFLICT DO NOTHING;
