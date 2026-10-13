-- verify.sql: READ ONLY. One result table. Every OK/PROBLEM row must say OK.
-- Run it in Supabase > SQL Editor after 002_secure_and_harden.sql.

select 'rls_on: ' || c.relname as check_name,
       case when c.relrowsecurity then 'OK' else 'PROBLEM' end as result,
       c.relrowsecurity::text as detail
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relname in ('profiles','sessions') and c.relkind = 'r'

union all
select 'policy: ' || tablename, 'INFO', policyname || ' | ' || cmd || ' | ' || coalesce(qual, with_check, '')
from pg_policies
where schemaname = 'public' and tablename in ('profiles','sessions')

union all
select 'policy count (expected 5)',
       case when count(*) = 5 then 'OK' else 'PROBLEM' end,
       count(*)::text
from pg_policies
where schemaname = 'public' and tablename in ('profiles','sessions')

union all
select 'open policy (expected none)',
       case when count(*) = 0 then 'OK' else 'PROBLEM' end,
       coalesce(string_agg(policyname, ', '), 'none')
from pg_policies
where schemaname = 'public' and tablename in ('profiles','sessions')
  and (qual = 'true' or with_check = 'true')

union all
select 'anon (signed-out) access to ' || t.tbl || ' (expected none)',
       case when has_table_privilege('anon', t.tbl, 'select')
              or has_table_privilege('anon', t.tbl, 'insert')
              or has_table_privilege('anon', t.tbl, 'update')
              or has_table_privilege('anon', t.tbl, 'delete')
            then 'PROBLEM' else 'OK' end,
       'read/insert/update/delete all blocked'
from (values ('public.profiles'), ('public.sessions')) as t(tbl)

union all
select 'unique index on sessions',
       case when count(*) = 1 then 'OK' else 'PROBLEM' end,
       count(*)::text
from pg_indexes
where schemaname = 'public' and indexname = 'sessions_user_ts_mod_uniq'

union all
select 'duplicate trainings (expected 0)',
       case when count(*) = 0 then 'OK' else 'PROBLEM' end,
       count(*)::text
from (select 1 from public.sessions group by user_id, ts, mod having count(*) > 1) d;
