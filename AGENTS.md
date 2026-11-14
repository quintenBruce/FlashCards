# Workspace instructions

- Do not launch the FlashCards app, Android build tools, emulators, or test runners. The user's separate Reminder App build exception does not apply here. The user explicitly authorized Firebase skill installation and Firebase setup tools for the project below.
- Source inspection, file editing, and Git operations are authorized. Keep all implementation changes committed in Git.
- Never commit API keys, signing keys, service account credentials, or App Check debug tokens.
- Keep responses concise. Record unexecuted verification honestly.

## Firebase

- Always discover and read the appropriate official Firebase agent skills before Firebase work. Source: https://github.com/firebase/agent-skills. Use firebase-basics for environment/app registration/config retrieval, firebase-auth-basics for Authentication, firebase-firestore for database setup, and firestore-rules-creation for rules when its dedicated author agent is unavailable.
- Use the existing Firebase project `flashcards-6b1ac` exclusively. Android package: `com.qcbru.flashcards`. Do not create a replacement project or touch Reminder's `reminder-app-a2150`.
- Prefer connected Firebase MCP tools; start with firebase_get_environment when available. Pass the explicit project ID to project-scoped CLI operations.
- A selected project is a working scope, not an IAM security boundary. Project-only credentials must be enforced through Google Cloud IAM.
- Never reuse Reminder's google-services.json. Retrieve the registered FlashCards Android app's configuration after Google Sign-in/OAuth setup.
- Keep Android app/build/test execution disabled unless the user separately authorizes it. Firebase setup authorization does not authorize an APK build or launch.
