# Google Play release checklist

Code changes made for Play review are in the repo. The steps below must be done by hand.

## Before the first upload
1. **Supabase:** run `supabase/migrations/20261113_delete_my_account.sql` in Supabase > SQL Editor, then confirm in `supabase/DATABASE.md` that its status is APPLIED. The in-app Delete account button fails until this runs.
2. **GitHub secrets:** `ANDROID_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD` must be set. Without them the build now stops (it no longer signs with the debug key).
3. **Google Sign-In:** in Google Cloud Console, add the SHA-1 of the key Google Play signs with (Play Console > Setup > App integrity > App signing certificate) to the Android OAuth client. Without it, Google sign-in fails for Play installs.
4. **Privacy policy:** host `www/privacy.html` at a public URL and enter that URL in Play Console.

## Play Console forms
- **Data safety:** declare account data, app activity and the optional screen, microphone and camera recordings (kept on device, not uploaded).
- **Permission declarations:** exact alarms (`SCHEDULE_EXACT_ALARM`, used for reminders) and the foreground service types `mediaPlayback`, `mediaProjection` and `microphone`. Each needs a short justification and, for screen recording, a short demo video.
- **Content rating** questionnaire and **target audience** (not directed at children under 13).
- **Store listing:** 512 x 512 icon, 1024 x 500 feature graphic, phone screenshots, short and full descriptions.
- **App access:** give reviewers a working test account, because sign-in is required.
- **Closed testing:** if the developer account was created after November 2023, run a closed test with at least 12 testers for 14 days before production.

## Each release
- Upload the `.aab` from the workflow artifact, not the `.apk`.
- Check that the target SDK is still at or above the Play requirement (currently API 36).
