do $$ begin
 if current_database() !~ '^dorm_repair_v2_(dev|test(_[a-z0-9]+)?)$' then raise exception 'Unsafe D target'; end if;
 if not exists(select 1 from public.sys_user) then
  if (select count(*) from public.sys_permission where available)<>20 or exists(select 1 from public.sys_role where code not in ('ADMIN','STUDENT','REPAIRER')) then raise exception 'Unknown empty identity templates'; end if;
  if exists(select 1 from public.sys_permission p where p.available and not exists(select 1 from public.sys_role_permission rp join public.sys_role r on r.id=rp.role_id where r.code='ADMIN' and rp.permission_id=p.id)) then raise exception 'Incomplete administrator template'; end if;
  if exists(select 1 from public.sys_role_permission rp join public.sys_role r on r.id=rp.role_id join public.sys_permission p on p.id=rp.permission_id where r.code in ('STUDENT','REPAIRER') and not(p.code in ('profile:read','profile:write','profile:password','category:read','space:read','facility:read') or(r.code='STUDENT' and p.code in ('legacy:student','address:own','residency:own','draft:own')) or(r.code='REPAIRER' and p.code='legacy:repairer'))) then raise exception 'Unknown shared seed grants'; end if;
  if (select count(*) from public.sys_role_permission rp join public.sys_role r on r.id=rp.role_id where r.code='STUDENT')<>10 or (select count(*) from public.sys_role_permission rp join public.sys_role r on r.id=rp.role_id where r.code='REPAIRER')<>7 then raise exception 'Incomplete seed grants'; end if;
 end if;
end $$;
