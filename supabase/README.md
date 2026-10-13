# Supabase

The Android app (`app/`) and the website (`www/`) share one Supabase project and two tables: `profiles` and `sessions`.
Nothing else in the database is used. Sign-in uses Supabase Auth (email and password).

The app's public "anon" key is in `Api.kt` and in `www/index.html`. It is public by design, so **all protection is the
Row Level Security (RLS) rules below**. Never put the `service_role` key in the app or the website.

## Files

| File | Purpose |
|---|---|
| `migrations/001_check_current_schema.sql` | Read only. Shows tables, columns, policies, who can read what. Run before and after 002. |
| `migrations/002_secure_and_harden.sql` | Creates or completes both tables, turns on RLS, replaces every policy, adds the unique index and limits. Safe to run again. |
| `verify.sql` | Read only. One result table, every row must say `OK`. |

Run each in **Supabase > SQL Editor**. Order: `001`, `002`, then `verify.sql`.
Note: the SQL Editor shows only the **last** query's result of a script, so run the blocks of 001 one at a time,
or use `verify.sql`.

## Tables

### `profiles` (one row per person)

| Column | Type | Default | Written by the app as |
|---|---|---|---|
| `id` | uuid, primary key, references `auth.users` (cascade delete) | | the signed-in user's id |
| `sessions` | integer | 0 | total trainings done |
| `total_sec` | bigint | 0 | total seconds trained |
| `streak` | integer | 0 | days in a row |
| `full_streak` | integer | 0 | days in a row with all four trainings |
| `last_date` | text, nullable | | day of the last training, as `Mon Oct 12 2026` (JavaScript `toDateString`) |
| `today_done` | jsonb | `{}` | which trainings are ticked today, plus `order` |
| `today_done_date` | text, nullable | | day `today_done` belongs to, same format |
| `today_full_counted` | boolean | false | today's full-day streak already counted |
| `mods` | jsonb | `{"blink":0,"fidget":0,"breath":0,"meditate":0}` | count per training |
| `defaults` | jsonb | `{}` | settings, saved trainings and sessions (`saved`, `saved_removed`), how-it-felt ratings (`feel`), recent trainings, per-training defaults |
| `updated_at` | timestamptz | now() | UTC time of the save |

### `sessions` (training history, one row per finished training)

| Column | Type | Meaning |
|---|---|---|
| `user_id` | uuid, references `auth.users` (cascade delete) | owner |
| `mod` | text | `blink`, `fidget`, `breath` or `meditate` |
| `sec` | integer | seconds trained (rest is not counted) |
| `sets`, `rep_sec`, `rest_sec` | integer, nullable | blink, fidget and breath settings |
| `mode` | text, nullable | `open` or `closed` (blink and fidget) |
| `med_min` | integer, nullable | meditation minutes |
| `ts` | bigint | finish time, milliseconds since 1970 |

Duplicates are impossible: unique index on `(user_id, ts, mod)`. There is an index on `(user_id, ts desc)` for History.

## Security rules (RLS)

Signed-out visitors (`anon`) have **no** access to either table. Signed-in people (`authenticated`) get:

| Table | Allowed | Rule |
|---|---|---|
| `profiles` | read, insert, update | only the row where `auth.uid() = id` |
| `sessions` | read, insert | only rows where `auth.uid() = user_id` |

No one can delete a profile row or edit or delete history through the API. Deleting an account in Supabase Auth
removes its rows (cascade).

Policies are combined with OR, so one leftover "using (true)" policy would expose everything.
`002` deletes every existing policy on these two tables before creating the five above.
`verify.sql` fails if any policy is that open.

## Limits (checked on new and changed rows)

- `sessions.mod` is one of the four trainings; `sessions.sec` is 0 to 86400; `sessions.ts` is above 0.
- `profiles` counts (`sessions`, `total_sec`, `streak`, `full_streak`) are not negative.
- `defaults` is at most 1 MB as text; `mods` and `today_done` at most 10 KB.

Longest possible training in the app: 6000 s (300 s per rep, 20 sets). The app also clamps `sec` to 86400.

## How the app talks to the database (`Api.kt`)

| Action | Request | Notes |
|---|---|---|
| Sign in, sign up, reset, refresh | `/auth/v1/token`, `/signup`, `/recover` | tokens kept on the phone |
| Load profile | `GET /rest/v1/profiles?id=eq.<uid>` | empty list means no row yet |
| Save profile | `POST /rest/v1/profiles?on_conflict=id` with `resolution=merge-duplicates` | needs the insert, update and select policies |
| Load history | `GET /rest/v1/sessions?user_id=eq.<uid>&order=ts.desc&limit=1000` | the website loads 200 |
| Save a training | `POST /rest/v1/sessions` | queued on the phone first |

### Saving and retrying (`Store.kt`)

1. A finished training is saved on the phone and queued, then sent.
2. Network error, 401, 429 or 5xx: it stays queued and is tried again later.
3. **409**: the database already has the row (an earlier try got through). It leaves the queue.
4. **400, 403, 422**: the database permanently refused it (a rule above). It leaves the queue, because retrying cannot work.
5. The profile is read first, merged with the phone's copy (higher counts, later day wins, lists joined), then saved.
   This stops two phones on one account erasing each other's counts. The settings blob (`defaults`) is still saved as one piece.

## Changing the database later

Add a new numbered file in `migrations/` (for example `003_...sql`), keep it safe to run twice, run it in the SQL Editor,
then run `verify.sql`. If you add a column the app writes, also add it to `002` so a fresh project matches.
