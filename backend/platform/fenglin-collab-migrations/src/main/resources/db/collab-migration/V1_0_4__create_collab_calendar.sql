-- 日历域：日历、日程、日程参与人
-- 偏差标注：文档建议的个人日历 EXCLUDE 约束未在首版启用（RRULE 未展开为实例，排他约束会误伤重复日程），
--           冲突检测改为应用层按（用户, 时段）查询；重复日程首版仅存储 RRULE，不展开实例（G-11 同类）。
SET search_path TO fenglin_collab, public;

CREATE TABLE collab_calendar (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    name varchar(128) NOT NULL,
    cal_type varchar(16) NOT NULL DEFAULT 'PERSONAL',
    owner_id bigint,
    dept_id bigint,
    color varchar(16),
    visible_scope varchar(16) NOT NULL DEFAULT 'DEPT',
    subscribe_url varchar(512),
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_cal_type CHECK (cal_type IN ('PERSONAL', 'TEAM', 'PUBLIC', 'HOLIDAY')),
    CONSTRAINT ck_cal_scope CHECK (visible_scope IN ('PRIVATE', 'DEPT', 'ALL'))
);

CREATE TABLE collab_event (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    calendar_id uuid NOT NULL REFERENCES collab_calendar (id),
    title varchar(256) NOT NULL,
    description_cipher text,
    location varchar(256),
    start_at timestamptz NOT NULL,
    end_at timestamptz NOT NULL,
    all_day smallint NOT NULL DEFAULT 0,
    rrule varchar(256),
    recurrence_id uuid,
    reminder_minutes int[],
    meeting_id uuid,
    status varchar(16) NOT NULL DEFAULT 'CONFIRMED',
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_event_time CHECK (end_at > start_at),
    CONSTRAINT ck_event_status CHECK (status IN ('TENTATIVE', 'CONFIRMED', 'CANCELLED'))
);

CREATE TABLE collab_event_attendee (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    event_id uuid NOT NULL REFERENCES collab_event (id),
    attendee_id bigint NOT NULL,
    response varchar(16) NOT NULL DEFAULT 'PENDING',
    is_organizer smallint NOT NULL DEFAULT 0,
    external_email varchar(128),
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_event_attendee_response CHECK (response IN ('PENDING', 'ACCEPTED', 'DECLINED', 'TENTATIVE')),
    CONSTRAINT uk_event_attendee UNIQUE (tenant_id, event_id, attendee_id)
);

COMMENT ON COLUMN collab_event.rrule IS '首版仅存储 RRULE，不展开实例（文档缺口 G-11 兜底）';
