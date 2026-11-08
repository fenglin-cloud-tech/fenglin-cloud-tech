-- P2 工作流中心：Flowable 7.0.1 流程引擎表结构。
-- 来源：7.0.1 引擎在 PostgreSQL 15 上自建 schema 后经 pg_dump 导出（Flyway 统一管控 DDL）。
-- 运行时 flowable.database-schema-update=false、flowable.idm.enabled=false、flowable.eventregistry.enabled=false。
-- 说明：Flowable 引擎表不启用 RLS，租户隔离由应用传入并约束 tenant_id。

SET search_path TO fenglin_hub, public;
CREATE TABLE act_evt_log (
    log_nr_ integer NOT NULL,
    type_ character varying(64),
    proc_def_id_ character varying(64),
    proc_inst_id_ character varying(64),
    execution_id_ character varying(64),
    task_id_ character varying(64),
    time_stamp_ timestamp without time zone NOT NULL,
    user_id_ character varying(255),
    data_ bytea,
    lock_owner_ character varying(255),
    lock_time_ timestamp without time zone,
    is_processed_ smallint DEFAULT 0
);



CREATE SEQUENCE act_evt_log_log_nr__seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE act_evt_log_log_nr__seq OWNED BY act_evt_log.log_nr_;



CREATE TABLE act_ge_bytearray (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    name_ character varying(255),
    deployment_id_ character varying(64),
    bytes_ bytea,
    generated_ boolean
);



CREATE TABLE act_ge_property (
    name_ character varying(64) NOT NULL,
    value_ character varying(300),
    rev_ integer
);



CREATE TABLE act_hi_actinst (
    id_ character varying(64) NOT NULL,
    rev_ integer DEFAULT 1,
    proc_def_id_ character varying(64) NOT NULL,
    proc_inst_id_ character varying(64) NOT NULL,
    execution_id_ character varying(64) NOT NULL,
    act_id_ character varying(255) NOT NULL,
    task_id_ character varying(64),
    call_proc_inst_id_ character varying(64),
    act_name_ character varying(255),
    act_type_ character varying(255) NOT NULL,
    assignee_ character varying(255),
    start_time_ timestamp without time zone NOT NULL,
    end_time_ timestamp without time zone,
    transaction_order_ integer,
    duration_ bigint,
    delete_reason_ character varying(4000),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);



CREATE TABLE act_hi_attachment (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    user_id_ character varying(255),
    name_ character varying(255),
    description_ character varying(4000),
    type_ character varying(255),
    task_id_ character varying(64),
    proc_inst_id_ character varying(64),
    url_ character varying(4000),
    content_id_ character varying(64),
    time_ timestamp without time zone
);



CREATE TABLE act_hi_comment (
    id_ character varying(64) NOT NULL,
    type_ character varying(255),
    time_ timestamp without time zone NOT NULL,
    user_id_ character varying(255),
    task_id_ character varying(64),
    proc_inst_id_ character varying(64),
    action_ character varying(255),
    message_ character varying(4000),
    full_msg_ bytea
);



CREATE TABLE act_hi_detail (
    id_ character varying(64) NOT NULL,
    type_ character varying(255) NOT NULL,
    proc_inst_id_ character varying(64),
    execution_id_ character varying(64),
    task_id_ character varying(64),
    act_inst_id_ character varying(64),
    name_ character varying(255) NOT NULL,
    var_type_ character varying(64),
    rev_ integer,
    time_ timestamp without time zone NOT NULL,
    bytearray_id_ character varying(64),
    double_ double precision,
    long_ bigint,
    text_ character varying(4000),
    text2_ character varying(4000)
);



CREATE TABLE act_hi_entitylink (
    id_ character varying(64) NOT NULL,
    link_type_ character varying(255),
    create_time_ timestamp without time zone,
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    parent_element_id_ character varying(255),
    ref_scope_id_ character varying(255),
    ref_scope_type_ character varying(255),
    ref_scope_definition_id_ character varying(255),
    root_scope_id_ character varying(255),
    root_scope_type_ character varying(255),
    hierarchy_type_ character varying(255)
);



CREATE TABLE act_hi_identitylink (
    id_ character varying(64) NOT NULL,
    group_id_ character varying(255),
    type_ character varying(255),
    user_id_ character varying(255),
    task_id_ character varying(64),
    create_time_ timestamp without time zone,
    proc_inst_id_ character varying(64),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255)
);



CREATE TABLE act_hi_procinst (
    id_ character varying(64) NOT NULL,
    rev_ integer DEFAULT 1,
    proc_inst_id_ character varying(64) NOT NULL,
    business_key_ character varying(255),
    proc_def_id_ character varying(64) NOT NULL,
    start_time_ timestamp without time zone NOT NULL,
    end_time_ timestamp without time zone,
    duration_ bigint,
    start_user_id_ character varying(255),
    start_act_id_ character varying(255),
    end_act_id_ character varying(255),
    super_process_instance_id_ character varying(64),
    delete_reason_ character varying(4000),
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    name_ character varying(255),
    callback_id_ character varying(255),
    callback_type_ character varying(255),
    reference_id_ character varying(255),
    reference_type_ character varying(255),
    propagated_stage_inst_id_ character varying(255),
    business_status_ character varying(255)
);



