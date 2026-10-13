-- 001_check_current_schema.sql
-- READ ONLY. Changes nothing. Run each block in Supabase > SQL Editor and read the result.
-- Send me the results (a screenshot of each is fine) if anything looks wrong.

-- 1. Which tables exist, and is Row Level Security (RLS) switched on?
--    rls_on must be TRUE for both profiles and sessions. FALSE means anyone with the public key can read them.
select c.relname as table_name, c.relrowsecurity as rls_on, c.relforcerowsecurity as rls_forced
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r'
order by c.relname;

-- 2. Columns and types of the two tables the app uses.
--    Expected profiles: id, sessions, total_sec, streak, full_streak, last_date, today_done,
--    today_done_date, today_full_counted, mods, defaults, updated_at.
--    Expected sessions: user_id, mod, sec, sets, rep_sec, rest_sec, mode, med_min, ts.
select table_name, column_name, data_type, is_nullable, column_default
from information_schema.columns
where table_schema = 'public' and table_name in ('profiles', 'sessions')
order by table_name, ordinal_position;

-- 3. Every RLS policy on those tables.
--    Policies are combined with OR. A single policy with using = true (for example
--    "Enable read access for all users") makes the whole table readable by everyone.
select tablename, policyname, cmd, roles, permissive, qual as using_expression, with_check
from pg_policies
where schemaname = 'public' and tablename in ('profiles', 'sessions')
order by tablename, cmd, policyname;

-- 4. What the signed-out public key (anon) is allowed to do. Expected: no rows at all.
select grantee, table_name, privilege_type
from information_schema.role_table_grants
where table_schema = 'public' and table_name in ('profiles', 'sessions') and grantee = 'anon'
order by table_name, privilege_type;

-- 5. Indexes and constraints.
select tablename, indexname, indexdef
from pg_indexes
where schemaname = 'public' and tablename in ('profiles', 'sessions')
order by tablename, indexname;

select conrelid::regclass as table_name, conname, contype, pg_get_constraintdef(oid) as definition
from pg_constraint
where connamespace = 'public'::regnamespace and conrelid::regclass::text in ('profiles', 'sessions')
order by 1, 2;

-- 6. Duplicate trainings (same user, same time, same training). Expected: no rows.
select user_id, ts, mod, count(*) as copies
from public.sessions
group by user_id, ts, mod
having count(*) > 1
order by copies desc
limit 50;

-- 7. Rows that look wrong. Expected: all zeros.
select
  (select count(*) from public.sessions where user_id is null) as sessions_without_user,
  (select count(*) from public.sessions where mod not in ('blink','fidget','breath','meditate')) as sessions_unknown_training,
  (select count(*) from public.sessions where sec < 0 or sec > 36000) as sessions_odd_length,
  (select count(*) from public.profiles where id is null) as profiles_without_id;

-- 8. Size of the data.
select
  (select count(*) from public.profiles) as profiles,
  (select count(*) from public.sessions) as sessions,
  (select coalesce(max(pg_column_size(defaults)), 0) from public.profiles) as biggest_defaults_bytes;

-- 9. Can a signed-out visitor (anon) read, add, change or delete anything?
--    Expected: every column below is FALSE. Any TRUE means the table is open to the public.
select t.tbl as table_name,
  has_table_privilege('anon', t.tbl, 'select') as anon_can_read,
  has_table_privilege('anon', t.tbl, 'insert') as anon_can_insert,
  has_table_privilege('anon', t.tbl, 'update') as anon_can_update,
  has_table_privilege('anon', t.tbl, 'delete') as anon_can_delete
from (values ('public.profiles'), ('public.sessions')) as t(tbl);
