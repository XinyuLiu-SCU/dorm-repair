\set ON_ERROR_STOP on
-- mandatory explicit expected_db; a missing variable fails before any schema changes.
begin;
select 1 / case when current_database() = :'expected_db' then 1 else 0 end as verified_target;
set local lock_timeout = '5s';
set local statement_timeout = '120s';
lock table sys_user, repair_order, repair_record in access exclusive mode;
do $$ begin
    if exists(select 1 from information_schema.tables where table_schema='public' and table_name like 'act_%')
       or exists(select 1 from information_schema.columns where table_schema='public' and table_name='repair_order' and column_name='process_instance_id')
       or to_regclass('public.dormrepair_schema_history') is not null then
        raise exception 'expected an unmigrated database; do not rerun or partially apply';
    end if;
end $$;
create temporary table migration_users_baseline on commit drop as table sys_user;
create temporary table migration_orders_baseline on commit drop as table repair_order;
create temporary table migration_records_baseline on commit drop as table repair_record;
\ir 010-common.sql
\ir 020-engine.sql
\ir 030-history.sql
\ir 040-business-association.sql
create table dormrepair_schema_history (
    version varchar(50) primary key,
    description text not null,
    applied_at timestamp not null default current_timestamp
);
insert into dormrepair_schema_history(version,description) values ('D001-flowable-7.2.0','common/engine/history and B001 business association; checksums in MANIFEST.json');
do $$ begin
    if exists((select * from sys_user except select * from migration_users_baseline)
        union all (select * from migration_users_baseline except select * from sys_user))
    or exists((select id,student_id,title,description,location,status,created_at,updated_at,repairer_id from repair_order
        except select id,student_id,title,description,location,status,created_at,updated_at,repairer_id from migration_orders_baseline)
        union all (select id,student_id,title,description,location,status,created_at,updated_at,repairer_id from migration_orders_baseline
        except select id,student_id,title,description,location,status,created_at,updated_at,repairer_id from repair_order))
    or exists((select * from repair_record except select * from migration_records_baseline)
        union all (select * from migration_records_baseline except select * from repair_record)) then
        raise exception 'existing business data changed';
    end if;
    if (select count(*) from repair_order_legacy) <> (select count(*) from migration_orders_baseline)
       or exists(select 1 from repair_order where process_instance_id is not null) then
        raise exception 'legacy registry mismatch';
    end if;
end $$;
commit;
