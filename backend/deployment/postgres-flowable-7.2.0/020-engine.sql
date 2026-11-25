create table act_re_deployment (
    id_ varchar(64),
    name_ varchar(255),
    category_ varchar(255),
    key_ varchar(255),
    tenant_id_ varchar(255) default '',
    deploy_time_ timestamp,
    derived_from_ varchar(64),
    derived_from_root_ varchar(64),
    parent_deployment_id_ varchar(255),
    engine_version_ varchar(255),
    primary key (id_)
);

create table act_re_model (
    id_ varchar(64) not null,
    rev_ integer,
    name_ varchar(255),
    key_ varchar(255),
    category_ varchar(255),
    create_time_ timestamp,
    last_update_time_ timestamp,
    version_ integer,
    meta_info_ varchar(4000),
    deployment_id_ varchar(64),
    editor_source_value_id_ varchar(64),
    editor_source_extra_value_id_ varchar(64),
    tenant_id_ varchar(255) default '',
    primary key (id_)
);

create table act_ru_execution (
    id_ varchar(64),
    rev_ integer,
    proc_inst_id_ varchar(64),
    business_key_ varchar(255),
    parent_id_ varchar(64),
    proc_def_id_ varchar(64),
    super_exec_ varchar(64),
    root_proc_inst_id_ varchar(64),
    act_id_ varchar(255),
    is_active_ boolean,
    is_concurrent_ boolean,
    is_scope_ boolean,
    is_event_scope_ boolean,
    is_mi_root_ boolean,
    suspension_state_ integer,
    cached_ent_state_ integer,
    tenant_id_ varchar(255) default '',
    name_ varchar(255),
    start_act_id_ varchar(255),
    start_time_ timestamp,
    start_user_id_ varchar(255),
    lock_time_ timestamp,
    lock_owner_ varchar(255),
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
    callback_id_ varchar(255),
    callback_type_ varchar(255),
    reference_id_ varchar(255),
    reference_type_ varchar(255),
    propagated_stage_inst_id_ varchar(255),
    business_status_ varchar(255),
    primary key (id_)
);

create table act_re_procdef (
    id_ varchar(64) not null,
    rev_ integer,
    category_ varchar(255),
    name_ varchar(255),
    key_ varchar(255) not null,
    version_ integer not null,
    deployment_id_ varchar(64),
    resource_name_ varchar(4000),
    dgrm_resource_name_ varchar(4000),
    description_ varchar(4000),
    has_start_form_key_ boolean,
    has_graphical_notation_ boolean,
    suspension_state_ integer,
    tenant_id_ varchar(255) default '',
    derived_from_ varchar(64),
    derived_from_root_ varchar(64),
    derived_version_ integer not null default 0,
    engine_version_ varchar(255),
    primary key (id_)
);

create table act_evt_log (
    log_nr_ serial primary key,
    type_ varchar(64),
    proc_def_id_ varchar(64),
    proc_inst_id_ varchar(64),
    execution_id_ varchar(64),
    task_id_ varchar(64),
    time_stamp_ timestamp not null,
    user_id_ varchar(255),
    data_ bytea,
    lock_owner_ varchar(255),
    lock_time_ timestamp null,
    is_processed_ smallint default 0
);

create table act_procdef_info (
	id_ varchar(64) not null,
    proc_def_id_ varchar(64) not null,
    rev_ integer,
    info_json_id_ varchar(64),
    primary key (id_)
);

create table act_ru_actinst (
    id_ varchar(64) not null,
    rev_ integer default 1,
    proc_def_id_ varchar(64) not null,
    proc_inst_id_ varchar(64) not null,
    execution_id_ varchar(64) not null,
    act_id_ varchar(255) not null,
    task_id_ varchar(64),
    call_proc_inst_id_ varchar(64),
    act_name_ varchar(255),
    act_type_ varchar(255) not null,
    assignee_ varchar(255),
    completed_by_ varchar(255),
    start_time_ timestamp not null,
    end_time_ timestamp,
    duration_ bigint,
    transaction_order_ integer,
    delete_reason_ varchar(4000),
    tenant_id_ varchar(255) default '',
    primary key (id_)
);

