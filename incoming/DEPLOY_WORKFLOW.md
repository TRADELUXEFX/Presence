# Zip-and-drop deploy (Presence native app)

1. Get a zip whose folders match the repo root (`app/...`, `www/...`). A wrapper folder is removed automatically.
2. On github.com open `incoming/` -> Add file -> Upload files -> upload the zip (any name) -> Commit to `main`.
3. Actions tab -> "Build Presence APK (Native)" unpacks it, checks the JavaScript, commits, deletes the zip and builds. Green = done.
   Download the APK from the repo's Releases page (or the run's Artifacts).

- To delete files add `DELETE_FILES.txt` at the zip root (one path per line, `#` comments ok).
- A JavaScript syntax error in `www/` turns the run red and no APK is built.
- `www/` is the Presence web app. It is copied inside the APK, and native PauzFirst-style screens will replace it step by step.
- Files under `.github/workflows/` inside a zip are ignored by GitHub. Edit those on github.com directly.
- Never delete `incoming/` or `.github/workflows/deploy.yml`.
- Android only installs over the old app if the same signing key is used (secrets: ANDROID_KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_PASSWORD).
- New SQL goes in `supabase/migrations/` for record keeping and is NOT run automatically: run it in Supabase > SQL Editor.
