-- read-only checks, safe before application activation.
select current_database(), current_user;
select version, description, applied_at from dormrepair_schema_history;
select name_, value_ from act_ge_property where name_ like '%schema.version' order by name_;
select count(*) as act_tables from information_schema.tables where table_schema='public' and table_name like 'act_%';
select id, status, process_instance_id from repair_order where id in (1,2,3) order by id;
select count(*) as orders, count(process_instance_id) as linked from repair_order;
select count(*) as legacy_orders from repair_order_legacy;
select count(*) as missing_legacy from repair_order o where o.process_instance_id is null and not exists(select 1 from repair_order_legacy l where l.order_id=o.id);
select count(*) as unexpected_legacy_association from repair_order o join repair_order_legacy l on l.order_id=o.id where o.process_instance_id is not null;
select count(*) as flw_tables from information_schema.tables where table_schema='public' and table_name like 'flw_%';
