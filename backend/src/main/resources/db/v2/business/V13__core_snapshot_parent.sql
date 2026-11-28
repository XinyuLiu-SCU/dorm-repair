do $$ begin if current_database() !~ '^dorm_repair_v2_(dev|test(_[a-z0-9]+)?)$' then raise exception 'Unsafe core target'; end if; end $$;
-- An official conversion can only reference a revision belonging to that same draft.
alter table public.v2_order add constraint fk_core_revision_parent foreign key(revision_id,draft_id) references public.repair_draft_revision(id,draft_id);
create index ix_core_student_orders on public.repair_order(student_id,id);
create index ix_core_worker_orders on public.repair_order(repairer_id,status,id);
