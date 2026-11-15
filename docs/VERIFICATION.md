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

## Single AI draft verification — November 15, 2026

- Added five Android unit tests covering canonical draft selection, legacy migration, saved/trash deck exclusion, request-only draft recovery and completed-turn reconciliation.
- Added backend tests for second-conversation rejection, simultaneous claims, discarded-draft replacement, saved-deck completion, user isolation and discarded-proposal approval.
- Existing tests cover directional confidence, reveal requirements, bounded practice, archived decks, answer normalization, content versions, CSV, DST, transactional approvals, review conflicts, budgets and provider receipt recovery.
- 28 shared/backend unit tests passed. Functions TypeScript compilation passed. 14 Android unit tests passed and the debug APK assembled successfully.
- Tests ran under local Node 24; Functions declares Node 22. No live provider calls were made by the mocked unit tests.
- UI uses a three-dot loading bubble and a title-free sheet with 64 dp creation buttons. Existing drafts prompt Resume draft / Start fresh. Multiple-conversation controls were removed.
- Backend changes have not been deployed. The new APK has not been installed or launched in this pass.

## Header overflow placement — November 15, 2026

Deck-wide actions now live in the top app bar beside the deck title: Ask AI, card trash, study options, edit, archive and trash. The library menu already occupies the header. Card-specific actions stay on their individual rows. Menu state resets when the destination changes, and deck deletion retains its confirmation and returns to the library only after success.

Kotlin compilation and git diff --check passed. No additional unit tests were added for this control-placement change. Device rendering remains unverified.

## Study controls and Expressive theme — November 15, 2026

- One unfinished study session occupies each included deck. Completed/submitted sessions remain in history and free their decks. Combined reviews occupy every included deck.
- Starting from the deck, custom study setup or a reminder resumes an overlapping unfinished session. Room resolves concurrent local starts in a transaction. When resuming legacy duplicates, the newest session is retained and older overlapping sessions are preserved as replaced history.
- The saveStudySession callable claims per-deck activeStudies slots transactionally and rejects another unfinished session, including concurrent starts. This backend change needs deployment for cross-device enforcement.
- Study/Resume study, Add card and Ask AI are action chips in a pinned, horizontally scrollable row above the card list. Less-used deck actions stay in the header overflow.
- Removed Show answer, visible Prompt/Answer headings, the confidence/swipe instructions and Tap to flip footer. Tapping the card remains the flip action and accessible flip semantics remain.
- Enabled MaterialExpressiveTheme and MotionScheme.expressive(). Kept the existing teal palette. Material 3 was upgraded from 1.4.0 to 1.5.0-rc01 because 1.4 hides the Expressive APIs.
- API choice verified against https://developer.android.com/jetpack/androidx/releases/compose-material3 and https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary .
- 19 Android unit tests and 32 shared/backend tests passed. Functions compilation and the debug APK build passed. The first Android attempt identified the unavailable Expressive APIs in Material 3 1.4; the dependency update resolved it.
- No phone installation, app launch or Firebase deployment was performed in this pass. Device layout and TalkBack behavior remain unverified.

## Chat bubble presentation — November 15, 2026

Outgoing messages are right-aligned in primary-container bubbles. AI replies, proposal previews and recovery/error messages are left-aligned in secondary-container bubbles. Bubbles wrap short text and cap their width at 88% of the transcript.

The pending reply uses three dots with staggered 6 dp hops over a 1.2-second cycle. System-disabled animation keeps the dots still. The bubble exposes one accessible “AI is replying” label.

Added conversation previews for normal and large text. assembleDebug and diff whitespace checks passed. No device installation, app launch or visual rendering occurred in this pass.

## Conversation scrolling and simplified workflows — November 15, 2026

- Conversations open at the newest message in a bottom-anchored list. Keyboard/composer height changes keep the latest message visible while following the end. Reading older messages preserves scroll position. Sending a message follows the end again.
- The transcript merges pending requests and replies chronologically, deduplicating saved receipts. Added three regression tests for old failed requests, newest outgoing messages and replacement by a saved reply.
- Removed the Reject action. The user approves a proposal or sends a refinement. Only the latest proposal offers approval, with older suggestions marked “Revised below.” Proposal approval is disabled while a reply is in flight.
- Trash is the sole removal workflow. Archive controls/navigation were removed. Legacy archived decks are visible and studyable. Trashed or purge-requested decks remain excluded. Added a regression case for unavailable decks and updated the legacy-archive expectation.
- Removed Undo wording and added Previous for practice, including from completion. It returns to the last rated card with its previous confidence/queue restored, allowing a replacement rating. No undo snackbar was added.
- Android unit tests and assembleDebug passed. Phone keyboard/scroll animation has not been visually exercised by the agent.

## Clear deck chat and offline drafting — November 15, 2026

Ask AI has a header Clear conversation history icon with confirmation. Clearing targets the current deck's conversations, pending requests, messages and proposals. It cancels local in-flight requests, uses the existing server deletion endpoint for locally known and remotely discovered conversations, and switches to a fresh conversation ID. Deck/card content is preserved.

Add card now opens whenever the deck is loaded, including offline. Saving still uses the existing online mutation path. Save failure preserves the editor inputs.

Two new scope tests cover clearing only the selected deck and finding requests before conversation metadata exists. All 25 Android unit tests and assembleDebug passed. Device installation and visual verification were not performed in this pass.

## Navigation and search spacing — November 15, 2026

Added bottom navigation for Decks, Study, Reminders and Settings on main destinations and deck detail. Home now uses the FlashCards app-name resource. Home and deck detail share a rounded search field with clear and keyboard search actions. Increased header spacing and aligned the deck action, search and content gaps. Search remains pinned on deck detail.

All 25 Android unit tests and assembleDebug passed. Existing ListItem and Slider deprecation warnings remain. Device UI interaction and screenshot tests were not run.

## Faster footer navigation and reminder redesign — November 15, 2026

Footer destinations crossfade with a 120 ms entry and 90 ms exit. The reminder page now shows time, repeat and deck scope with pause switches, tap-to-edit and a Delete overflow action. Completed one-time reminders open for rescheduling. The editor uses structured time and date pickers, three repeat choices, deck selection and collapsed optional details. Unsaved reminder drafts survive recreation and confirm dismissal. Notification guidance checks app and channel settings when the page resumes.

Extracted reminder validation and the existing local clock for unit testing. Paused and deleted reminders are excluded from notification delivery and snoozing. Local snoozes record the reminder version and are excluded from rescheduling after an edit.

All 39 Android unit tests passed, including 14 reminder tests for validation, selected days/decks, one-time expiration, time zones, daylight saving, pause state, stale snoozes and next occurrence calculation. assembleDebug and git diff --check passed. Device visual interaction and delivery of a real timed notification were not exercised.