CREATE TABLE act_hi_taskinst (
    id_ character varying(64) NOT NULL,
    rev_ integer DEFAULT 1,
    proc_def_id_ character varying(64),
    task_def_id_ character varying(64),
    task_def_key_ character varying(255),
    proc_inst_id_ character varying(64),
    execution_id_ character varying(64),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    propagated_stage_inst_id_ character varying(255),
    state_ character varying(255),
    name_ character varying(255),
    parent_task_id_ character varying(64),
    description_ character varying(4000),
    owner_ character varying(255),
    assignee_ character varying(255),
    start_time_ timestamp without time zone NOT NULL,
    in_progress_time_ timestamp without time zone,
    in_progress_started_by_ character varying(255),
    claim_time_ timestamp without time zone,
    claimed_by_ character varying(255),
    suspended_time_ timestamp without time zone,
    suspended_by_ character varying(255),
    end_time_ timestamp without time zone,
    completed_by_ character varying(255),
    duration_ bigint,
    delete_reason_ character varying(4000),
    priority_ integer,
    in_progress_due_date_ timestamp without time zone,
    due_date_ timestamp without time zone,
    form_key_ character varying(255),
    category_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    last_updated_time_ timestamp without time zone
);



CREATE TABLE act_hi_tsk_log (
    id_ integer NOT NULL,
    type_ character varying(64),
    task_id_ character varying(64) NOT NULL,
    time_stamp_ timestamp without time zone NOT NULL,
    user_id_ character varying(255),
    data_ character varying(4000),
    execution_id_ character varying(64),
    proc_inst_id_ character varying(64),
    proc_def_id_ character varying(64),
    scope_id_ character varying(255),
    scope_definition_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);



CREATE SEQUENCE act_hi_tsk_log_id__seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE act_hi_tsk_log_id__seq OWNED BY act_hi_tsk_log.id_;



CREATE TABLE act_hi_varinst (
    id_ character varying(64) NOT NULL,
    rev_ integer DEFAULT 1,
    proc_inst_id_ character varying(64),
    execution_id_ character varying(64),
    task_id_ character varying(64),
    name_ character varying(255) NOT NULL,
    var_type_ character varying(100),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    bytearray_id_ character varying(64),
    double_ double precision,
    long_ bigint,
    text_ character varying(4000),
    text2_ character varying(4000),
    meta_info_ character varying(4000),
    create_time_ timestamp without time zone,
    last_updated_time_ timestamp without time zone
);



CREATE TABLE act_procdef_info (
    id_ character varying(64) NOT NULL,
    proc_def_id_ character varying(64) NOT NULL,
    rev_ integer,
    info_json_id_ character varying(64)
);



CREATE TABLE act_re_deployment (
    id_ character varying(64) NOT NULL,
    name_ character varying(255),
    category_ character varying(255),
    key_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    deploy_time_ timestamp without time zone,
    derived_from_ character varying(64),
    derived_from_root_ character varying(64),
    parent_deployment_id_ character varying(255),
    engine_version_ character varying(255)
);



CREATE TABLE act_re_model (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    name_ character varying(255),
    key_ character varying(255),
    category_ character varying(255),
    create_time_ timestamp without time zone,
    last_update_time_ timestamp without time zone,
    version_ integer,
    meta_info_ character varying(4000),
    deployment_id_ character varying(64),
    editor_source_value_id_ character varying(64),
    editor_source_extra_value_id_ character varying(64),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);



CREATE TABLE act_re_procdef (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    name_ character varying(255),
    key_ character varying(255) NOT NULL,
    version_ integer NOT NULL,
    deployment_id_ character varying(64),
    resource_name_ character varying(4000),
    dgrm_resource_name_ character varying(4000),
    description_ character varying(4000),
    has_start_form_key_ boolean,
    has_graphical_notation_ boolean,
    suspension_state_ integer,
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    derived_from_ character varying(64),
    derived_from_root_ character varying(64),
    derived_version_ integer DEFAULT 0 NOT NULL,
    engine_version_ character varying(255)
);



CREATE TABLE act_ru_actinst (
    id_ character varying(64) NOT NULL,
    rev_ integer DEFAULT 1,
    proc_def_id_ character varying(64) NOT NULL,
    proc_inst_id_ character varying(64) NOT NULL,
    execution_id_ character varying(64) NOT NULL,
    act_id_ character varying(255) NOT NULL,
    task_id_ character varying(64),
    call_proc_inst_id_ character varying(64),
    act_name_ character varying(255),
    act_type_ character varying(255) NOT NULL,
    assignee_ character varying(255),
    start_time_ timestamp without time zone NOT NULL,
    end_time_ timestamp without time zone,
    duration_ bigint,
    transaction_order_ integer,
    delete_reason_ character varying(4000),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);



CREATE TABLE act_ru_deadletter_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    type_ character varying(255) NOT NULL,
    exclusive_ boolean,
    execution_id_ character varying(64),
    process_instance_id_ character varying(64),
    proc_def_id_ character varying(64),
    element_id_ character varying(255),
    element_name_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    correlation_id_ character varying(255),
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    duedate_ timestamp without time zone,
    repeat_ character varying(255),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    create_time_ timestamp without time zone,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);



CREATE TABLE act_ru_entitylink (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    create_time_ timestamp without time zone,
    link_type_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    parent_element_id_ character varying(255),
    ref_scope_id_ character varying(255),
    ref_scope_type_ character varying(255),
    ref_scope_definition_id_ character varying(255),
    root_scope_id_ character varying(255),
    root_scope_type_ character varying(255),
    hierarchy_type_ character varying(255)
);



CREATE TABLE act_ru_event_subscr (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    event_type_ character varying(255) NOT NULL,
    event_name_ character varying(255),
    execution_id_ character varying(64),
    proc_inst_id_ character varying(64),
    activity_id_ character varying(64),
    configuration_ character varying(255),
    created_ timestamp without time zone NOT NULL,
    proc_def_id_ character varying(64),
    sub_scope_id_ character varying(64),
    scope_id_ character varying(64),
    scope_definition_id_ character varying(64),
    scope_definition_key_ character varying(255),
    scope_type_ character varying(64),
    lock_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);



