// 列表列宽规约（可复用）：
// 1) 长数字主键（≥15 位纯数字，用于身份识别、跨系统核对、人工检索）：必须整列显示，
//    固定宽度 168px（19 位数字 + 单元格内边距），并加 show-overflow-tooltip 兜底；不允许压缩。
// 2) 带前缀业务编号（合同号、报价单号等长度可变）：固定 160px 起，允许省略号 + tooltip。
// 3) 名称/描述类：使用 min-width + show-overflow-tooltip，允许省略。
// 4) 宽度预算冲突时，优先压缩 Tier 2、时间列与操作列，Tier 1 主键列不压。
export const ID_COLUMN_WIDTH = 168
// 门户舒适档表格正文 13px，19 位数字比紧凑档宽约 3px，整列显示需要 176px。
export const ID_COLUMN_WIDTH_COMFORT = 176
export const CODE_COLUMN_WIDTH = 160
