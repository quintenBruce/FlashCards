# Design contract

## Visual system

Use Reminder's Material 3 teal palette: light primary #285E59, background #F6F8F5,
surface white; dark primary #A8D9CE, background #111C1A, surface #192522.
Radii 12/18/26 dp, spacing 8/12/16/20/24 dp, semibold headings, system type.
Minimum 48 dp controls. Confidence uses words/icons plus color; never color alone.

## Screens

Decks (home), Review, Reminders, Settings form the primary navigation. Use a rail
on wide windows. Deck detail has Cards, Study, Chat tabs. Navigation 3 keeps the
back stack saveable. Practice/test occupies the screen without primary navigation.

- Decks: title, search, sort; create/manual, AI, import; resume sessions; deck tiles
  with card count and offline/download status. Empty: create your first deck.
- Cards: metadata, direction-specific confidence, search/star filtering, card
  actions, duplicate/reorder/archive/trash and CSV/TSV export.
- Study: all/starred/struggling/known/unrated; term/definition/both; shuffle;
  browse/practice/typed/multiple choice/test, question count (default 20).
- Chat: persisted turns, composer, exact proposal summary, expandable before/after
  details, Approve request and Reject. No automatic changes.
- Review: select decks, confidence pool, saved session history and resume actions.
- Reminders: next occurrence, scope/filter, one-time/daily/weekdays, timezone,
  local time, edit/delete. Study now and 15 minute Snooze notification actions.
- Settings: account, system/light/dark theme, TTS rate/voice, downloads, usage,
  backup/import, trash, conversation deletion, account deletion and sign-out.

## State and motion

Loading uses progress indicators; empty states explain the next action. Errors
remain actionable with Retry. Offline banner states that downloads and progress
are available while editing/chat wait for a connection. AI budget exhaustion is
a separate state, never a generic error or a lost grade.

Cards flip over 220 ms. Practice left swipe = Low, right = High, visible Medium
button. Rating requires answer reveal. Browse swipes navigate. Provide undo and
visible button alternatives. Reduced motion replaces rotation with immediate
state change. Large text scrolls inside cards; playback never reveals a hidden
answer. TalkBack labels announce the visible side and confidence actions.

Keep draft inputs, selections, queue, answers and progress through recreation.
Persist study changes in Room before advancing. No orientation lock or
configChanges workaround. Main content respects system bars and IME insets.

## Design review gate

Review the implemented card, test, and AI proposal screens on the target phone
before release. Runtime screenshots and performance are unverified until the
user runs the app. Figma/Stitch are optional; no additional skills installed.