CREATE TABLE act_ru_execution (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    proc_inst_id_ character varying(64),
    business_key_ character varying(255),
    parent_id_ character varying(64),
    proc_def_id_ character varying(64),
    super_exec_ character varying(64),
    root_proc_inst_id_ character varying(64),
    act_id_ character varying(255),
    is_active_ boolean,
    is_concurrent_ boolean,
    is_scope_ boolean,
    is_event_scope_ boolean,
    is_mi_root_ boolean,
    suspension_state_ integer,
    cached_ent_state_ integer,
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    name_ character varying(255),
    start_act_id_ character varying(255),
    start_time_ timestamp without time zone,
    start_user_id_ character varying(255),
    lock_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    is_count_enabled_ boolean,
    evt_subscr_count_ integer,
    task_count_ integer,
    job_count_ integer,
    timer_job_count_ integer,
    susp_job_count_ integer,
    deadletter_job_count_ integer,
    external_worker_job_count_ integer,
    var_count_ integer,
    id_link_count_ integer,
    callback_id_ character varying(255),
    callback_type_ character varying(255),
    reference_id_ character varying(255),
    reference_type_ character varying(255),
    propagated_stage_inst_id_ character varying(255),
    business_status_ character varying(255)
);



CREATE TABLE act_ru_external_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    type_ character varying(255) NOT NULL,
    lock_exp_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    exclusive_ boolean,
    execution_id_ character varying(64),
    process_instance_id_ character varying(64),
    proc_def_id_ character varying(64),
    element_id_ character varying(255),
    element_name_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    correlation_id_ character varying(255),
    retries_ integer,
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    duedate_ timestamp without time zone,
    repeat_ character varying(255),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    create_time_ timestamp without time zone,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);



CREATE TABLE act_ru_history_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    lock_exp_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    retries_ integer,
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    adv_handler_cfg_id_ character varying(64),
    create_time_ timestamp without time zone,
    scope_type_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);



CREATE TABLE act_ru_identitylink (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    group_id_ character varying(255),
    type_ character varying(255),
    user_id_ character varying(255),
    task_id_ character varying(64),
    proc_inst_id_ character varying(64),
    proc_def_id_ character varying(64),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255)
);



CREATE TABLE act_ru_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    type_ character varying(255) NOT NULL,
    lock_exp_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    exclusive_ boolean,
    execution_id_ character varying(64),
    process_instance_id_ character varying(64),
    proc_def_id_ character varying(64),
    element_id_ character varying(255),
    element_name_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    correlation_id_ character varying(255),
    retries_ integer,
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    duedate_ timestamp without time zone,
    repeat_ character varying(255),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    create_time_ timestamp without time zone,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);



CREATE TABLE act_ru_suspended_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    type_ character varying(255) NOT NULL,
    exclusive_ boolean,
    execution_id_ character varying(64),
    process_instance_id_ character varying(64),
    proc_def_id_ character varying(64),
    element_id_ character varying(255),
    element_name_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    correlation_id_ character varying(255),
    retries_ integer,
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    duedate_ timestamp without time zone,
    repeat_ character varying(255),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    create_time_ timestamp without time zone,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);



CREATE TABLE act_ru_task (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    execution_id_ character varying(64),
    proc_inst_id_ character varying(64),
    proc_def_id_ character varying(64),
    task_def_id_ character varying(64),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    propagated_stage_inst_id_ character varying(255),
    state_ character varying(255),
    name_ character varying(255),
    parent_task_id_ character varying(64),
    description_ character varying(4000),
    task_def_key_ character varying(255),
    owner_ character varying(255),
    assignee_ character varying(255),
    delegation_ character varying(64),
    priority_ integer,
    create_time_ timestamp without time zone,
    in_progress_time_ timestamp without time zone,
    in_progress_started_by_ character varying(255),
    claim_time_ timestamp without time zone,
    claimed_by_ character varying(255),
    suspended_time_ timestamp without time zone,
    suspended_by_ character varying(255),
    in_progress_due_date_ timestamp without time zone,
    due_date_ timestamp without time zone,
    category_ character varying(255),
    suspension_state_ integer,
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    form_key_ character varying(255),
    is_count_enabled_ boolean,
    var_count_ integer,
    id_link_count_ integer,
    sub_task_count_ integer
);



CREATE TABLE act_ru_timer_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    type_ character varying(255) NOT NULL,
    lock_exp_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    exclusive_ boolean,
    execution_id_ character varying(64),
    process_instance_id_ character varying(64),
    proc_def_id_ character varying(64),
    element_id_ character varying(255),
    element_name_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    correlation_id_ character varying(255),
    retries_ integer,
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    duedate_ timestamp without time zone,
    repeat_ character varying(255),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    create_time_ timestamp without time zone,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);



CREATE TABLE act_ru_variable (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    type_ character varying(255) NOT NULL,
    name_ character varying(255) NOT NULL,
    execution_id_ character varying(64),
    proc_inst_id_ character varying(64),
    task_id_ character varying(64),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    bytearray_id_ character varying(64),
    double_ double precision,
    long_ bigint,
    text_ character varying(4000),
    text2_ character varying(4000),
    meta_info_ character varying(4000)
);



CREATE TABLE flw_ru_batch (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    type_ character varying(64) NOT NULL,
    search_key_ character varying(255),
    search_key2_ character varying(255),
    create_time_ timestamp without time zone NOT NULL,
    complete_time_ timestamp without time zone,
    status_ character varying(255),
    batch_doc_id_ character varying(64),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);



CREATE TABLE flw_ru_batch_part (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    batch_id_ character varying(64),
    type_ character varying(64) NOT NULL,
    scope_id_ character varying(64),
    sub_scope_id_ character varying(64),
    scope_type_ character varying(64),
    search_key_ character varying(255),
    search_key2_ character varying(255),
    create_time_ timestamp without time zone NOT NULL,
    complete_time_ timestamp without time zone,
    status_ character varying(255),
    result_doc_id_ character varying(64),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);



