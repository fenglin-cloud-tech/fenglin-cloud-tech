-- 云协业务域前置数据（可重复执行）
-- 说明：
-- 1) 权限点与字典项属于平台库 fenglin_hub（IAM），不在云协独立库中重复建模；
-- 2) 全部使用 ON CONFLICT DO NOTHING，重复执行不会产生脏数据；
-- 3) collab_approval 流程定义需要 Flowable 部署，SQL 无法完成，由云协服务首次提单时
--    调用工作流中心管理接口自动部署（见 HttpWorkflowClient#deployProcess）。
SET search_path TO fenglin_hub, public;

-- ---------- 权限点 ----------
INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8501, 'biz:collab:im:chat', '即时消息收发', 'API', 'collab', 10, 0),
       (8502, 'biz:collab:im:group:manage', '会话成员管理', 'API', 'collab', 11, 0),
       (8503, 'biz:collab:doc:read', '文档查看', 'API', 'collab', 20, 0),
       (8504, 'biz:collab:doc:edit', '文档编辑', 'API', 'collab', 21, 0),
       (8505, 'biz:collab:doc:comment', '文档评论', 'API', 'collab', 22, 0),
       (8506, 'biz:collab:doc:manage', '文档授权与回滚', 'API', 'collab', 23, 0),
       (8507, 'biz:collab:calendar:read', '日程查看与忙闲', 'API', 'collab', 30, 0),
       (8508, 'biz:collab:calendar:manage', '日程维护', 'API', 'collab', 31, 0),
       (8509, 'biz:collab:meeting:join', '会议查看与签到', 'API', 'collab', 40, 0),
       (8510, 'biz:collab:meeting:manage', '会议组织与纪要', 'API', 'collab', 41, 0),
       (8511, 'biz:collab:okr:read', 'OKR 查看', 'API', 'collab', 50, 0),
       (8512, 'biz:collab:okr:manage', 'OKR 维护与对齐', 'API', 'collab', 51, 0),
       (8513, 'biz:collab:okr:progress', 'KR 进度更新', 'API', 'collab', 52, 0),
       (8514, 'biz:collab:approval:read', '审批查看', 'API', 'collab', 60, 0),
       (8515, 'biz:collab:approval:submit', '审批发起', 'API', 'collab', 61, 0),
       (8516, 'biz:collab:approval:handle', '审批处理', 'API', 'collab', 62, 0),
       (8517, 'biz:collab:workbench:personal', '个人工作台', 'API', 'collab', 70, 0),
       (8518, 'biz:collab:ona:self', '个人协作画像', 'API', 'collab', 80, 0),
       (8519, 'tenant:collab:im:sensitive:manage', '敏感词策略管理', 'API', 'collab', 110, 0),
       (8520, 'tenant:collab:im:govern', '会话审计治理', 'API', 'collab', 111, 0),
       (8521, 'tenant:collab:doc:policy', '文档密级策略', 'API', 'collab', 120, 0),
       (8522, 'tenant:collab:calendar:public:manage', '公共日历管理', 'API', 'collab', 130, 0),
       (8523, 'tenant:collab:meeting:room:manage', '会议室资源管理', 'API', 'collab', 140, 0),
       (8524, 'tenant:collab:okr:period:manage', 'OKR 周期配置', 'API', 'collab', 150, 0),
       (8525, 'tenant:collab:approval:template:manage', '审批模板管理', 'API', 'collab', 160, 0),
       (8526, 'tenant:collab:workbench:template:manage', '工作台组件管理', 'API', 'collab', 170, 0),
       (8527, 'tenant:collab:ona:dept', '部门协作网络查看', 'API', 'collab', 180, 0),
       (8528, 'tenant:collab:ona:tenant', '全租户协作网络查看', 'API', 'collab', 181, 0),
       (8529, 'tenant:collab:ona:config', 'ONA 度量配置', 'API', 'collab', 182, 0)
ON CONFLICT (code) DO NOTHING;

-- ---------- 字典类型（全局） ----------
INSERT INTO hub_dict_type (id, code, name, scope, status, deleted)
VALUES (8601, 'collab_conv_type', '会话类型', 'PLATFORM', 1, 0),
       (8602, 'collab_msg_type', '消息类型', 'PLATFORM', 1, 0),
       (8603, 'collab_doc_secret_level', '文档密级', 'PLATFORM', 1, 0),
       (8604, 'collab_doc_permission', '文档协作者权限', 'PLATFORM', 1, 0),
       (8605, 'collab_cal_type', '日历类型', 'PLATFORM', 1, 0),
       (8606, 'collab_event_status', '日程状态', 'PLATFORM', 1, 0),
       (8607, 'collab_meeting_mode', '会议形式', 'PLATFORM', 1, 0),
       (8608, 'collab_meeting_status', '会议状态', 'PLATFORM', 1, 0),
       (8609, 'collab_room_equipment', '会议室设备', 'PLATFORM', 1, 0),
       (8610, 'collab_okr_obj_type', 'OKR 目标类型', 'PLATFORM', 1, 0),
       (8611, 'collab_okr_period_status', 'OKR 周期状态', 'PLATFORM', 1, 0),
       (8612, 'collab_approval_status', '审批状态', 'PLATFORM', 1, 0),
       (8613, 'collab_ona_period_type', 'ONA 快照周期', 'PLATFORM', 1, 0)
ON CONFLICT (code) DO NOTHING;

