create table act_ge_property (
    name_ varchar(64),
    value_ varchar(300),
    rev_ integer,
    primary key (name_)
);

create table act_ge_bytearray (
    id_ varchar(64),
    rev_ integer,
    name_ varchar(255),
    deployment_id_ varchar(64),
    bytes_ bytea,
    generated_ boolean,
    primary key (id_)
);

insert into act_ge_property
values ('common.schema.version', '7.2.0.2', 1);

insert into act_ge_property
values ('next.dbid', '1', 1);


create table act_ru_entitylink (
    id_ varchar(64),
    rev_ integer,
    create_time_ timestamp,
    link_type_ varchar(255),
    scope_id_ varchar(255),
    sub_scope_id_ varchar(255),
    scope_type_ varchar(255),
    scope_definition_id_ varchar(255),
    parent_element_id_ varchar(255),
    ref_scope_id_ varchar(255),
    ref_scope_type_ varchar(255),
    ref_scope_definition_id_ varchar(255),
    root_scope_id_ varchar(255),
    root_scope_type_ varchar(255),
    hierarchy_type_ varchar(255),
    primary key (id_)
);

create index act_idx_ent_lnk_scope on act_ru_entitylink(scope_id_, scope_type_, link_type_);
create index act_idx_ent_lnk_ref_scope on act_ru_entitylink(ref_scope_id_, ref_scope_type_, link_type_);
create index act_idx_ent_lnk_root_scope on act_ru_entitylink(root_scope_id_, root_scope_type_, link_type_);
create index act_idx_ent_lnk_scope_def on act_ru_entitylink(scope_definition_id_, scope_type_, link_type_);

create table act_hi_entitylink (
    id_ varchar(64),
    link_type_ varchar(255),
    create_time_ timestamp,
    scope_id_ varchar(255),
    sub_scope_id_ varchar(255),
    scope_type_ varchar(255),
    scope_definition_id_ varchar(255),
    parent_element_id_ varchar(255),
    ref_scope_id_ varchar(255),
    ref_scope_type_ varchar(255),
    ref_scope_definition_id_ varchar(255),
    root_scope_id_ varchar(255),
    root_scope_type_ varchar(255),
    hierarchy_type_ varchar(255),
    primary key (id_)
);

create index act_idx_hi_ent_lnk_scope on act_hi_entitylink(scope_id_, scope_type_, link_type_);
create index act_idx_hi_ent_lnk_ref_scope on act_hi_entitylink(ref_scope_id_, ref_scope_type_, link_type_);
create index act_idx_hi_ent_lnk_root_scope on act_hi_entitylink(root_scope_id_, root_scope_type_, link_type_);
create index act_idx_hi_ent_lnk_scope_def on act_hi_entitylink(scope_definition_id_, scope_type_, link_type_);


create table act_ru_identitylink (
    id_ varchar(64),
    rev_ integer,
    group_id_ varchar(255),
    type_ varchar(255),
    user_id_ varchar(255),
    task_id_ varchar(64),
    proc_inst_id_ varchar(64),
    proc_def_id_ varchar (64),
    scope_id_ varchar(255),
    sub_scope_id_ varchar(255),
    scope_type_ varchar(255),
    scope_definition_id_ varchar(255),
    primary key (id_)
);

create index act_idx_ident_lnk_user on act_ru_identitylink(user_id_);
create index act_idx_ident_lnk_group on act_ru_identitylink(group_id_);
create index act_idx_ident_lnk_scope on act_ru_identitylink(scope_id_, scope_type_);
create index act_idx_ident_lnk_sub_scope on act_ru_identitylink(sub_scope_id_, scope_type_);
create index act_idx_ident_lnk_scope_def on act_ru_identitylink(scope_definition_id_, scope_type_);

create table act_hi_identitylink (
    id_ varchar(64),
    group_id_ varchar(255),
    type_ varchar(255),
    user_id_ varchar(255),
    task_id_ varchar(64),
    create_time_ timestamp,
    proc_inst_id_ varchar(64),
    scope_id_ varchar(255),
    sub_scope_id_ varchar(255),
    scope_type_ varchar(255),
    scope_definition_id_ varchar(255),
    primary key (id_)
);

create index act_idx_hi_ident_lnk_user on act_hi_identitylink(user_id_);
create index act_idx_hi_ident_lnk_scope on act_hi_identitylink(scope_id_, scope_type_);
create index act_idx_hi_ident_lnk_sub_scope on act_hi_identitylink(sub_scope_id_, scope_type_);
create index act_idx_hi_ident_lnk_scope_def on act_hi_identitylink(scope_definition_id_, scope_type_);