ALTER TABLE ONLY act_evt_log ALTER COLUMN log_nr_ SET DEFAULT nextval('act_evt_log_log_nr__seq'::regclass);



ALTER TABLE ONLY act_hi_tsk_log ALTER COLUMN id_ SET DEFAULT nextval('act_hi_tsk_log_id__seq'::regclass);



ALTER TABLE ONLY act_evt_log
    ADD CONSTRAINT act_evt_log_pkey PRIMARY KEY (log_nr_);



ALTER TABLE ONLY act_ge_bytearray
    ADD CONSTRAINT act_ge_bytearray_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_ge_property
    ADD CONSTRAINT act_ge_property_pkey PRIMARY KEY (name_);



ALTER TABLE ONLY act_hi_actinst
    ADD CONSTRAINT act_hi_actinst_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_hi_attachment
    ADD CONSTRAINT act_hi_attachment_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_hi_comment
    ADD CONSTRAINT act_hi_comment_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_hi_detail
    ADD CONSTRAINT act_hi_detail_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_hi_entitylink
    ADD CONSTRAINT act_hi_entitylink_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_hi_identitylink
    ADD CONSTRAINT act_hi_identitylink_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_hi_procinst
    ADD CONSTRAINT act_hi_procinst_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_hi_procinst
    ADD CONSTRAINT act_hi_procinst_proc_inst_id__key UNIQUE (proc_inst_id_);



ALTER TABLE ONLY act_hi_taskinst
    ADD CONSTRAINT act_hi_taskinst_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_hi_tsk_log
    ADD CONSTRAINT act_hi_tsk_log_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_hi_varinst
    ADD CONSTRAINT act_hi_varinst_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_procdef_info
    ADD CONSTRAINT act_procdef_info_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_re_deployment
    ADD CONSTRAINT act_re_deployment_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_re_model
    ADD CONSTRAINT act_re_model_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_re_procdef
    ADD CONSTRAINT act_re_procdef_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_ru_actinst
    ADD CONSTRAINT act_ru_actinst_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_ru_deadletter_job
    ADD CONSTRAINT act_ru_deadletter_job_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_ru_entitylink
    ADD CONSTRAINT act_ru_entitylink_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_ru_event_subscr
    ADD CONSTRAINT act_ru_event_subscr_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_ru_execution
    ADD CONSTRAINT act_ru_execution_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_ru_external_job
    ADD CONSTRAINT act_ru_external_job_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_ru_history_job
    ADD CONSTRAINT act_ru_history_job_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_ru_identitylink
    ADD CONSTRAINT act_ru_identitylink_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_ru_job
    ADD CONSTRAINT act_ru_job_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_ru_suspended_job
    ADD CONSTRAINT act_ru_suspended_job_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_ru_task
    ADD CONSTRAINT act_ru_task_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_ru_timer_job
    ADD CONSTRAINT act_ru_timer_job_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_ru_variable
    ADD CONSTRAINT act_ru_variable_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY act_procdef_info
    ADD CONSTRAINT act_uniq_info_procdef UNIQUE (proc_def_id_);



ALTER TABLE ONLY act_re_procdef
    ADD CONSTRAINT act_uniq_procdef UNIQUE (key_, version_, derived_version_, tenant_id_);



ALTER TABLE ONLY flw_ru_batch_part
    ADD CONSTRAINT flw_ru_batch_part_pkey PRIMARY KEY (id_);



ALTER TABLE ONLY flw_ru_batch
    ADD CONSTRAINT flw_ru_batch_pkey PRIMARY KEY (id_);



CREATE INDEX act_idx_athrz_procedef ON act_ru_identitylink USING btree (proc_def_id_);



CREATE INDEX act_idx_bytear_depl ON act_ge_bytearray USING btree (deployment_id_);



CREATE INDEX act_idx_deadletter_job_correlation_id ON act_ru_deadletter_job USING btree (correlation_id_);



CREATE INDEX act_idx_deadletter_job_custom_values_id ON act_ru_deadletter_job USING btree (custom_values_id_);



CREATE INDEX act_idx_deadletter_job_exception_stack_id ON act_ru_deadletter_job USING btree (exception_stack_id_);



CREATE INDEX act_idx_deadletter_job_execution_id ON act_ru_deadletter_job USING btree (execution_id_);



CREATE INDEX act_idx_deadletter_job_proc_def_id ON act_ru_deadletter_job USING btree (proc_def_id_);



CREATE INDEX act_idx_deadletter_job_process_instance_id ON act_ru_deadletter_job USING btree (process_instance_id_);



CREATE INDEX act_idx_djob_scope ON act_ru_deadletter_job USING btree (scope_id_, scope_type_);



CREATE INDEX act_idx_djob_scope_def ON act_ru_deadletter_job USING btree (scope_definition_id_, scope_type_);



CREATE INDEX act_idx_djob_sub_scope ON act_ru_deadletter_job USING btree (sub_scope_id_, scope_type_);



CREATE INDEX act_idx_ejob_scope ON act_ru_external_job USING btree (scope_id_, scope_type_);



CREATE INDEX act_idx_ejob_scope_def ON act_ru_external_job USING btree (scope_definition_id_, scope_type_);



CREATE INDEX act_idx_ejob_sub_scope ON act_ru_external_job USING btree (sub_scope_id_, scope_type_);



CREATE INDEX act_idx_ent_lnk_ref_scope ON act_ru_entitylink USING btree (ref_scope_id_, ref_scope_type_, link_type_);



