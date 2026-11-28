do $$ begin if current_database() !~ '^dorm_repair_v2_(dev|test(_[a-z0-9]+)?)$' then raise exception 'Unsafe D target'; end if; end $$;
-- R2 permits a verified residency with no declared end date.
alter table public.room_resident alter column valid_to drop not null;