create table act_ru_job (
    id_ varchar(64) not null,
    rev_ integer,
    category_ varchar(255),
    type_ varchar(255) not null,
    lock_exp_time_ timestamp,
    lock_owner_ varchar(255),
    exclusive_ boolean,
    execution_id_ varchar(64),
    process_instance_id_ varchar(64),
    proc_def_id_ varchar(64),
    element_id_ varchar(255),
    element_name_ varchar(255),
    scope_id_ varchar(255),
    sub_scope_id_ varchar(255),
    scope_type_ varchar(255),
    scope_definition_id_ varchar(255),
    correlation_id_ varchar(255),
    retries_ integer,
    exception_stack_id_ varchar(64),
    exception_msg_ varchar(4000),
    duedate_ timestamp,
    repeat_ varchar(255),
    handler_type_ varchar(255),
    handler_cfg_ varchar(4000),
    custom_values_id_ varchar(64),
    create_time_ timestamp,
    tenant_id_ varchar(255) default '',
    primary key (id_)
);

create table act_ru_timer_job (
    id_ varchar(64) not null,
    rev_ integer,
    category_ varchar(255),
    type_ varchar(255) not null,
    lock_exp_time_ timestamp,
    lock_owner_ varchar(255),
    exclusive_ boolean,
    execution_id_ varchar(64),
    process_instance_id_ varchar(64),
    proc_def_id_ varchar(64),
    element_id_ varchar(255),
    element_name_ varchar(255),
    scope_id_ varchar(255),
    sub_scope_id_ varchar(255),
    scope_type_ varchar(255),
    scope_definition_id_ varchar(255),
    correlation_id_ varchar(255),
    retries_ integer,
    exception_stack_id_ varchar(64),
    exception_msg_ varchar(4000),
    duedate_ timestamp,
    repeat_ varchar(255),
    handler_type_ varchar(255),
    handler_cfg_ varchar(4000),
    custom_values_id_ varchar(64),
    create_time_ timestamp,
    tenant_id_ varchar(255) default '',
    primary key (id_)
);

create table act_ru_suspended_job (
    id_ varchar(64) not null,
    rev_ integer,
    category_ varchar(255),
    type_ varchar(255) not null,
    exclusive_ boolean,
    execution_id_ varchar(64),
    process_instance_id_ varchar(64),
    proc_def_id_ varchar(64),
    element_id_ varchar(255),
    element_name_ varchar(255),
    scope_id_ varchar(255),
    sub_scope_id_ varchar(255),
    scope_type_ varchar(255),
    scope_definition_id_ varchar(255),
    correlation_id_ varchar(255),
    retries_ integer,
    exception_stack_id_ varchar(64),
    exception_msg_ varchar(4000),
    duedate_ timestamp,
    repeat_ varchar(255),
    handler_type_ varchar(255),
    handler_cfg_ varchar(4000),
    custom_values_id_ varchar(64),
    create_time_ timestamp,
    tenant_id_ varchar(255) default '',
    primary key (id_)
);

create table act_ru_deadletter_job (
    id_ varchar(64) not null,
    rev_ integer,
    category_ varchar(255),
    type_ varchar(255) not null,
    exclusive_ boolean,
    execution_id_ varchar(64),
    process_instance_id_ varchar(64),
    proc_def_id_ varchar(64),
    element_id_ varchar(255),
    element_name_ varchar(255),
    scope_id_ varchar(255),
    sub_scope_id_ varchar(255),
    scope_type_ varchar(255),
    scope_definition_id_ varchar(255),
    correlation_id_ varchar(255),
    exception_stack_id_ varchar(64),
    exception_msg_ varchar(4000),
    duedate_ timestamp,
    repeat_ varchar(255),
    handler_type_ varchar(255),
    handler_cfg_ varchar(4000),
    custom_values_id_ varchar(64),
    create_time_ timestamp,
    tenant_id_ varchar(255) default '',
    primary key (id_)
);

create table act_ru_history_job (
    id_ varchar(64) not null,
    rev_ integer,
    lock_exp_time_ timestamp,
    lock_owner_ varchar(255),
    retries_ integer,
    exception_stack_id_ varchar(64),
    exception_msg_ varchar(4000),
    handler_type_ varchar(255),
    handler_cfg_ varchar(4000),
    custom_values_id_ varchar(64),
    adv_handler_cfg_id_ varchar(64),
    create_time_ timestamp,
    scope_type_ varchar(255),
    tenant_id_ varchar(255) default '',
    primary key (id_)
);

