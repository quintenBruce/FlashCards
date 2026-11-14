# FlashCards

Personal Android English–Spanish study app: Compose UI, offline Room sessions,
Google sign-in, Firebase persistence, Sol deck conversations with explicit batch
approval, Luna answer grading, manual reminders, and a $10 monthly AI budget.

## Source

- `android/`: Android app and Java Room storage module.
- `functions/`: authenticated Firebase callable functions and scheduled jobs.
- `shared/`: backend domain rules and regression tests.
- `docs/DESIGN.md`: screen and interaction specifications.
- `docs/SETUP.md`: private Firebase and Android configuration.
- `docs/VERIFICATION.md`: commands and device acceptance checklist.
- `evals/`: bilingual evaluation cases and scoring procedure.

No FlashCards application, package installation, build, or test runner has been
executed by the implementation agent, per workspace instructions. Source and
tests require the documented verification before installation or release.

Firebase resources and secrets must be configured separately; the repository
does not reuse Reminder's Firebase project or credentials. All online mutations
require your allowlisted, verified Google account and Firebase App Check.
