-- 云研内置度量口径（tenant_id=0 平台级，租户可按 metric_code 覆盖）
-- 文档 §8 V1_0_18 seed_metric_config；本模块编为 V1_1_2。
SET search_path TO fenglin_devops, public;

INSERT INTO devops_metric_config
    (id, tenant_id, metric_code, metric_name, metric_group, formula, caliber_version, weight, target_value, unit,
     status, created_by)
VALUES
    (900001, 0, 'dora.deploy_freq', '部署频率', 'dora', '生产部署次数 / 统计周期天数', 'v1', 0.25, 1.0, '次/天', 'enabled', 0),
    (900002, 0, 'dora.lead_time_hours', '变更前置时间', 'dora', '变更关联最近提交时间到生产部署完成时间的中位数（小时）', 'v1', 0.25, 24.0, '小时', 'enabled', 0),
    (900003, 0, 'dora.change_failure_rate', '变更失败率', 'dora', '导致回滚或热修复的生产部署数 / 生产部署总数', 'v1', 0.25, 0.15, '比率', 'enabled', 0),
    (900004, 0, 'dora.mttr_hours', '故障恢复时间', 'dora', '生产故障从发生到恢复的中位数（小时）', 'v1', 0.25, 4.0, '小时', 'enabled', 0),
    (900005, 0, 'delivery.lead_time_hours', '需求交付周期', 'delivery', '需求交付时间 - 创建时间的中位数（小时）', 'v1', 0.30, 72.0, '小时', 'enabled', 0),
    (900006, 0, 'delivery.throughput', '需求交付吞吐', 'delivery', '统计周期内交付需求数', 'v1', 0.20, 20.0, '个', 'enabled', 0),
    (900007, 0, 'quality.defect_escape_rate', '缺陷逃逸率', 'quality', '生产发现缺陷数 / 缺陷总数', 'v1', 0.25, 0.05, '比率', 'enabled', 0),
    (900008, 0, 'quality.defect_density', '缺陷密度', 'quality', '缺陷数 / 交付需求数', 'v1', 0.25, 0.5, '个/需求', 'enabled', 0),
    (900009, 0, 'contribution.effective_commit_rate', '有效提交率', 'contribution', '关联工作项提交数 / 非排除提交数', 'v1', 0.25, 0.8, '比率', 'enabled', 0),
    (900010, 0, 'contribution.code_lines', '代码贡献行数', 'contribution', '新增行数 - 删除行数', 'v1', 0.25, 1000.0, '行', 'enabled', 0)
ON CONFLICT DO NOTHING;