create table act_ru_external_job (
    id_ varchar(64) not null,
    rev_ integer,
    category_ varchar(255),
    type_ varchar(255) not null,
    lock_exp_time_ timestamp,
    lock_owner_ varchar(255),
    exclusive_ boolean,
    execution_id_ varchar(64),
    process_instance_id_ varchar(64),
    proc_def_id_ varchar(64),
    element_id_ varchar(255),
    element_name_ varchar(255),
    scope_id_ varchar(255),
    sub_scope_id_ varchar(255),
    scope_type_ varchar(255),
    scope_definition_id_ varchar(255),
    correlation_id_ varchar(255),
    retries_ integer,
    exception_stack_id_ varchar(64),
    exception_msg_ varchar(4000),
    duedate_ timestamp,
    repeat_ varchar(255),
    handler_type_ varchar(255),
    handler_cfg_ varchar(4000),
    custom_values_id_ varchar(64),
    create_time_ timestamp,
    tenant_id_ varchar(255) default '',
    primary key (id_)
);

create index act_idx_job_exception_stack_id on act_ru_job(exception_stack_id_);
create index act_idx_job_custom_values_id on act_ru_job(custom_values_id_);
create index act_idx_job_correlation_id on act_ru_job(correlation_id_);

create index act_idx_timer_job_exception_stack_id on act_ru_timer_job(exception_stack_id_);
create index act_idx_timer_job_custom_values_id on act_ru_timer_job(custom_values_id_);
create index act_idx_timer_job_correlation_id on act_ru_timer_job(correlation_id_);
create index act_idx_timer_job_duedate on act_ru_timer_job(duedate_); 

create index act_idx_suspended_job_exception_stack_id on act_ru_suspended_job(exception_stack_id_);
create index act_idx_suspended_job_custom_values_id on act_ru_suspended_job(custom_values_id_);
create index act_idx_suspended_job_correlation_id on act_ru_suspended_job(correlation_id_);

create index act_idx_deadletter_job_exception_stack_id on act_ru_deadletter_job(exception_stack_id_);
create index act_idx_deadletter_job_custom_values_id on act_ru_deadletter_job(custom_values_id_);
create index act_idx_deadletter_job_correlation_id on act_ru_deadletter_job(correlation_id_);

create index act_idx_external_job_exception_stack_id on act_ru_external_job(exception_stack_id_);
create index act_idx_external_job_custom_values_id on act_ru_external_job(custom_values_id_);
create index act_idx_external_job_correlation_id on act_ru_external_job(correlation_id_);

alter table act_ru_job
    add constraint act_fk_job_exception
    foreign key (exception_stack_id_)
    references act_ge_bytearray (id_);

alter table act_ru_job
    add constraint act_fk_job_custom_values
    foreign key (custom_values_id_)
    references act_ge_bytearray (id_);

alter table act_ru_timer_job
    add constraint act_fk_timer_job_exception
    foreign key (exception_stack_id_)
    references act_ge_bytearray (id_);

alter table act_ru_timer_job
    add constraint act_fk_timer_job_custom_values
    foreign key (custom_values_id_)
    references act_ge_bytearray (id_);

alter table act_ru_suspended_job
    add constraint act_fk_suspended_job_exception
    foreign key (exception_stack_id_)
    references act_ge_bytearray (id_);

alter table act_ru_suspended_job
    add constraint act_fk_suspended_job_custom_values
    foreign key (custom_values_id_)
    references act_ge_bytearray (id_);

alter table act_ru_deadletter_job
    add constraint act_fk_deadletter_job_exception
    foreign key (exception_stack_id_)
    references act_ge_bytearray (id_);

alter table act_ru_deadletter_job
    add constraint act_fk_deadletter_job_custom_values
    foreign key (custom_values_id_)
    references act_ge_bytearray (id_);

alter table act_ru_external_job
    add constraint act_fk_external_job_exception
    foreign key (exception_stack_id_)
    references act_ge_bytearray (id_);

alter table act_ru_external_job
    add constraint act_fk_external_job_custom_values
    foreign key (custom_values_id_)
    references act_ge_bytearray (id_);

create index act_idx_job_scope on act_ru_job(scope_id_, scope_type_);
create index act_idx_job_sub_scope on act_ru_job(sub_scope_id_, scope_type_);
create index act_idx_job_scope_def on act_ru_job(scope_definition_id_, scope_type_);

create index act_idx_tjob_scope on act_ru_timer_job(scope_id_, scope_type_);
create index act_idx_tjob_sub_scope on act_ru_timer_job(sub_scope_id_, scope_type_);
create index act_idx_tjob_scope_def on act_ru_timer_job(scope_definition_id_, scope_type_); 