create index act_idx_exec_buskey on act_ru_execution(business_key_);
create index act_idx_exe_root on act_ru_execution(root_proc_inst_id_);
create index act_idx_exec_ref_id_ on act_ru_execution(reference_id_);
create index act_idx_variable_task_id on act_ru_variable(task_id_);

create index act_idx_bytear_depl on act_ge_bytearray(deployment_id_);

create index act_idx_ru_acti_start on act_ru_actinst(start_time_);
create index act_idx_ru_acti_end on act_ru_actinst(end_time_);
create index act_idx_ru_acti_proc on act_ru_actinst(proc_inst_id_);
create index act_idx_ru_acti_proc_act on act_ru_actinst(proc_inst_id_, act_id_);
create index act_idx_ru_acti_exec on act_ru_actinst(execution_id_);
create index act_idx_ru_acti_exec_act on act_ru_actinst(execution_id_, act_id_);
create index act_idx_ru_acti_task on act_ru_actinst(task_id_);

alter table act_ge_bytearray
    add constraint act_fk_bytearr_depl
    foreign key (deployment_id_) 
    references act_re_deployment (id_);

alter table act_re_procdef
    add constraint act_uniq_procdef
    unique (key_,version_, derived_version_, tenant_id_);
    
create index act_idx_exe_procinst on act_ru_execution(proc_inst_id_);
alter table act_ru_execution
    add constraint act_fk_exe_procinst 
    foreign key (proc_inst_id_) 
    references act_ru_execution (id_);

create index act_idx_exe_parent on act_ru_execution(parent_id_);
alter table act_ru_execution
    add constraint act_fk_exe_parent
    foreign key (parent_id_) 
    references act_ru_execution (id_);
    
create index act_idx_exe_super on act_ru_execution(super_exec_);
alter table act_ru_execution
    add constraint act_fk_exe_super
    foreign key (super_exec_) 
    references act_ru_execution (id_);
    

create index act_idx_exe_procdef on act_ru_execution(proc_def_id_); 
alter table act_ru_execution
    add constraint act_fk_exe_procdef 
    foreign key (proc_def_id_) 
    references act_re_procdef (id_);    
    

create index act_idx_tskass_task on act_ru_identitylink(task_id_);
alter table act_ru_identitylink
    add constraint act_fk_tskass_task
    foreign key (task_id_) 
    references act_ru_task (id_);
    
create index act_idx_athrz_procedef on act_ru_identitylink(proc_def_id_);
alter table act_ru_identitylink
    add constraint act_fk_athrz_procedef
    foreign key (proc_def_id_) 
    references act_re_procdef (id_);
    
create index act_idx_idl_procinst on act_ru_identitylink(proc_inst_id_);
alter table act_ru_identitylink
    add constraint act_fk_idl_procinst
    foreign key (proc_inst_id_) 
    references act_ru_execution (id_);    
    
create index act_idx_task_exec on act_ru_task(execution_id_);
alter table act_ru_task
    add constraint act_fk_task_exe
    foreign key (execution_id_)
    references act_ru_execution (id_);
    
create index act_idx_task_procinst on act_ru_task(proc_inst_id_);
alter table act_ru_task
    add constraint act_fk_task_procinst
    foreign key (proc_inst_id_)
    references act_ru_execution (id_);
    
create index act_idx_task_procdef on act_ru_task(proc_def_id_);
alter table act_ru_task
  add constraint act_fk_task_procdef
  foreign key (proc_def_id_)
  references act_re_procdef (id_);
  
create index act_idx_var_exe on act_ru_variable(execution_id_);
alter table act_ru_variable 
    add constraint act_fk_var_exe
    foreign key (execution_id_) 
    references act_ru_execution (id_);

create index act_idx_var_procinst on act_ru_variable(proc_inst_id_);
alter table act_ru_variable
    add constraint act_fk_var_procinst
    foreign key (proc_inst_id_)
    references act_ru_execution(id_);

create index act_idx_job_execution_id on act_ru_job(execution_id_);
alter table act_ru_job 
    add constraint act_fk_job_execution 
    foreign key (execution_id_) 
    references act_ru_execution (id_);

create index act_idx_job_process_instance_id on act_ru_job(process_instance_id_);
alter table act_ru_job 
    add constraint act_fk_job_process_instance 
    foreign key (process_instance_id_) 
    references act_ru_execution (id_);

