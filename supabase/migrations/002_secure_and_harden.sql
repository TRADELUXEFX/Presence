-- 002_secure_and_harden.sql
-- Safe to run more than once. Run 001_check_current_schema.sql first and read it.
-- It works with both the Android app and the website, which use the same two tables.
--
-- What it does
--   1. Makes sure both tables exist with the columns the app and website write (never changes existing data).
--   2. Switches on Row Level Security, removes every old policy on these two tables, and adds strict ones:
--      a signed-in person can read and write ONLY their own rows. Signed-out visitors can do nothing.
--   3. Stops the same training being saved twice (needed because a saved training can be retried).
--   4. Adds an index so History loads fast, and sanity limits on the values.
--
-- Run it as a whole in Supabase > SQL Editor. If it stops with an error, nothing is half-done: it runs
-- as one transaction.

begin;

-- 1. Tables and columns ------------------------------------------------------------------------------

create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade
);
alter table public.profiles add column if not exists sessions           integer      not null default 0;
alter table public.profiles add column if not exists total_sec          bigint       not null default 0;
alter table public.profiles add column if not exists streak             integer      not null default 0;
alter table public.profiles add column if not exists full_streak        integer      not null default 0;
alter table public.profiles add column if not exists last_date          text;
alter table public.profiles add column if not exists today_done         jsonb        not null default '{}'::jsonb;
alter table public.profiles add column if not exists today_done_date    text;
alter table public.profiles add column if not exists today_full_counted boolean      not null default false;
alter table public.profiles add column if not exists mods               jsonb        not null default '{"blink":0,"fidget":0,"breath":0,"meditate":0}'::jsonb;
alter table public.profiles add column if not exists defaults           jsonb        not null default '{}'::jsonb;
alter table public.profiles add column if not exists updated_at         timestamptz  not null default now();

create table if not exists public.sessions (
  user_id uuid not null references auth.users (id) on delete cascade
);
alter table public.sessions add column if not exists mod      text;
alter table public.sessions add column if not exists sec      integer;
alter table public.sessions add column if not exists sets     integer;
alter table public.sessions add column if not exists rep_sec  integer;
alter table public.sessions add column if not exists rest_sec integer;
alter table public.sessions add column if not exists mode     text;
alter table public.sessions add column if not exists med_min  integer;
alter table public.sessions add column if not exists ts       bigint;

-- 2. Row Level Security ------------------------------------------------------------------------------

alter table public.profiles enable row level security;
alter table public.sessions enable row level security;

-- Remove EVERY existing policy on these two tables. Policies are combined with OR, so one old
-- permissive policy (for example "using (true)") would undo all the rules below.
do $$
declare r record;
begin
  for r in
    select policyname, tablename from pg_policies
    where schemaname = 'public' and tablename in ('profiles', 'sessions')
  loop
    execute format('drop policy %I on public.%I', r.policyname, r.tablename);
  end loop;
end $$;

-- profiles: one row per person. Read, create and change your own. No delete (removing the account removes it).
create policy "profiles: read own"   on public.profiles for select to authenticated using      (auth.uid() = id);
create policy "profiles: insert own" on public.profiles for insert to authenticated with check (auth.uid() = id);
create policy "profiles: update own" on public.profiles for update to authenticated using      (auth.uid() = id) with check (auth.uid() = id);

-- sessions: the training history. Read your own, add your own. History is never edited or deleted by the apps.
create policy "sessions: read own"   on public.sessions for select to authenticated using      (auth.uid() = user_id);
create policy "sessions: insert own" on public.sessions for insert to authenticated with check (auth.uid() = user_id);

-- Table permissions: signed-out visitors (anon) get nothing; signed-in people get only what the policies allow.
revoke all on public.profiles from anon;
revoke all on public.sessions from anon;
revoke all on public.profiles from authenticated;
revoke all on public.sessions from authenticated;
grant select, insert, update on public.profiles to authenticated;
grant select, insert         on public.sessions to authenticated;

-- 3. No double-saved trainings -----------------------------------------------------------------------
-- The app retries a save when the answer is lost on a bad connection. Remove exact copies that already exist
-- (keeps one), then make a repeat impossible. The app treats the "already exists" reply as saved.
delete from public.sessions a
using public.sessions b
where a.ctid > b.ctid
  and a.user_id = b.user_id
  and a.ts = b.ts
  and a.mod = b.mod;

create unique index if not exists sessions_user_ts_mod_uniq on public.sessions (user_id, ts, mod);

-- History loads newest first for one person.
create index if not exists sessions_user_ts_idx on public.sessions (user_id, ts desc);

-- 4. Sanity limits (NOT VALID: they apply to new and changed rows, and never fail on old rows) -----------
-- Dropped and re-added each run, so running this file again always leaves the newest limits in place.
-- The time limit is a full day (86400 s), not 10 hours: the app never caps a training's length itself, and a row the
-- database refuses would stay in the phone's save queue and be retried forever.
alter table public.sessions drop constraint if exists sessions_mod_check;
alter table public.sessions drop constraint if exists sessions_sec_check;
alter table public.sessions drop constraint if exists sessions_ts_check;
alter table public.profiles drop constraint if exists profiles_counts_check;
alter table public.profiles drop constraint if exists profiles_json_size_check;

alter table public.sessions add constraint sessions_mod_check
  check (mod in ('blink', 'fidget', 'breath', 'meditate')) not valid;
alter table public.sessions add constraint sessions_sec_check
  check (sec >= 0 and sec <= 86400) not valid;
alter table public.sessions add constraint sessions_ts_check
  check (ts > 0) not valid;
alter table public.profiles add constraint profiles_counts_check
  check (sessions >= 0 and total_sec >= 0 and streak >= 0 and full_streak >= 0) not valid;
-- Anyone signed in can write their own row, so cap the free-form JSON columns (settings, saved lists, ratings
-- live in "defaults"). 1 MB is far above real use and stops one account filling the database.
alter table public.profiles add constraint profiles_json_size_check
  check (octet_length(defaults::text) <= 1000000
     and octet_length(mods::text) <= 10000
     and octet_length(today_done::text) <= 10000) not valid;

commit;

-- After it finishes, run 001_check_current_schema.sql again:
--   block 1: rls_on = true for both tables
--   block 3: exactly the 5 policies named above
--   block 4: no rows
--   block 9: "permission denied" (or 0) for the signed-out visitor