create index act_idx_sjob_scope on act_ru_suspended_job(scope_id_, scope_type_);
create index act_idx_sjob_sub_scope on act_ru_suspended_job(sub_scope_id_, scope_type_);
create index act_idx_sjob_scope_def on act_ru_suspended_job(scope_definition_id_, scope_type_);   

create index act_idx_djob_scope on act_ru_deadletter_job(scope_id_, scope_type_);
create index act_idx_djob_sub_scope on act_ru_deadletter_job(sub_scope_id_, scope_type_);
create index act_idx_djob_scope_def on act_ru_deadletter_job(scope_definition_id_, scope_type_);

create index act_idx_ejob_scope on act_ru_external_job(scope_id_, scope_type_);
create index act_idx_ejob_sub_scope on act_ru_external_job(sub_scope_id_, scope_type_);
create index act_idx_ejob_scope_def on act_ru_external_job(scope_definition_id_, scope_type_);

create table flw_ru_batch (
    id_ varchar(64) not null,
    rev_ integer,
    type_ varchar(64) not null,
    search_key_ varchar(255),
    search_key2_ varchar(255),
    create_time_ timestamp not null,
    complete_time_ timestamp,
    status_ varchar(255),
    batch_doc_id_ varchar(64),
    tenant_id_ varchar(255) default '',
    primary key (id_)
);

create table flw_ru_batch_part (
    id_ varchar(64) not null,
    rev_ integer,
    batch_id_ varchar(64),
    type_ varchar(64) not null,
    scope_id_ varchar(64),
    sub_scope_id_ varchar(64),
    scope_type_ varchar(64),
    search_key_ varchar(255),
    search_key2_ varchar(255),
    create_time_ timestamp not null,
    complete_time_ timestamp,
    status_ varchar(255),
    result_doc_id_ varchar(64),
    tenant_id_ varchar(255) default '',
    primary key (id_)
);

create index flw_idx_batch_part on flw_ru_batch_part(batch_id_);

alter table flw_ru_batch_part
    add constraint flw_fk_batch_part_parent
    foreign key (batch_id_)
    references flw_ru_batch (id_);

create table act_ru_task (
    id_ varchar(64),
    rev_ integer,
    execution_id_ varchar(64),
    proc_inst_id_ varchar(64),
    proc_def_id_ varchar(64),
    task_def_id_ varchar(64),
    scope_id_ varchar(255),
    sub_scope_id_ varchar(255),
    scope_type_ varchar(255),
    scope_definition_id_ varchar(255),
    propagated_stage_inst_id_ varchar(255),
    state_ varchar(255),
    name_ varchar(255),
    parent_task_id_ varchar(64),
    description_ varchar(4000),
    task_def_key_ varchar(255),
    owner_ varchar(255),
    assignee_ varchar(255),
    delegation_ varchar(64),
    priority_ integer,
    create_time_ timestamp,
    in_progress_time_ timestamp,
    in_progress_started_by_ varchar(255),
    claim_time_ timestamp,
    claimed_by_ varchar(255),
    suspended_time_ timestamp,
    suspended_by_ varchar(255),
    in_progress_due_date_ timestamp,
    due_date_ timestamp,
    category_ varchar(255),
    suspension_state_ integer,
    tenant_id_ varchar(255) default '',
    form_key_ varchar(255),
    is_count_enabled_ boolean,
    var_count_ integer, 
    id_link_count_ integer,
    sub_task_count_ integer,
    primary key (id_)
);

create index act_idx_task_create on act_ru_task(create_time_);
create index act_idx_task_scope on act_ru_task(scope_id_, scope_type_);
create index act_idx_task_sub_scope on act_ru_task(sub_scope_id_, scope_type_);
create index act_idx_task_scope_def on act_ru_task(scope_definition_id_, scope_type_);

create table act_hi_taskinst (
    id_ varchar(64) not null,
    rev_ integer default 1,
    proc_def_id_ varchar(64),
    task_def_id_ varchar(64),
    task_def_key_ varchar(255),
    proc_inst_id_ varchar(64),
    execution_id_ varchar(64),
    scope_id_ varchar(255),
    sub_scope_id_ varchar(255),
    scope_type_ varchar(255),
    scope_definition_id_ varchar(255),
    propagated_stage_inst_id_ varchar(255),
    state_ varchar(255),
    name_ varchar(255),
    parent_task_id_ varchar(64),
    description_ varchar(4000),
    owner_ varchar(255),
    assignee_ varchar(255),
    start_time_ timestamp not null,
    in_progress_time_ timestamp,
    in_progress_started_by_ varchar(255),
    claim_time_ timestamp,
    claimed_by_ varchar(255),
    suspended_time_ timestamp,
    suspended_by_ varchar(255),
    end_time_ timestamp,
    completed_by_ varchar(255),
    duration_ bigint,
    delete_reason_ varchar(4000),
    priority_ integer,
    in_progress_due_date_ timestamp,
    due_date_ timestamp,
    form_key_ varchar(255),
    category_ varchar(255),
    tenant_id_ varchar(255) default '',
    last_updated_time_ timestamp,
    primary key (id_)
);

