do $$ begin if current_database() !~ '^dorm_repair_v2_(dev|test(_[a-z0-9]+)?)$' then raise exception 'Unsafe target'; end if; end $$;
alter table public.campus add constraint uq_campus_name unique(name);
create function public.prevent_catalog_name_overlap() returns trigger language plpgsql as $$ begin
 perform guard_key from public.system_guard where guard_key='foundation-catalog' for update;
 if tg_table_name='repair_item' and exists(select 1 from public.repair_category where name=new.name) then raise exception 'Item must not duplicate a category name' using errcode='23514'; end if;
 if tg_table_name='repair_category' and exists(select 1 from public.repair_item where name=new.name) then raise exception 'Category must not duplicate an item name' using errcode='23514'; end if;
 return new; end $$;
create trigger item_name_guard before insert or update on public.repair_item for each row execute function public.prevent_catalog_name_overlap();
create trigger category_name_guard before insert or update on public.repair_category for each row execute function public.prevent_catalog_name_overlap();
