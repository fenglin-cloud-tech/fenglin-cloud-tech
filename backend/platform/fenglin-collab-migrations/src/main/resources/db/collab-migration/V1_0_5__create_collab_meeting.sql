-- 会议域：会议室、会议、纪要、行动项
-- 扩展：collab_meeting_checkin（签到，接口 API-MTG-004 需要但文档未定义表）、
--       collab_room_reservation（会议室预定，接口 API-MTG-007 返回 reservationId 但文档未定义表）。
SET search_path TO fenglin_collab, public;

CREATE TABLE collab_meeting_room (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    name varchar(128) NOT NULL,
    location varchar(256),
    capacity int NOT NULL DEFAULT 0,
    equipments varchar(32)[],
    building_id bigint,
    dept_id bigint,
    bookable smallint NOT NULL DEFAULT 1,
    status varchar(16) NOT NULL DEFAULT 'AVAILABLE',
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_room_status CHECK (status IN ('AVAILABLE', 'MAINTENANCE', 'DISABLED'))
);

CREATE TABLE collab_meeting (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    title varchar(256) NOT NULL,
    organizer_id bigint NOT NULL,
    event_id uuid,
    room_id bigint,
    room_slot tstzrange,
    start_at timestamptz NOT NULL,
    end_at timestamptz NOT NULL,
    meeting_mode varchar(16) NOT NULL DEFAULT 'ONLINE',
    join_code varchar(16),
    rtc_room_id varchar(64),
    agenda_cipher text,
    status varchar(16) NOT NULL DEFAULT 'SCHEDULED',
    record_enabled smallint NOT NULL DEFAULT 0,
    external_allowed smallint NOT NULL DEFAULT 0,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_meeting_time CHECK (end_at > start_at),
    CONSTRAINT ck_meeting_mode CHECK (meeting_mode IN ('ONLINE', 'OFFLINE', 'HYBRID')),
    CONSTRAINT ck_meeting_status CHECK (status IN ('SCHEDULED', 'ONGOING', 'ENDED', 'CANCELLED'))
);

CREATE TABLE collab_meeting_minutes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    meeting_id uuid NOT NULL REFERENCES collab_meeting (id),
    template_id bigint,
    content_cipher text,
    summary varchar(512),
    transcript_file_id uuid,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    published_at timestamptz,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_minutes_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE TABLE collab_meeting_action (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    minutes_id uuid NOT NULL,
    content varchar(512) NOT NULL,
    assignee_id bigint,
    due_at timestamptz,
    todo_id bigint,
    status varchar(16) NOT NULL DEFAULT 'OPEN',
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_action_status CHECK (status IN ('OPEN', 'DONE', 'CANCELLED'))
);

CREATE TABLE collab_meeting_checkin (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    meeting_id uuid NOT NULL REFERENCES collab_meeting (id),
    user_id bigint NOT NULL,
    method varchar(16) NOT NULL DEFAULT 'SCAN',
    checkin_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT uk_meeting_checkin UNIQUE (tenant_id, meeting_id, user_id)
);

CREATE TABLE collab_room_reservation (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    room_id bigint NOT NULL,
    meeting_id uuid,
    slot tstzrange NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'RESERVED',
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_reservation_status CHECK (status IN ('RESERVED', 'RELEASED', 'CANCELLED'))
);

-- 有 btree_gist 时启用数据库级排他约束（文档 4.4.2 ex_room_slot）；否则由服务层咨询锁 + 重叠校验保证。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist')
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ex_room_reservation_slot') THEN
        ALTER TABLE collab_room_reservation
            ADD CONSTRAINT ex_room_reservation_slot
            EXCLUDE USING gist (tenant_id WITH =, room_id WITH =, slot WITH &&)
            WHERE (deleted = 0 AND status = 'RESERVED');
    END IF;
END
$$;

COMMENT ON TABLE collab_meeting_checkin IS '扩展：文档未定义签到表，按 API-MTG-004 最小语义补齐';
COMMENT ON TABLE collab_room_reservation IS '扩展：文档未定义会议室预定表，按 API-MTG-007 最小语义补齐';