create table act_hi_tsk_log (
    id_ serial primary key,
    type_ varchar(64),
    task_id_ varchar(64) not null,
    time_stamp_ timestamp not null,
    user_id_ varchar(255),
    data_ varchar(4000),
    execution_id_ varchar(64),
    proc_inst_id_ varchar(64),
    proc_def_id_ varchar(64),
    scope_id_ varchar(255),
    scope_definition_id_ varchar(255),
    sub_scope_id_ varchar(255),
    scope_type_ varchar(255),
    tenant_id_ varchar(255) default ''
);

create index act_idx_hi_task_scope on act_hi_taskinst(scope_id_, scope_type_);
create index act_idx_hi_task_sub_scope on act_hi_taskinst(sub_scope_id_, scope_type_);
create index act_idx_hi_task_scope_def on act_hi_taskinst(scope_definition_id_, scope_type_);
create index act_idx_act_hi_tsk_log_task on act_hi_tsk_log(task_id_);

create table act_ru_variable (
    id_ varchar(64) not null,
    rev_ integer,
    type_ varchar(255) not null,
    name_ varchar(255) not null,
    execution_id_ varchar(64),
    proc_inst_id_ varchar(64),
    task_id_ varchar(64),
    scope_id_ varchar(255),
    sub_scope_id_ varchar(255),
    scope_type_ varchar(255),
    bytearray_id_ varchar(64),
    double_ double precision,
    long_ bigint,
    text_ varchar(4000),
    text2_ varchar(4000),
    meta_info_ varchar(4000),
    primary key (id_)
);

create index act_idx_ru_var_scope_id_type on act_ru_variable(scope_id_, scope_type_);
create index act_idx_ru_var_sub_id_type on act_ru_variable(sub_scope_id_, scope_type_);

create index act_idx_var_bytearray on act_ru_variable(bytearray_id_);
alter table act_ru_variable 
    add constraint act_fk_var_bytearray 
    foreign key (bytearray_id_) 
    references act_ge_bytearray (id_);

create table act_hi_varinst (
    id_ varchar(64) not null,
    rev_ integer default 1,
    proc_inst_id_ varchar(64),
    execution_id_ varchar(64),
    task_id_ varchar(64),
    name_ varchar(255) not null,
    var_type_ varchar(100),
    scope_id_ varchar(255),
    sub_scope_id_ varchar(255),
    scope_type_ varchar(255),
    bytearray_id_ varchar(64),
    double_ double precision,
    long_ bigint,
    text_ varchar(4000),
    text2_ varchar(4000),
    meta_info_ varchar(4000),
    create_time_ timestamp,
    last_updated_time_ timestamp,
    primary key (id_)
);

create index act_idx_hi_procvar_name_type on act_hi_varinst(name_, var_type_);
create index act_idx_hi_var_scope_id_type on act_hi_varinst(scope_id_, scope_type_);
create index act_idx_hi_var_sub_id_type on act_hi_varinst(sub_scope_id_, scope_type_);


create table act_ru_event_subscr (
    id_ varchar(64) not null,
    rev_ integer,
    event_type_ varchar(255) not null,
    event_name_ varchar(255),
    execution_id_ varchar(64),
    proc_inst_id_ varchar(64),
    activity_id_ varchar(64),
    configuration_ varchar(255),
    created_ timestamp not null,
    proc_def_id_ varchar(64),
    sub_scope_id_ varchar(64),
    scope_id_ varchar(64),
    scope_definition_id_ varchar(64),
    scope_definition_key_ varchar(255),
    scope_type_ varchar(64),
    lock_time_ timestamp,
    lock_owner_ varchar(255),
    tenant_id_ varchar(255) default '',
    primary key (id_)
);

create index act_idx_event_subscr_config_ on act_ru_event_subscr(configuration_);
create index act_idx_event_subscr on act_ru_event_subscr(execution_id_);
create index act_idx_event_subscr_proc_id on act_ru_event_subscr(proc_inst_id_);
create index act_idx_event_subscr_scoperef_ on act_ru_event_subscr(scope_id_, scope_type_);
