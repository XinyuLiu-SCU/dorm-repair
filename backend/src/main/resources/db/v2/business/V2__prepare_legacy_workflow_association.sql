do $$ begin
 if current_database() !~ '^dorm_repair_v2_(dev|test(_[a-z0-9]+)?)$' then
  raise exception 'Refusing non-isolated V2 migration target';
 end if;
end $$;
-- No engine table or process instance is created here.
alter table public.repair_order add column if not exists process_instance_id varchar(64);
create unique index if not exists ux_repair_order_process_instance_id on public.repair_order(process_instance_id) where process_instance_id is not null;
create table if not exists public.repair_order_legacy (
 order_id bigint primary key references public.repair_order(id),
 recorded_at timestamp not null default current_timestamp
);
insert into public.repair_order_legacy(order_id)
 select id from public.repair_order where process_instance_id is null on conflict(order_id) do nothing;