CREATE INDEX act_idx_ent_lnk_root_scope ON act_ru_entitylink USING btree (root_scope_id_, root_scope_type_, link_type_);



CREATE INDEX act_idx_ent_lnk_scope ON act_ru_entitylink USING btree (scope_id_, scope_type_, link_type_);



CREATE INDEX act_idx_ent_lnk_scope_def ON act_ru_entitylink USING btree (scope_definition_id_, scope_type_, link_type_);



CREATE INDEX act_idx_event_subscr ON act_ru_event_subscr USING btree (execution_id_);



CREATE INDEX act_idx_event_subscr_config_ ON act_ru_event_subscr USING btree (configuration_);



CREATE INDEX act_idx_event_subscr_scoperef_ ON act_ru_event_subscr USING btree (scope_id_, scope_type_);



CREATE INDEX act_idx_exe_parent ON act_ru_execution USING btree (parent_id_);



CREATE INDEX act_idx_exe_procdef ON act_ru_execution USING btree (proc_def_id_);



CREATE INDEX act_idx_exe_procinst ON act_ru_execution USING btree (proc_inst_id_);



CREATE INDEX act_idx_exe_root ON act_ru_execution USING btree (root_proc_inst_id_);



CREATE INDEX act_idx_exe_super ON act_ru_execution USING btree (super_exec_);



CREATE INDEX act_idx_exec_buskey ON act_ru_execution USING btree (business_key_);



CREATE INDEX act_idx_exec_ref_id_ ON act_ru_execution USING btree (reference_id_);



CREATE INDEX act_idx_external_job_correlation_id ON act_ru_external_job USING btree (correlation_id_);



CREATE INDEX act_idx_external_job_custom_values_id ON act_ru_external_job USING btree (custom_values_id_);



CREATE INDEX act_idx_external_job_exception_stack_id ON act_ru_external_job USING btree (exception_stack_id_);



CREATE INDEX act_idx_hi_act_inst_end ON act_hi_actinst USING btree (end_time_);



CREATE INDEX act_idx_hi_act_inst_exec ON act_hi_actinst USING btree (execution_id_, act_id_);



CREATE INDEX act_idx_hi_act_inst_procinst ON act_hi_actinst USING btree (proc_inst_id_, act_id_);



CREATE INDEX act_idx_hi_act_inst_start ON act_hi_actinst USING btree (start_time_);



CREATE INDEX act_idx_hi_detail_act_inst ON act_hi_detail USING btree (act_inst_id_);



CREATE INDEX act_idx_hi_detail_name ON act_hi_detail USING btree (name_);



CREATE INDEX act_idx_hi_detail_proc_inst ON act_hi_detail USING btree (proc_inst_id_);



CREATE INDEX act_idx_hi_detail_task_id ON act_hi_detail USING btree (task_id_);



CREATE INDEX act_idx_hi_detail_time ON act_hi_detail USING btree (time_);



CREATE INDEX act_idx_hi_ent_lnk_ref_scope ON act_hi_entitylink USING btree (ref_scope_id_, ref_scope_type_, link_type_);



CREATE INDEX act_idx_hi_ent_lnk_root_scope ON act_hi_entitylink USING btree (root_scope_id_, root_scope_type_, link_type_);



CREATE INDEX act_idx_hi_ent_lnk_scope ON act_hi_entitylink USING btree (scope_id_, scope_type_, link_type_);



CREATE INDEX act_idx_hi_ent_lnk_scope_def ON act_hi_entitylink USING btree (scope_definition_id_, scope_type_, link_type_);



CREATE INDEX act_idx_hi_ident_lnk_procinst ON act_hi_identitylink USING btree (proc_inst_id_);



CREATE INDEX act_idx_hi_ident_lnk_scope ON act_hi_identitylink USING btree (scope_id_, scope_type_);



CREATE INDEX act_idx_hi_ident_lnk_scope_def ON act_hi_identitylink USING btree (scope_definition_id_, scope_type_);



CREATE INDEX act_idx_hi_ident_lnk_sub_scope ON act_hi_identitylink USING btree (sub_scope_id_, scope_type_);



CREATE INDEX act_idx_hi_ident_lnk_task ON act_hi_identitylink USING btree (task_id_);



CREATE INDEX act_idx_hi_ident_lnk_user ON act_hi_identitylink USING btree (user_id_);



CREATE INDEX act_idx_hi_pro_i_buskey ON act_hi_procinst USING btree (business_key_);



CREATE INDEX act_idx_hi_pro_inst_end ON act_hi_procinst USING btree (end_time_);



CREATE INDEX act_idx_hi_pro_super_procinst ON act_hi_procinst USING btree (super_process_instance_id_);



CREATE INDEX act_idx_hi_procvar_exe ON act_hi_varinst USING btree (execution_id_);



CREATE INDEX act_idx_hi_procvar_name_type ON act_hi_varinst USING btree (name_, var_type_);



CREATE INDEX act_idx_hi_procvar_proc_inst ON act_hi_varinst USING btree (proc_inst_id_);



CREATE INDEX act_idx_hi_procvar_task_id ON act_hi_varinst USING btree (task_id_);



CREATE INDEX act_idx_hi_task_inst_procinst ON act_hi_taskinst USING btree (proc_inst_id_);



CREATE INDEX act_idx_hi_task_scope ON act_hi_taskinst USING btree (scope_id_, scope_type_);



CREATE INDEX act_idx_hi_task_scope_def ON act_hi_taskinst USING btree (scope_definition_id_, scope_type_);



CREATE INDEX act_idx_hi_task_sub_scope ON act_hi_taskinst USING btree (sub_scope_id_, scope_type_);



CREATE INDEX act_idx_hi_var_scope_id_type ON act_hi_varinst USING btree (scope_id_, scope_type_);