create index act_idx_job_proc_def_id on act_ru_job(proc_def_id_);
alter table act_ru_job 
    add constraint act_fk_job_proc_def
    foreign key (proc_def_id_) 
    references act_re_procdef (id_);

create index act_idx_timer_job_execution_id on act_ru_timer_job(execution_id_);
alter table act_ru_timer_job 
    add constraint act_fk_timer_job_execution 
    foreign key (execution_id_) 
    references act_ru_execution (id_);

create index act_idx_timer_job_process_instance_id on act_ru_timer_job(process_instance_id_);
alter table act_ru_timer_job 
    add constraint act_fk_timer_job_process_instance 
    foreign key (process_instance_id_) 
    references act_ru_execution (id_);

create index act_idx_timer_job_proc_def_id on act_ru_timer_job(proc_def_id_);
alter table act_ru_timer_job 
    add constraint act_fk_timer_job_proc_def
    foreign key (proc_def_id_) 
    references act_re_procdef (id_);

create index act_idx_suspended_job_execution_id on act_ru_suspended_job(execution_id_);    
alter table act_ru_suspended_job 
    add constraint act_fk_suspended_job_execution 
    foreign key (execution_id_) 
    references act_ru_execution (id_);
    
create index act_idx_suspended_job_process_instance_id on act_ru_suspended_job(process_instance_id_);    
alter table act_ru_suspended_job 
    add constraint act_fk_suspended_job_process_instance 
    foreign key (process_instance_id_) 
    references act_ru_execution (id_);

create index act_idx_suspended_job_proc_def_id on act_ru_suspended_job(proc_def_id_);    
alter table act_ru_suspended_job 
    add constraint act_fk_suspended_job_proc_def
    foreign key (proc_def_id_) 
    references act_re_procdef (id_);

create index act_idx_deadletter_job_execution_id on act_ru_deadletter_job(execution_id_);      
alter table act_ru_deadletter_job 
    add constraint act_fk_deadletter_job_execution 
    foreign key (execution_id_) 
    references act_ru_execution (id_);
 
create index act_idx_deadletter_job_process_instance_id on act_ru_deadletter_job(process_instance_id_);        
alter table act_ru_deadletter_job 
    add constraint act_fk_deadletter_job_process_instance 
    foreign key (process_instance_id_) 
    references act_ru_execution (id_);
    
create index act_idx_deadletter_job_proc_def_id on act_ru_deadletter_job(proc_def_id_);    
alter table act_ru_deadletter_job 
    add constraint act_fk_deadletter_job_proc_def
    foreign key (proc_def_id_) 
    references act_re_procdef (id_);
    
alter table act_ru_event_subscr
    add constraint act_fk_event_exec
    foreign key (execution_id_)
    references act_ru_execution(id_);

create index act_idx_model_source on act_re_model(editor_source_value_id_);
alter table act_re_model 
    add constraint act_fk_model_source 
    foreign key (editor_source_value_id_) 
    references act_ge_bytearray (id_);

create index act_idx_model_source_extra on act_re_model(editor_source_extra_value_id_);
alter table act_re_model 
    add constraint act_fk_model_source_extra 
    foreign key (editor_source_extra_value_id_) 
    references act_ge_bytearray (id_);
    
create index act_idx_model_deployment on act_re_model(deployment_id_);    
alter table act_re_model 
    add constraint act_fk_model_deployment 
    foreign key (deployment_id_) 
    references act_re_deployment (id_);        

create index act_idx_procdef_info_json on act_procdef_info(info_json_id_);
alter table act_procdef_info 
    add constraint act_fk_info_json_ba 
    foreign key (info_json_id_) 
    references act_ge_bytearray (id_);

create index act_idx_procdef_info_proc on act_procdef_info(proc_def_id_);
alter table act_procdef_info 
    add constraint act_fk_info_procdef 
    foreign key (proc_def_id_) 
    references act_re_procdef (id_);
    
alter table act_procdef_info
    add constraint act_uniq_info_procdef
    unique (proc_def_id_);

insert into act_ge_property
values ('schema.version', '7.2.0.2', 1);

insert into act_ge_property
values ('schema.history', 'create(7.2.0.2)', 1);
