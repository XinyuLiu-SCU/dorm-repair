do $$ begin
 if current_database() !~ '^dorm_repair_v2_(dev|test(_[a-z0-9]+)?)$' then raise exception 'Unsafe target'; end if;
end $$;
select guard_key from public.system_guard where guard_key='rbac-admin' for update;
do $$ begin
 if exists(select 1 from public.sys_user) then
  if to_regclass('pg_temp.c6_confirmed_grants') is null then raise exception 'C6 requires an explicit trusted authorization mapping for existing users'; end if;
 else
  if exists(select 1 from public.system_guard where initialized_user_id is not null) or exists(select 1 from public.sys_role where code not in ('ADMIN','STUDENT','REPAIRER')) then raise exception 'Empty user catalog has unknown initialization or custom roles'; end if;
  if (select count(*) from public.sys_role)<>3 or exists(select 1 from public.sys_role_permission rp join public.sys_role r on r.id=rp.role_id join public.sys_permission p on p.id=rp.permission_id where (r.code='ADMIN' and not p.available) or (r.code in ('STUDENT','REPAIRER') and p.code not in ('profile:read','profile:write','profile:password','legacy:student','legacy:repairer','category:read','space:read','facility:read','category:write','space:write','facility:write'))) then raise exception 'Unknown seed grants'; end if;
 end if;
end $$;
-- The mapping is a connection-local, explicitly supplied complete desired catalog-write role mapping.
-- No automatically inferred keeper, no historical reconstruction.
create temporary table if not exists c6_confirmed_grants(role_id bigint,permission_code varchar(80),primary key(role_id,permission_code)) on commit drop;
do $$ begin
 if exists(select 1 from c6_confirmed_grants g left join public.sys_role r on r.id=g.role_id where r.id is null or g.permission_code not in ('category:write','space:write','facility:write')) then raise exception 'Invalid confirmed mapping'; end if;
end $$;
create temporary table c6_affected on commit drop as select distinct ur.user_id from public.sys_user_role ur join public.sys_role_permission rp on rp.role_id=ur.role_id join public.sys_permission p on p.id=rp.permission_id where p.code in ('category:write','space:write','facility:write') union select ur.user_id from public.sys_user_role ur join c6_confirmed_grants g on g.role_id=ur.role_id;
-- Empty catalog: only normalize the known seed templates.
insert into c6_confirmed_grants select r.id,p.code from public.sys_role r cross join public.sys_permission p where not exists(select 1 from public.sys_user) and r.code='ADMIN' and p.code in ('category:write','space:write','facility:write') on conflict do nothing;
delete from public.sys_role_permission rp using public.sys_permission p where rp.permission_id=p.id and p.code in ('category:write','space:write','facility:write');
insert into public.sys_role_permission select g.role_id,p.id from c6_confirmed_grants g join public.sys_permission p on p.code=g.permission_code;
do $$ begin
 if exists(select 1 from public.sys_user) and not exists(select 1 from public.sys_user u where u.enabled and not exists(select 1 from public.sys_permission p where p.available and not exists(select 1 from public.sys_user_role ur join public.sys_role r on r.id=ur.role_id and r.enabled join public.sys_role_permission rp on rp.role_id=r.id where ur.user_id=u.id and rp.permission_id=p.id))) then raise exception 'Confirmed mapping loses last custodian'; end if;
 if not exists(select 1 from public.sys_user) then
  insert into public.sys_role_permission select r.id,p.id from public.sys_role r cross join public.sys_permission p where r.code='ADMIN' and p.available on conflict do nothing;
 end if;
end $$;
update public.sys_user set auth_version=auth_version+1,version=version+1 where id in(select user_id from c6_affected);
update public.sys_role set version=version+1 where id in(select role_id from c6_confirmed_grants) or code in ('STUDENT','REPAIRER');
insert into public.audit_event(action,target_type,request_id,details) values ('C6_AUTHORIZATION_RECONCILED','RBAC','migration_c6',jsonb_build_object('affectedUsers',(select count(*) from c6_affected),'mode',case when exists(select 1 from public.sys_user) then 'EXPLICIT_MAPPING' else 'EMPTY_SEEDS' end));