CREATE INDEX act_idx_hi_var_sub_id_type ON act_hi_varinst USING btree (sub_scope_id_, scope_type_);



CREATE INDEX act_idx_ident_lnk_group ON act_ru_identitylink USING btree (group_id_);



CREATE INDEX act_idx_ident_lnk_scope ON act_ru_identitylink USING btree (scope_id_, scope_type_);



CREATE INDEX act_idx_ident_lnk_scope_def ON act_ru_identitylink USING btree (scope_definition_id_, scope_type_);



CREATE INDEX act_idx_ident_lnk_sub_scope ON act_ru_identitylink USING btree (sub_scope_id_, scope_type_);



CREATE INDEX act_idx_ident_lnk_user ON act_ru_identitylink USING btree (user_id_);



CREATE INDEX act_idx_idl_procinst ON act_ru_identitylink USING btree (proc_inst_id_);



CREATE INDEX act_idx_job_correlation_id ON act_ru_job USING btree (correlation_id_);



CREATE INDEX act_idx_job_custom_values_id ON act_ru_job USING btree (custom_values_id_);



CREATE INDEX act_idx_job_exception_stack_id ON act_ru_job USING btree (exception_stack_id_);



CREATE INDEX act_idx_job_execution_id ON act_ru_job USING btree (execution_id_);



CREATE INDEX act_idx_job_proc_def_id ON act_ru_job USING btree (proc_def_id_);



CREATE INDEX act_idx_job_process_instance_id ON act_ru_job USING btree (process_instance_id_);



CREATE INDEX act_idx_job_scope ON act_ru_job USING btree (scope_id_, scope_type_);



CREATE INDEX act_idx_job_scope_def ON act_ru_job USING btree (scope_definition_id_, scope_type_);



CREATE INDEX act_idx_job_sub_scope ON act_ru_job USING btree (sub_scope_id_, scope_type_);



CREATE INDEX act_idx_model_deployment ON act_re_model USING btree (deployment_id_);



CREATE INDEX act_idx_model_source ON act_re_model USING btree (editor_source_value_id_);



CREATE INDEX act_idx_model_source_extra ON act_re_model USING btree (editor_source_extra_value_id_);



CREATE INDEX act_idx_procdef_info_json ON act_procdef_info USING btree (info_json_id_);



CREATE INDEX act_idx_procdef_info_proc ON act_procdef_info USING btree (proc_def_id_);



CREATE INDEX act_idx_ru_acti_end ON act_ru_actinst USING btree (end_time_);



CREATE INDEX act_idx_ru_acti_exec ON act_ru_actinst USING btree (execution_id_);



CREATE INDEX act_idx_ru_acti_exec_act ON act_ru_actinst USING btree (execution_id_, act_id_);



CREATE INDEX act_idx_ru_acti_proc ON act_ru_actinst USING btree (proc_inst_id_);



CREATE INDEX act_idx_ru_acti_proc_act ON act_ru_actinst USING btree (proc_inst_id_, act_id_);



CREATE INDEX act_idx_ru_acti_start ON act_ru_actinst USING btree (start_time_);



CREATE INDEX act_idx_ru_acti_task ON act_ru_actinst USING btree (task_id_);



CREATE INDEX act_idx_ru_var_scope_id_type ON act_ru_variable USING btree (scope_id_, scope_type_);



CREATE INDEX act_idx_ru_var_sub_id_type ON act_ru_variable USING btree (sub_scope_id_, scope_type_);



CREATE INDEX act_idx_sjob_scope ON act_ru_suspended_job USING btree (scope_id_, scope_type_);



CREATE INDEX act_idx_sjob_scope_def ON act_ru_suspended_job USING btree (scope_definition_id_, scope_type_);



CREATE INDEX act_idx_sjob_sub_scope ON act_ru_suspended_job USING btree (sub_scope_id_, scope_type_);



CREATE INDEX act_idx_suspended_job_correlation_id ON act_ru_suspended_job USING btree (correlation_id_);



CREATE INDEX act_idx_suspended_job_custom_values_id ON act_ru_suspended_job USING btree (custom_values_id_);



CREATE INDEX act_idx_suspended_job_exception_stack_id ON act_ru_suspended_job USING btree (exception_stack_id_);



CREATE INDEX act_idx_suspended_job_execution_id ON act_ru_suspended_job USING btree (execution_id_);



CREATE INDEX act_idx_suspended_job_proc_def_id ON act_ru_suspended_job USING btree (proc_def_id_);



CREATE INDEX act_idx_suspended_job_process_instance_id ON act_ru_suspended_job USING btree (process_instance_id_);



CREATE INDEX act_idx_task_create ON act_ru_task USING btree (create_time_);



CREATE INDEX act_idx_task_exec ON act_ru_task USING btree (execution_id_);



CREATE INDEX act_idx_task_procdef ON act_ru_task USING btree (proc_def_id_);



CREATE INDEX act_idx_task_procinst ON act_ru_task USING btree (proc_inst_id_);



CREATE INDEX act_idx_task_scope ON act_ru_task USING btree (scope_id_, scope_type_);



CREATE INDEX act_idx_task_scope_def ON act_ru_task USING btree (scope_definition_id_, scope_type_);



CREATE INDEX act_idx_task_sub_scope ON act_ru_task USING btree (sub_scope_id_, scope_type_);



CREATE INDEX act_idx_timer_job_correlation_id ON act_ru_timer_job USING btree (correlation_id_);



CREATE INDEX act_idx_timer_job_custom_values_id ON act_ru_timer_job USING btree (custom_values_id_);



CREATE INDEX act_idx_timer_job_duedate ON act_ru_timer_job USING btree (duedate_);



CREATE INDEX act_idx_timer_job_exception_stack_id ON act_ru_timer_job USING btree (exception_stack_id_);



