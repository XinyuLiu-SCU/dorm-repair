-- Failure injection is test-only and must never be added to application migration locations.
create table public.v2_failure_probe (id bigint primary key);
insert into public.v2_failure_probe(id) values (1);
select 1 / 0;
