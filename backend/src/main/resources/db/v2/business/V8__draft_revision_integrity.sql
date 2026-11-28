do $$ begin if current_database() !~ '^dorm_repair_v2_(dev|test(_[a-z0-9]+)?)$' then raise exception 'Unsafe D target'; end if; end $$;
alter table public.repair_draft_revision add constraint uq_draft_revision_parent unique(id,draft_id);
alter table public.repair_draft_revision add constraint fk_revision_same_parent foreign key(previous_revision_id,draft_id) references public.repair_draft_revision(id,draft_id) on delete restrict;
alter table public.repair_draft add constraint fk_draft_current_revision foreign key(id,current_revision) references public.repair_draft_revision(draft_id,revision_no) deferrable initially deferred;
create function public.check_draft_revision_sequence() returns trigger language plpgsql as $$ declare previous_no integer; begin
 if new.previous_revision_id is null then if new.revision_no<>1 then raise exception 'First revision must be 1' using errcode='23514'; end if;
 else select revision_no into previous_no from public.repair_draft_revision where id=new.previous_revision_id and draft_id=new.draft_id;
 if previous_no is null or new.revision_no<>previous_no+1 then raise exception 'Invalid draft revision chain' using errcode='23514'; end if; end if;return new;end $$;
create trigger draft_revision_sequence before insert on public.repair_draft_revision for each row execute function public.check_draft_revision_sequence();