CREATE INDEX act_idx_timer_job_execution_id ON act_ru_timer_job USING btree (execution_id_);



CREATE INDEX act_idx_timer_job_proc_def_id ON act_ru_timer_job USING btree (proc_def_id_);



CREATE INDEX act_idx_timer_job_process_instance_id ON act_ru_timer_job USING btree (process_instance_id_);



CREATE INDEX act_idx_tjob_scope ON act_ru_timer_job USING btree (scope_id_, scope_type_);



CREATE INDEX act_idx_tjob_scope_def ON act_ru_timer_job USING btree (scope_definition_id_, scope_type_);



CREATE INDEX act_idx_tjob_sub_scope ON act_ru_timer_job USING btree (sub_scope_id_, scope_type_);



CREATE INDEX act_idx_tskass_task ON act_ru_identitylink USING btree (task_id_);



CREATE INDEX act_idx_var_bytearray ON act_ru_variable USING btree (bytearray_id_);



CREATE INDEX act_idx_var_exe ON act_ru_variable USING btree (execution_id_);



CREATE INDEX act_idx_var_procinst ON act_ru_variable USING btree (proc_inst_id_);



CREATE INDEX act_idx_variable_task_id ON act_ru_variable USING btree (task_id_);



CREATE INDEX flw_idx_batch_part ON flw_ru_batch_part USING btree (batch_id_);



ALTER TABLE ONLY act_ru_identitylink
    ADD CONSTRAINT act_fk_athrz_procedef FOREIGN KEY (proc_def_id_) REFERENCES act_re_procdef(id_);



ALTER TABLE ONLY act_ge_bytearray
    ADD CONSTRAINT act_fk_bytearr_depl FOREIGN KEY (deployment_id_) REFERENCES act_re_deployment(id_);



ALTER TABLE ONLY act_ru_deadletter_job
    ADD CONSTRAINT act_fk_deadletter_job_custom_values FOREIGN KEY (custom_values_id_) REFERENCES act_ge_bytearray(id_);



ALTER TABLE ONLY act_ru_deadletter_job
    ADD CONSTRAINT act_fk_deadletter_job_exception FOREIGN KEY (exception_stack_id_) REFERENCES act_ge_bytearray(id_);



