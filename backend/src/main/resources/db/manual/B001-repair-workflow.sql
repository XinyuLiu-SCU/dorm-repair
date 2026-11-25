-- one-time migration; not executed automatically. requires explicit database approval.
-- execute with psql -v on_error_stop=1 in a maintenance window.
begin;
lock table repair_order in access exclusive mode;
alter table repair_order add column process_instance_id varchar(64);
create unique index ux_repair_order_process_instance_id on repair_order (process_instance_id) where process_instance_id is not null;
create table repair_order_legacy (
    order_id bigint primary key references repair_order(id),
    recorded_at timestamp not null default current_timestamp
);
insert into repair_order_legacy (order_id) select id from repair_order;
commit;
