# Verification record and release gate

## Current status

- Source and configuration inspection only.
- JSON/XML parsing, whitespace checks, and Git tracking checked with PowerShell/Git.
- Dependencies have not been installed. TypeScript/Kotlin have not been compiled.
- Tests have been written but not run. No emulator/phone/app was launched.
- Firebase has not been provisioned or deployed; real sign-in/AI/push unverified.
- The 100-case evaluation suite awaits bilingual human approval and execution.

Do not interpret source completion as a verified APK or a release approval.

## Commands for the user after authorizing execution

From the repository root:

```powershell
pnpm install --frozen-lockfile
pnpm test
pnpm run build:functions
```

For Android (copy the example config only for build-only checks):

```powershell
Copy-Item android/app/google-services.example.json android/app/google-services.json
Set-Location android
./gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
```

Use the real Firebase config for actual phone sign-in. Run Compose instrumentation
tests on an authorized emulator/phone. Check in the generated Room schema JSON.

## Automated source coverage

- Shared tests: confidence queue spacing, accent preservation, unscored uncertain
  answers, token cost arithmetic, Chicago month resets, spring gaps/fall overlaps.
- Backend transaction tests: rejected and duplicate approvals, stale-target atomic
  rejection, metadata/content version separation, offline progress conflicts,
  concurrent budget reservations, idempotent settlement and uncertain requests.
- Android unit tests: directional practice, reveal requirement, one-card queues,
  multiple-choice fallback, CSV quoting/newlines, metadata confidence, DST parity.
- Compose test: accessible visible-side label and flip action.

The transaction mock tests supplement real Firestore verification; they do not
replace emulator/device checks of rules, indexes, API schemas or runtime behavior.

## Phone acceptance checklist

Record phone model, Android version, app commit/build, timezone and result.

1. Sign in as owner, deny a different Google account, verify bootstrap and App
   Check failures cannot read or mutate another UID. Use a second authenticated
   account in Firebase/emulator to verify Firestore rules reject cross-user reads
   and all direct client writes.
2. Create/edit/star/reorder/duplicate a deck and cards. Confirm star/order changes
   retain confidence, meaning changes invalidate it, archive hides normal study,
   trash restores within 30 days, purge is irreversible after cleanup.
3. Import quoted multiline CSV, TSV, accented Spanish and duplicate/invalid rows.
   Export CSV/TSV and reimport into a new deck. Round-trip complete JSON history.
   Interrupt a multi-page restore and retry the same file: no overwrites or copies
   of previously restored pages. Restored reminders must start disabled.
4. Download a deck, enable airplane mode, restart app/phone and complete a practice
   session. Check Low after two other cards, Medium after five, High completes one
   direction, tiny queues shorten spacing, undo restores the prior queue/rating.
5. Reconnect and verify queued events upload once. On a second device, change
   confidence/content and confirm stale offline events remain in history without
   overwriting current cloud confidence. Cloud session conflicts retain local work.
6. Typed practice accepts saved alternatives locally. Non-exact answers use Luna.
   Check Spanish accents/ñ, negation, false friends and valid regional synonyms.
   Disconnect during grading: answer remains pending. Manual grade correction
   survives later grading. Test feedback appears only after submission.
7. Ask Sol to explain, add, edit and delete cards. Inspect every before/after in
   a multi-card proposal. Reject: no content changes. Approve: exact changes once.
   Edit a target after preview: approval fails without partial edits. Interrupt
   provider response: no content changes and no silent retry charge.
8. In a test environment, lower the cap to trigger $8 warning/$10 stop behavior.
   Competing requests cannot bypass reservations. Check a Chicago month boundary.
   Pending reservations stay visible. Non-AI study remains available.
9. Create once/daily/weekday alerts, grant/deny notification permission. Test app
   foreground, background, closed, offline, reboot and timezone changes. Confirm
   FCM/local deduplication, one alert per occurrence, Study now filters, 15-minute
   snooze, no follow-up on dismissal, no alerts after delete/sign-out.
10. Check light/dark, 200% fonts, TalkBack, reduced animation scale, narrow/landscape
    windows and wide-screen rail. Confirm card/buttons/composer avoid system bars
    and keyboard. Check tap/drag conflicts and smooth flip/swipe on the target phone.
11. Approve and run `evals/cases.json`. Require >=95% grading agreement and no
    unresolved critical accent/negation/meaning errors. Manually inspect Sol decks
    for natural language, accuracy, dialect, examples and requested edits.
12. Account deletion blocks cloud access immediately, clears local data and
    notifications, and completes retryable backend cleanup. Logs contain no text
    or credentials. Reinstall/sign-out does not expose the old account cache.

## Required before personal release

Successful build/tests plus recorded owner sign-in, offline resume, an approved
AI batch, real device reminder delivery, and bilingual evaluation results.

## UI implementation verification — November 15, 2026

Implemented the searchable deck library, two-choice creation sheet, standalone AI chat, deck card menus, automatic content preparation, direct study/resume, archived decks and secondary navigation. Removed visible download management, budget, import/export and backend controls. Added card-trash recovery, optional card fields, readable proposal fields and full-height editors.

Repaired stranded chat requests with existing turns, proposal outcome reconciliation, successful AI-deletion navigation, stable mutation IDs, new-card draft identity, network-held rating locks, ambiguous undo compensation, latest-local-session conflict handling, clean remote-session refresh, partial deck-refresh failures, pending-grade worker retries and account-scoped offline data retention at sign-out. Added deck scope and bounded session selection with matching source regression cases.

Validation performed
- Kotlin compilation succeeded after correcting initial syntax errors.
- Two integrated assembleDebug runs succeeded. Final integration took 11 seconds with incremental tasks.
- Added source regression cases for session subsets/deck scope and archived-deck exclusion. Tests were not executed.
- No app launch, emulator run, screenshot rendering, live AI request or deployment occurred.
- Existing deprecation and unchecked-cast compiler warnings remain.
- Final Kotlin compilation passed after adding dirty-form dismissal protection and field messages. The APK predates those final form refinements.

Remaining work from the design/audit
- Device verification of keyboard, TalkBack, large text, dark-system-bar contrast and complete destination previews.
- Dirty-form dismissal protection and text-length field messages are implemented. Full focus restoration and language/list validation messages need device review.
- Per-screen Room subscriptions, durable conflict-resolution UI and a complete lifecycle/concurrency regression suite.
- Oversized session growth after grading, atomic reordering, and full draft naming/history polish.
- Study currently defaults to 20 cards and allows up to 100 per session, including practice and browse. This bounds snapshot size and can be adjusted through Study options.
