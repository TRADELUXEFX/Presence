# Deploy the Presence app

1. Make a zip whose folders match the repo root (`app/...`, `README.md`, and so on). A single wrapper folder is removed automatically.
2. On github.com open `incoming/` > Add file > Upload files > choose the zip > Commit to `main`.
3. The Actions run "Build Presence APK (Native)" unpacks the zip, builds, and publishes the APK to Releases. Green means done.

Notes
- To delete files, put `DELETE_FILES.txt` at the root of the zip. One repo-relative path per line. `#` starts a comment.
- The workflow refuses to delete some files (`www/index.html`, `www/sw.js`, `www/manifest.json`, the Gradle files, `incoming/`, and `.github/`). Remove those by hand on github.com.
- A new upload cancels a build that is still running.
- Files under `.github/workflows/` inside a zip are ignored. Edit those on github.com.
- New SQL goes in `supabase/migrations/` and is NOT run automatically. Run it in Supabase > SQL Editor.

GitHub Pages
- The old web app in `www/` can be published by GitHub Pages. Set Settings > Pages > Source to "GitHub Actions", which stops the branch publish.
