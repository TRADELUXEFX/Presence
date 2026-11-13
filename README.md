# Presense Training

Native Android app (Kotlin, namespace `com.presence.app` (applicationId `com.presense.app`)). No WebView.

## Folders
- `app/` the Android app. Screens are built in code with the design kit in `Ui.kt`.
- `supabase/migrations/` SQL history. Run new SQL by hand in Supabase > SQL Editor.
- `incoming/` drop a zip here to deploy. See `incoming/DEPLOY_WORKFLOW.md`.

## Features
- Login, sign up, forgot password (Supabase).
- Home: day streak, full streak, today's progress, the four trainings.
- Trainings: No Blinking, No Fidgeting, Box Breathing, Meditation. Each has a ring timer, voice prompts, screen kept awake, and a foreground service so it keeps running with the screen off. Mirror mode uses the front camera.
- Progress: stats and training history.
- Weekly summary with PDF report.
- Settings: dark mode, daily reminder, saved defaults, sign out.
- Home screen streak widget.

## Build
GitHub Actions builds the APK and AAB (JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.10.1, target SDK 36). The APK is attached to the Releases page.
Release signing uses the secrets ANDROID_KEYSTORE_BASE64, KEYSTORE_PASSWORD and KEY_PASSWORD. Keep the same key so updates install over the old app.
