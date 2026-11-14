# Firebase connection

## Target and local source

- Existing project: `flashcards-6b1ac` (provided by user).
- Android package: `com.qcbru.flashcards`.
- Default CLI project: committed `.firebaserc`.
- SDKs and Google services Gradle plugin are already declared. Android uses
  Firebase Authentication, Firestore, Functions, FCM and App Check.
- Google sign-in is implemented through Credential Manager and Firebase Auth.
- Real Android `google-services.json` is still pending retrieval from Firebase.
  The copy currently found in Downloads belongs to Reminder and was not copied.

## Official skills

Installed from https://github.com/firebase/agent-skills using Codex's bundled
skill installer because `npx` is not on this shell's PATH. The repository source
was inspected before installation. Skills become discoverable on the next turn.

Installed: firebase-basics, firebase-auth-basics, firebase-firestore,
firestore-rules-creation, firebase-security-rules-auditor, firebase-ai-logic-basics,
firebase-app-hosting-basics, firebase-crashlytics, firebase-data-connect-basics,
firebase-hosting-basics, firebase-remote-config-basics,
extension-to-functions-codebase, xcode-project-setup.

## Access and remaining remote work

The Firebase plugin is installed, but its Firebase MCP tools have not yet become
available in this running chat. No Firebase project was read or changed during
this setup turn. Cloud registration/provider/database status is unverified.

Once the connector is available:

1. Read the Firebase environment and confirm `flashcards-6b1ac`.
2. List apps in that project. Reuse the Android app if the package matches;
   otherwise register `com.qcbru.flashcards` with nickname FlashCards Android.
3. Inspect existing Firestore edition/location. Use the existing default instance
   when compatible; do not replace databases or change location without a concrete
   migration decision.
4. Enable/verify Google sign-in and add the signing fingerprints for the actual
   Android keystore. Determine the owner/support email from the authorized account.
5. Download the Android app config with the official tools into
   `android/app/google-services.json` and verify its project and package IDs.
6. Deploy the repository's private rules/indexes and configure owner-only backend
   access. Check App Check registration and prerequisites before sign-in testing.

## Project-only permissions

The Firebase MCP server uses the credentials available to the Firebase CLI/ADC.
Those credentials can access projects allowed by their IAM roles. The selected
project is not an access-control boundary. To enforce project-only access, use a
dedicated identity granted access only to `flashcards-6b1ac`.

References:
- https://firebase.google.com/docs/projects/iam/overview
- https://firebase.google.com/docs/ai-assistance/mcp-server

No Android app, Gradle task, test suite, or deployment was run in this turn.
