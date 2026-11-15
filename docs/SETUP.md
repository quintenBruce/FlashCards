# Private setup (user-operated)

No application, build, installer, cloud provisioning, or deployment was executed
by the implementation agent. Perform these steps only when you want to run and
connect the app. Never select the Reminder Firebase project for this app.

## 1. Firebase

1. Use the existing Firebase project `flashcards-6b1ac`. The committed
   `.firebaserc` selects it. The project already has a Standard, Native-mode
   Firestore database with the literal ID `default` in `us-south1`. This is a
   named database, distinct from `(default)`. The Android client, Admin SDK, and
   Firestore deployment config explicitly target `default`; do not create a
   second database to satisfy this setup. Firebase Admin's named-database API
   remains a public preview, so review its support status before release.
   Verify billing before deploying paid Cloud Functions.
2. Enable Authentication → Google. Use your verified Google account.
3. Register Android package `com.qcbru.flashcards`. Add your debug and release
   signing SHA-1/SHA-256 certificates. The user can obtain these with Android
   Studio or `android/gradlew.bat signingReport` when execution is authorized.
4. Download that project's `google-services.json` into `android/app/`. It is
   ignored. The example config supports compilation only and cannot sign in.
5. Register App Check. Debug builds use the debug provider; privately allowlist
   the token shown on the device. Release builds use Play Integrity. For a
   sideloaded release, configure Play Integrity for the actual distribution
   channel/certificate or use an explicitly authorized debug pilot build.
6. Confirm `.firebaserc` selects `flashcards-6b1ac`. Copy
   `functions/.env.example` to `functions/.env` and set `OWNER_EMAIL` to your
   Google account. Empty/unconfigured OWNER_EMAIL denies bootstrap.
7. Put the OpenAI key in Secret Manager using the command below. Keep the
   default `gpt-6.1-sol` / `gpt-6-luna` models and 10,000,000 micro-USD cap.
8. Deploy Firestore rules and indexes and the Functions source after checks pass.
   First owner sign-in creates `access/{uid}` with `enabled: true`. Every data
   endpoint rechecks this server-controlled record. Other accounts are denied.

Commands for the user (not executed here):

```powershell
pnpm install --frozen-lockfile
pnpm run build:functions
npx -y firebase-tools@latest functions:secrets:set OPENAI_API_KEY --project flashcards-6b1ac
npx -y firebase-tools@latest deploy --only "firestore,functions" --project flashcards-6b1ac
```

The deploy tool also uses `functions/package-lock.json` for buildpack installs.
Both locks derive from the existing Reminder dependency resolution; dependencies
are pinned. Re-review dependency updates before changing either lock.

## 2. Budget and operations

- Monthly model budget defaults to $10, America/Chicago calendar months. Warning
  starts at $8 including reservations. Costs are integer micro-USD.
- Before every provider call, reserve the byte-bounded maximum input plus maximum
  output cost. No SDK retries. Unknown model/rate pairs fail closed.
- Provider responses with usage settle the reservation once. An interrupted
  response leaves its reservation charged conservatively. Do not manually refund
  it without checking provider billing. Duplicate request IDs never call AI twice.
- Firebase costs are separate. Configure Cloud Billing alerts appropriate to your
  account. Zero minimum function instances avoid continuously warm instances.
- `dispatchReminders` runs each minute; `cleanDeletedData` runs daily. Deleted
  account cleanup waits at least 10 minutes for in-flight functions to expire.
- Logs contain operation name, duration and error code. They omit learning text,
  student answers, chats, push tokens, credentials and exception messages.
- Revoking `access/{uid}.enabled` immediately blocks cloud reads and calls.
  Offline downloaded material remains on the device until sign-out/local removal.

## 3. Android

Open `android/` in Android Studio when you authorize execution. Use SDK 37 and
the pinned wrapper/AGP/Compose versions. The separate Java-only storage module
uses Room's Java annotation processor; Kotlin app code consumes its DAO.

Grant notification permission in Reminders. Exact-alarm permission is not
requested; background delivery is best effort using FCM and local inexact alarms.
Configure offline English/Spanish TTS voices in Android settings.

Only use `google-services.example.json` copied to `google-services.json` for
build-only checks. Replace it with the real config before phone acceptance.

## 4. Source control and release

All source changes are committed locally. No remote repository was supplied or
created, and no code was pushed. Choose a private Git remote when ready.

Official Firebase agent skills are installed in the user's Codex skills directory.
See `FIREBASE_CONNECTION.md` for the current local connection status. Firebase
administrative credentials are governed by Google Cloud IAM; choosing a default
project does not restrict an authenticated account to that project.
CI is manual (`workflow_dispatch`) to respect the no-execution instruction;
it does not deploy. After an authorized first build, review and commit Room's
generated schema JSON before any database migration. Keep signing material and
App Check debug tokens out of version control.
