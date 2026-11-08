-- 全表启用 RLS、更新时间触发器与运行角色授权
-- 与平台库一致：应用以 fl_collab_app 角色连接，事务内 SET LOCAL app.tenant_id 后才能读写
SET search_path TO fenglin_collab, public;

DO $$
DECLARE
    t text;
    tables text[] := ARRAY[
        'collab_conversation', 'collab_conversation_member', 'collab_message', 'collab_message_read',
        'collab_message_reaction', 'collab_document', 'collab_document_version', 'collab_document_collaborator',
        'collab_document_comment', 'collab_calendar', 'collab_event', 'collab_event_attendee',
        'collab_meeting_room', 'collab_meeting', 'collab_meeting_minutes', 'collab_meeting_action',
        'collab_meeting_checkin', 'collab_room_reservation', 'collab_okr_period', 'collab_okr_objective',
        'collab_okr_key_result', 'collab_okr_alignment', 'collab_okr_progress', 'collab_approval_template',
        'collab_approval_instance', 'collab_approval_task', 'collab_workbench_widget', 'collab_workbench_layout',
        'collab_notification', 'collab_todo', 'collab_ona_graph', 'collab_ona_edge', 'collab_ona_metric',
        'collab_ona_snapshot', 'collab_ona_config', 'collab_file', 'collab_setting'
    ];
BEGIN
    FOREACH t IN ARRAY tables
    LOOP
        EXECUTE format('ALTER TABLE fenglin_collab.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE fenglin_collab.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS p_%s_tenant ON fenglin_collab.%I', t, t);
        EXECUTE format(
            'CREATE POLICY p_%s_tenant ON fenglin_collab.%I USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id())',
            t, t);

        EXECUTE format('DROP TRIGGER IF EXISTS trg_%s_updated ON fenglin_collab.%I', t, t);
        EXECUTE format(
            'CREATE TRIGGER trg_%s_updated BEFORE UPDATE ON fenglin_collab.%I FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
            t, t);
    END LOOP;
END
$$;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA fenglin_collab TO fl_collab_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA fenglin_collab TO fl_collab_app;

COMMENT ON TABLE fenglin_collab.collab_conversation IS 'IM 会话（IM-R01 成员上限 2000）';
COMMENT ON TABLE fenglin_collab.collab_document IS '协同文档（正文密文，机密文档禁外链）';
COMMENT ON TABLE fenglin_collab.collab_meeting IS '会议（会议室占用时段 room_slot）';
COMMENT ON TABLE fenglin_collab.collab_approval_instance IS '审批实例（幂等键唯一）';
COMMENT ON TABLE fenglin_collab.collab_todo IS '统一待办（来源唯一约束去重）';
