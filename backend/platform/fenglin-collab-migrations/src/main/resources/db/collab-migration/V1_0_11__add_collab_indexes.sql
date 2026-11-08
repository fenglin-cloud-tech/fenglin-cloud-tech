-- 业务索引：按 02_丰麟云协_03 第 5 节建立查询索引
SET search_path TO fenglin_collab, public;

CREATE INDEX IF NOT EXISTS idx_conv_tenant_status ON collab_conversation (tenant_id, status, last_message_at DESC);
CREATE INDEX IF NOT EXISTS idx_conv_member_user ON collab_conversation_member (tenant_id, member_id, conversation_id);
CREATE INDEX IF NOT EXISTS idx_msg_conv_seq ON collab_message (tenant_id, conversation_id, seq DESC);
CREATE INDEX IF NOT EXISTS idx_msg_sender ON collab_message (tenant_id, sender_id, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_msg_read_msg ON collab_message_read (tenant_id, message_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_doc_owner ON collab_document (tenant_id, owner_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_doc_link ON collab_document (link_token) WHERE link_token IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_doc_collab_subject ON collab_document_collaborator (tenant_id, subject_type, subject_id);
CREATE INDEX IF NOT EXISTS idx_event_cal_time ON collab_event (tenant_id, calendar_id, start_at, end_at);
CREATE INDEX IF NOT EXISTS idx_event_att_user ON collab_event_attendee (tenant_id, attendee_id, event_id);
CREATE INDEX IF NOT EXISTS idx_meeting_org_time ON collab_meeting (tenant_id, organizer_id, start_at DESC);
CREATE INDEX IF NOT EXISTS idx_meeting_reservation ON collab_room_reservation (tenant_id, room_id, slot);
CREATE INDEX IF NOT EXISTS idx_action_assignee ON collab_meeting_action (tenant_id, assignee_id, status);
CREATE INDEX IF NOT EXISTS idx_kr_objective ON collab_okr_key_result (tenant_id, objective_id);
CREATE INDEX IF NOT EXISTS idx_okr_owner_period ON collab_okr_objective (tenant_id, owner_id, period_id);
CREATE INDEX IF NOT EXISTS idx_apv_initiator ON collab_approval_instance (tenant_id, initiator_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_apv_task_assignee ON collab_approval_task (tenant_id, assignee_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notif_receiver ON collab_notification (tenant_id, receiver_id, read_flag, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_todo_owner_status ON collab_todo (tenant_id, owner_id, status, due_at);
CREATE INDEX IF NOT EXISTS idx_ona_edge_graph ON collab_ona_edge (tenant_id, graph_id, source_id);
CREATE INDEX IF NOT EXISTS idx_ona_metric_node ON collab_ona_metric (tenant_id, snapshot_id, node_id);
CREATE INDEX IF NOT EXISTS idx_file_biz ON collab_file (tenant_id, biz_type, biz_id);

-- 部分唯一索引：逻辑删除后允许重建同名实体
CREATE UNIQUE INDEX IF NOT EXISTS uk_conv_member ON collab_conversation_member (tenant_id, conversation_id, member_id)
    WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_doc_collab ON collab_document_collaborator
    (tenant_id, document_id, subject_type, subject_id) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_apv_tpl_code ON collab_approval_template (tenant_id, code) WHERE deleted = 0;
