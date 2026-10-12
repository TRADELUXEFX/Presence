# Zip-and-drop deploy (Presence PWA)

1. Make a zip whose folders match the repo root (`www/...`, `supabase/...`). A wrapper folder is removed automatically.
2. On github.com open `incoming/` -> Add file -> Upload files -> upload the zip (any name) -> Commit to `main`.
3. Actions tab -> "Deploy Presence (PWA)" unpacks it, checks the JavaScript, commits, deletes the zip and publishes `www/`. Green = live.

- To delete files add `DELETE_FILES.txt` at the zip root (one path per line, `#` comments ok).
- If the JavaScript has a syntax error the run turns red and NOTHING goes live (the old version stays online).
- Files under `.github/workflows/` inside a zip are ignored by GitHub. Edit those on github.com directly.
- Never delete `incoming/` or `.github/workflows/deploy.yml`.
- New SQL goes in `supabase/migrations/` for record keeping, but it is NOT run automatically: run it in Supabase > SQL Editor.