-- ---------- 字典项（tenant_id=0 平台级，所有租户只读可见） ----------
INSERT INTO hub_dict_item (id, tenant_id, dict_code, item_value, item_label, sort, status, deleted)
VALUES (8701, 0, 'collab_conv_type', 'SINGLE', '单聊', 1, 1, 0),
       (8702, 0, 'collab_conv_type', 'GROUP', '群聊', 2, 1, 0),
       (8703, 0, 'collab_conv_type', 'TOPIC', '话题群', 3, 1, 0),
       (8711, 0, 'collab_msg_type', 'TEXT', '文本', 1, 1, 0),
       (8712, 0, 'collab_msg_type', 'RICH', '富文本', 2, 1, 0),
       (8713, 0, 'collab_msg_type', 'IMAGE', '图片', 3, 1, 0),
       (8714, 0, 'collab_msg_type', 'FILE', '文件', 4, 1, 0),
       (8715, 0, 'collab_msg_type', 'AUDIO', '音频', 5, 1, 0),
       (8716, 0, 'collab_msg_type', 'VIDEO', '视频', 6, 1, 0),
       (8717, 0, 'collab_msg_type', 'CARD', '卡片', 7, 1, 0),
       (8718, 0, 'collab_msg_type', 'SYSTEM', '系统消息', 8, 1, 0),
       (8721, 0, 'collab_doc_secret_level', 'PUBLIC', '公开', 1, 1, 0),
       (8722, 0, 'collab_doc_secret_level', 'INTERNAL', '内部', 2, 1, 0),
       (8723, 0, 'collab_doc_secret_level', 'CONFIDENTIAL', '机密', 3, 1, 0),
       (8731, 0, 'collab_doc_permission', 'READ', '只读', 1, 1, 0),
       (8732, 0, 'collab_doc_permission', 'COMMENT', '评论', 2, 1, 0),
       (8733, 0, 'collab_doc_permission', 'EDIT', '编辑', 3, 1, 0),
       (8734, 0, 'collab_doc_permission', 'MANAGE', '管理', 4, 1, 0),
       (8741, 0, 'collab_cal_type', 'PERSONAL', '个人日历', 1, 1, 0),
       (8742, 0, 'collab_cal_type', 'TEAM', '团队日历', 2, 1, 0),
       (8743, 0, 'collab_cal_type', 'PUBLIC', '公共日历', 3, 1, 0),
       (8744, 0, 'collab_cal_type', 'HOLIDAY', '假期日历', 4, 1, 0),
       (8751, 0, 'collab_event_status', 'CONFIRMED', '已确认', 1, 1, 0),
       (8752, 0, 'collab_event_status', 'CANCELLED', '已取消', 2, 1, 0),
       (8761, 0, 'collab_meeting_mode', 'ONLINE', '线上', 1, 1, 0),
       (8762, 0, 'collab_meeting_mode', 'OFFLINE', '线下', 2, 1, 0),
       (8763, 0, 'collab_meeting_mode', 'HYBRID', '混合', 3, 1, 0),
       (8771, 0, 'collab_meeting_status', 'SCHEDULED', '已预约', 1, 1, 0),
       (8772, 0, 'collab_meeting_status', 'ONGOING', '进行中', 2, 1, 0),
       (8773, 0, 'collab_meeting_status', 'ENDED', '已结束', 3, 1, 0),
       (8774, 0, 'collab_meeting_status', 'CANCELLED', '已取消', 4, 1, 0),
       (8781, 0, 'collab_room_equipment', 'VIDEO', '视频会议', 1, 1, 0),
       (8782, 0, 'collab_room_equipment', 'WHITEBOARD', '白板', 2, 1, 0),
       (8783, 0, 'collab_room_equipment', 'PROJECTOR', '投影', 3, 1, 0),
       (8784, 0, 'collab_room_equipment', 'TV', '大屏', 4, 1, 0),
       (8791, 0, 'collab_okr_obj_type', 'COMPANY', '公司', 1, 1, 0),
       (8792, 0, 'collab_okr_obj_type', 'DEPT', '部门', 2, 1, 0),
       (8793, 0, 'collab_okr_obj_type', 'TEAM', '团队', 3, 1, 0),
       (8794, 0, 'collab_okr_obj_type', 'PERSONAL', '个人', 4, 1, 0),
       (8801, 0, 'collab_okr_period_status', 'OPEN', '进行中', 1, 1, 0),
       (8802, 0, 'collab_okr_period_status', 'CLOSED', '已关闭', 2, 1, 0),
       (8811, 0, 'collab_approval_status', 'DRAFT', '草稿', 1, 1, 0),
       (8812, 0, 'collab_approval_status', 'RUNNING', '审批中', 2, 1, 0),
       (8813, 0, 'collab_approval_status', 'APPROVED', '已通过', 3, 1, 0),
       (8814, 0, 'collab_approval_status', 'REJECTED', '已驳回', 4, 1, 0),
       (8815, 0, 'collab_approval_status', 'CANCELLED', '已撤销', 5, 1, 0),
       (8821, 0, 'collab_ona_period_type', 'WEEK', '周', 1, 1, 0),
       (8822, 0, 'collab_ona_period_type', 'MONTH', '月', 2, 1, 0),
       (8823, 0, 'collab_ona_period_type', 'QUARTER', '季度', 3, 1, 0)
ON CONFLICT (tenant_id, dict_code, item_value) DO NOTHING;