ALTER TABLE ONLY act_ru_deadletter_job
    ADD CONSTRAINT act_fk_deadletter_job_execution FOREIGN KEY (execution_id_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY act_ru_deadletter_job
    ADD CONSTRAINT act_fk_deadletter_job_proc_def FOREIGN KEY (proc_def_id_) REFERENCES act_re_procdef(id_);



ALTER TABLE ONLY act_ru_deadletter_job
    ADD CONSTRAINT act_fk_deadletter_job_process_instance FOREIGN KEY (process_instance_id_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY act_ru_event_subscr
    ADD CONSTRAINT act_fk_event_exec FOREIGN KEY (execution_id_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY act_ru_execution
    ADD CONSTRAINT act_fk_exe_parent FOREIGN KEY (parent_id_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY act_ru_execution
    ADD CONSTRAINT act_fk_exe_procdef FOREIGN KEY (proc_def_id_) REFERENCES act_re_procdef(id_);



ALTER TABLE ONLY act_ru_execution
    ADD CONSTRAINT act_fk_exe_procinst FOREIGN KEY (proc_inst_id_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY act_ru_execution
    ADD CONSTRAINT act_fk_exe_super FOREIGN KEY (super_exec_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY act_ru_external_job
    ADD CONSTRAINT act_fk_external_job_custom_values FOREIGN KEY (custom_values_id_) REFERENCES act_ge_bytearray(id_);



ALTER TABLE ONLY act_ru_external_job
    ADD CONSTRAINT act_fk_external_job_exception FOREIGN KEY (exception_stack_id_) REFERENCES act_ge_bytearray(id_);



ALTER TABLE ONLY act_ru_identitylink
    ADD CONSTRAINT act_fk_idl_procinst FOREIGN KEY (proc_inst_id_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY act_procdef_info
    ADD CONSTRAINT act_fk_info_json_ba FOREIGN KEY (info_json_id_) REFERENCES act_ge_bytearray(id_);



ALTER TABLE ONLY act_procdef_info
    ADD CONSTRAINT act_fk_info_procdef FOREIGN KEY (proc_def_id_) REFERENCES act_re_procdef(id_);



ALTER TABLE ONLY act_ru_job
    ADD CONSTRAINT act_fk_job_custom_values FOREIGN KEY (custom_values_id_) REFERENCES act_ge_bytearray(id_);



ALTER TABLE ONLY act_ru_job
    ADD CONSTRAINT act_fk_job_exception FOREIGN KEY (exception_stack_id_) REFERENCES act_ge_bytearray(id_);



ALTER TABLE ONLY act_ru_job
    ADD CONSTRAINT act_fk_job_execution FOREIGN KEY (execution_id_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY act_ru_job
    ADD CONSTRAINT act_fk_job_proc_def FOREIGN KEY (proc_def_id_) REFERENCES act_re_procdef(id_);



ALTER TABLE ONLY act_ru_job
    ADD CONSTRAINT act_fk_job_process_instance FOREIGN KEY (process_instance_id_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY act_re_model
    ADD CONSTRAINT act_fk_model_deployment FOREIGN KEY (deployment_id_) REFERENCES act_re_deployment(id_);



ALTER TABLE ONLY act_re_model
    ADD CONSTRAINT act_fk_model_source FOREIGN KEY (editor_source_value_id_) REFERENCES act_ge_bytearray(id_);



ALTER TABLE ONLY act_re_model
    ADD CONSTRAINT act_fk_model_source_extra FOREIGN KEY (editor_source_extra_value_id_) REFERENCES act_ge_bytearray(id_);



ALTER TABLE ONLY act_ru_suspended_job
    ADD CONSTRAINT act_fk_suspended_job_custom_values FOREIGN KEY (custom_values_id_) REFERENCES act_ge_bytearray(id_);



ALTER TABLE ONLY act_ru_suspended_job
    ADD CONSTRAINT act_fk_suspended_job_exception FOREIGN KEY (exception_stack_id_) REFERENCES act_ge_bytearray(id_);



ALTER TABLE ONLY act_ru_suspended_job
    ADD CONSTRAINT act_fk_suspended_job_execution FOREIGN KEY (execution_id_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY act_ru_suspended_job
    ADD CONSTRAINT act_fk_suspended_job_proc_def FOREIGN KEY (proc_def_id_) REFERENCES act_re_procdef(id_);



ALTER TABLE ONLY act_ru_suspended_job
    ADD CONSTRAINT act_fk_suspended_job_process_instance FOREIGN KEY (process_instance_id_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY act_ru_task
    ADD CONSTRAINT act_fk_task_exe FOREIGN KEY (execution_id_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY act_ru_task
    ADD CONSTRAINT act_fk_task_procdef FOREIGN KEY (proc_def_id_) REFERENCES act_re_procdef(id_);



ALTER TABLE ONLY act_ru_task
    ADD CONSTRAINT act_fk_task_procinst FOREIGN KEY (proc_inst_id_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY act_ru_timer_job
    ADD CONSTRAINT act_fk_timer_job_custom_values FOREIGN KEY (custom_values_id_) REFERENCES act_ge_bytearray(id_);



ALTER TABLE ONLY act_ru_timer_job
    ADD CONSTRAINT act_fk_timer_job_exception FOREIGN KEY (exception_stack_id_) REFERENCES act_ge_bytearray(id_);



ALTER TABLE ONLY act_ru_timer_job
    ADD CONSTRAINT act_fk_timer_job_execution FOREIGN KEY (execution_id_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY act_ru_timer_job
    ADD CONSTRAINT act_fk_timer_job_proc_def FOREIGN KEY (proc_def_id_) REFERENCES act_re_procdef(id_);



ALTER TABLE ONLY act_ru_timer_job
    ADD CONSTRAINT act_fk_timer_job_process_instance FOREIGN KEY (process_instance_id_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY act_ru_identitylink
    ADD CONSTRAINT act_fk_tskass_task FOREIGN KEY (task_id_) REFERENCES act_ru_task(id_);



ALTER TABLE ONLY act_ru_variable
    ADD CONSTRAINT act_fk_var_bytearray FOREIGN KEY (bytearray_id_) REFERENCES act_ge_bytearray(id_);



ALTER TABLE ONLY act_ru_variable
    ADD CONSTRAINT act_fk_var_exe FOREIGN KEY (execution_id_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY act_ru_variable
    ADD CONSTRAINT act_fk_var_procinst FOREIGN KEY (proc_inst_id_) REFERENCES act_ru_execution(id_);



ALTER TABLE ONLY flw_ru_batch_part
    ADD CONSTRAINT flw_fk_batch_part_parent FOREIGN KEY (batch_id_) REFERENCES flw_ru_batch(id_);



INSERT INTO act_ge_property (name_, value_, rev_) VALUES ('common.schema.version', '7.0.1.1', 1);
INSERT INTO act_ge_property (name_, value_, rev_) VALUES ('next.dbid', '1', 1);
INSERT INTO act_ge_property (name_, value_, rev_) VALUES ('identitylink.schema.version', '7.0.1.1', 1);
INSERT INTO act_ge_property (name_, value_, rev_) VALUES ('entitylink.schema.version', '7.0.1.1', 1);
INSERT INTO act_ge_property (name_, value_, rev_) VALUES ('eventsubscription.schema.version', '7.0.1.1', 1);
INSERT INTO act_ge_property (name_, value_, rev_) VALUES ('task.schema.version', '7.0.1.1', 1);
INSERT INTO act_ge_property (name_, value_, rev_) VALUES ('variable.schema.version', '7.0.1.1', 1);
INSERT INTO act_ge_property (name_, value_, rev_) VALUES ('job.schema.version', '7.0.1.1', 1);
INSERT INTO act_ge_property (name_, value_, rev_) VALUES ('batch.schema.version', '7.0.1.1', 1);
INSERT INTO act_ge_property (name_, value_, rev_) VALUES ('schema.version', '7.0.1.1', 1);
INSERT INTO act_ge_property (name_, value_, rev_) VALUES ('schema.history', 'create(7.0.1.1)', 1);
INSERT INTO act_ge_property (name_, value_, rev_) VALUES ('cfg.execution-related-entities-count', 'true', 1);
INSERT INTO act_ge_property (name_, value_, rev_) VALUES ('cfg.task-related-entities-count', 'true', 1);

-- 引擎表授权
GRANT SELECT, INSERT, UPDATE, DELETE ON act_evt_log, act_ge_bytearray, act_ge_property, act_hi_actinst, act_hi_attachment, act_hi_comment, act_hi_detail, act_hi_entitylink, act_hi_identitylink, act_hi_procinst, act_hi_taskinst, act_hi_tsk_log, act_hi_varinst, act_procdef_info, act_re_deployment, act_re_model, act_re_procdef, act_ru_actinst, act_ru_deadletter_job, act_ru_entitylink, act_ru_event_subscr, act_ru_execution, act_ru_external_job, act_ru_history_job, act_ru_identitylink, act_ru_job, act_ru_suspended_job, act_ru_task, act_ru_timer_job, act_ru_variable, flw_ru_batch, flw_ru_batch_part TO fl_app, fl_platform_role;

