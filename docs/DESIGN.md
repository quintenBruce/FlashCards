# FlashCards Android design contract

This is the target design for the next implementation. Use the workflow handoff for persistence, recovery and commit order. The examples below use sample deck content. They describe proposed layouts, not screenshots of the running app.

## What the source review changes

| Current design decision | Effect on the learner | Target |
|---|---|---|
| FlashApp combines a branded app bar, root navigation, page heading and deck tab row. | After choosing AI, the screen still asks the user to choose Cards, Study or Chat. | Give each task one destination title. Creation opens the conversation directly. |
| DecksScreen puts two creation buttons, chips, resume controls and actions inside every deck tile. | Finding a familiar deck requires scanning unrelated controls. | Search followed by a list of tappable titles. One persistent Add deck action. |
| ChatScreen exposes numbered conversations and Delete before the first message. Proposal details render serialized objects. | The person has to interpret storage concepts before deciding what to learn or save. | Start with a composer. Preview the actual deck name, fronts, backs and changes in reading order. |
| StudySetup asks about five choices before practice. The prompt face, Listen action, direction hints and rating controls compete for space. | Starting practice requires decisions before the first card appears. | One tap on Study starts a sensible default. Reveal the answer before presenting ratings. |
| UiPreviews exercises the card face in isolation. | It gives no evidence about the composer, keyboard, sheet, session controls or full-screen large-text layout. | Review complete destination states with representative text and real surrounding chrome. |

## Layouts to implement

The sketches show visual order. Brackets denote controls. Sample content and count values are illustrative.

~~~
LIBRARY                          CREATION SHEET
Your decks                 [⋮]    ─────
[ Search decks             × ]    Add a deck
                                 [ Create manually             › ]
Travel Spanish                   [ Create with AI              › ]
42 cards
─────────────────────────────
Words from class
18 cards
                  [+ Add deck]
~~~

**Library.** Use one app-bar title, a search field and a flat LazyColumn. The entire row opens a deck. Give the title visual priority and show a reliable card count as secondary text when available. Allow a two-line title. Keep the selected search query on return. Anchor a labeled extended Add deck button at the lower trailing edge of the content column and reserve space below the last row so the button cannot cover it. The app-bar overflow contains Study history, Reminders, Archived decks and Settings. Keep the overflow in the same place when the list is empty. Show “No decks yet” for a genuinely empty library. For a search miss, show “No decks match ‘travel’” and Clear search. Reuse the existing Add button in both states.

**Creation sheet.** Use ModalBottomSheet with a drag handle, title and two full-width rows. The whole row is a single target. Opening the sheet from search dismisses the search keyboard while preserving the query. Either selection closes the sheet and opens one destination. Back or an outside tap restores focus to Add deck. At large font sizes both row labels wrap. No selection is preselected or submitted on dismissal.

~~~
CREATE WITH AI                   CREATED DECK
[‹] Create with AI               [‹] Travel Spanish           [⋮]
                                 [ Study ]           [ Add card ]
       Make a deck for a trip.
                                 ¿Cuánto cuesta?
I have 12 travel phrases.         How much does it cost?
                                 ────────────────────────────
Travel Spanish                   ¿Dónde está la estación?
12 cards                         Where is the station?
¿Cuánto cuesta?
How much does it cost?
[Review all 12 cards]
[Create deck]   [Reject]

[ Describe your deck…     ][↑]
~~~

**Creation chat.** The empty conversation offers one brief prompt, such as “What would you like to learn?” The input accepts a topic or pasted notes. Keep Send labeled for accessibility. After sending, show the outgoing message once, a local progress indicator and the reply. Keep the composer anchored when a proposal expands. A proposal previews the deck title, card count and first two front/back pairs. “Review all 12 cards” expands a list in the conversation or opens a dedicated preview returning to the same scroll position. Use “Create deck” for the confirmation that creates a deck. For existing decks use a concrete edit action, such as “Add 3 cards.” For deletion show the exact target and a destructive action label. Reject leaves a compact outcome on that proposal. Preserve expansion separately for each proposal.

**Existing-deck chat.** Open from the deck menu item “Ask AI about this deck.” Use the deck title as context and “Ask about this deck…” for the composer. Explanations render as ordinary replies. Show “Previous conversations” in the chat menu only when relevant. Name entries from their first message or topic, with a date when needed. Inside AI creation, offer Resume draft when a recoverable draft exists. During streaming or delayed receipt updates, scroll to a new reply only if the reader was near the end or just sent a message. If they are reviewing an earlier proposal, preserve position and offer “New reply” to jump.

**Manual creation and card editing.** Create manually opens New deck with the name field first, optional description and collapsed language options displaying their defaults. Save opens the new deck. Its empty state says “Add your first card” and uses a single Add card button. The card editor starts with Front and Back, with the corresponding languages as field hints. Put example, explanation and alternative answers under More options. A validation error stays beside the field, focus moves to the first invalid field, and the typed values survive retry. On a compact screen use a full-height scrolling form. Back with a dirty draft preserves it or asks whether to discard it. Back from an untouched form closes it immediately.

**Deck detail.** On a populated deck give Study primary emphasis and Add card secondary emphasis. When a matching paused session exists the primary action reads “Resume study”; “Start new session” is available from Study options. Use one row per card with term above translation. The card menu exposes Edit, Star/Unstar and other supported management actions. A bounded find-in-deck action can reveal a search field when needed. Keep deck editing, study options, archive and trash in the deck menu. Keep dictionary-like scrolling simple. On a confirmed deck deletion, return to the library. Show trash recovery in Settings and archive recovery in Archived decks.

~~~
PRACTICE · BEFORE REVEAL         PRACTICE · AFTER REVEAL
[‹] Travel Spanish               [‹] Travel Spanish
3 of 12 completed                3 of 12 completed
─────────────────────────────    ─────────────────────────────
Spanish                          English
¿Cuánto cuesta?                   How much does it cost?
[Listen]                         [Listen]
                                 Example sentence, if available
[Show answer]                    How well did you know it?
                                 [Low]  [Medium]  [High]
~~~

**Practice.** Start directly on the prompt. The card and Show answer activate the same reveal action. Keep Listen separate so pronunciation never reveals or rates a card. After reveal, emphasize the translation. Place the optional example below it and keep rating buttons in a stable action area. Low and Medium return the card later, High completes it in this session. Expose Undo after a rating. A single Back affordance exits to the caller after locally committed progress. Keep swipe as a shortcut and make every gesture available through a labeled control. Support an immediate face change when motion is reduced.

**Other study modes.** In Browse show position (“Card 4 of 12”), Previous/Next, and Finish on the last card. For typed answers, keep the prompt and current input reachable above the keyboard. Save answer precedes feedback and Next. In Test, show “Answer saved” and hold the answer key until submission. Use “Awaiting grade” for pending results and keep manual correction under the individual result’s menu. The results screen shows completed cards for practice/browse, and grades for graded modes. Resume from History restores the saved mode, card and state.

**Secondary destinations.** In Study history put unfinished sessions first, with the deck title, mode, last activity and Resume. Completed sessions open their result. Put multi-deck practice behind “Study across decks.” Reminders lists the next alert and deck scope, with one Add reminder action. An editor shows repeat, local time and scope first, revealing date or weekdays according to repeat. Settings groups Appearance, Pronunciation and Account, with Trash reachable for recovery. Separate destructive account actions from routine preferences. Sign-in needs one short explanation and one Continue with Google action.

## Visual system and adaptive behavior

- Keep FlashTheme’s existing light and dark teal schemes, system typography, semibold titles and 12/18/26 dp shape scale. Use primary for the main action, surface for lists and onSurfaceVariant for supporting text. Reserve a tinted container for a focused study face or a message. Repeating large filled cards around every list row obscures grouping. Use spacing first and dividers where adjacent rows need separation.
- Start with 16 dp horizontal margins on narrow windows, 24 dp on roomy windows, 8 dp between related text/control elements and 16–24 dp between sections. Use Material titleLarge for a destination, titleMedium for deck names, bodyLarge for conversation and field content, and bodyMedium for secondary information. Let rows grow vertically. Give interactive elements at least a 48 dp target.
- Keep one navigation path across sizes. On a tablet or unfolded screen, center the library and deck list within roughly 760 dp and conversational or form content within roughly 640 dp. These are starting design values for review. Align the Add button with the content edge. Keep a standalone, focused chat and study destination through resize. At compact height, let the session body and controls scroll together if a pinned footer would squeeze the card or answer field out of view. Wrap or stack actions at large font sizes.
- MainActivity already calls enableEdgeToEdge and the manifest specifies adjustResize. Let one Scaffold own chrome insets. Pass remaining list padding to scrollable content, consume it once, and let the composer/form handle remaining IME space. Keep the last deck, last proposal action and focused form field reachable in gesture and three-button navigation. Confirm system icons remain legible when the app theme differs from the device theme.
- Preserve route ID, query, list position, conversation, draft and pending proposal across rotation and resize. Keep domain objects and long transcripts in the existing persistent layer. A saved route should identify its content by ID. Opening a sheet, expanding a preview and navigating to another screen are distinct interactions with predictable Back behavior.
- Use visible labels and state as well as color. A deck row should announce its title, known count and open action once. Announce proposal outcomes and errors once without moving focus away from text being edited. Return focus to the invoking control after a sheet/menu closes and to the next relevant heading after a route change. Verify both text contrast and focus visibility in light/dark themes, including disabled, selected and error states. Preserve accents, mixed-language text and long translated labels.

## State and copy decisions

| Situation | Visible response | Available action |
|---|---|---|
| Cached decks exist, background refresh in progress | Show the list immediately. | Open a deck or search normally. |
| First load, no reliable local list | Brief progress in the list region. | Present an inline retry if loading fails. |
| Selected deck has no cached content and device is offline | “Connect to open this deck.” | Retry in place after reconnecting. |
| A deck was saved but loading its cards failed | “Deck saved. Its cards couldn’t load.” | Load cards again for that exact deck. |
| AI status is uncertain | “Your message may have been received.” | Check status on that message. |
| AI edit preview became stale | “This deck changed after the preview.” | Request a fresh preview while preserving the conversation. |
| No cards match a study filter | Name the active filter. | Change filter. For a genuinely empty deck offer Add card. |
| Learner exits practice after one rating | Resume is available on the originating deck and in History. | Resume at the locally saved card. |

## Design verification for the implementation pass

Use state-driven Compose previews with sample data and event callbacks. Keep previews independent of network and account state. The existing previews cover the face of a card. Add representative full-screen previews where the redesign first introduces each layout. Execution and rendering are deferred until authorized.

| Review | Fixture | Pass condition |
|---|---|---|
| First-use comprehension | Empty library → Add deck → AI | One obvious Add action, two sheet choices, then one chat composer. A learner can type a topic without navigating further. |
| Everyday use | 30 decks, a two-line title, a search miss, and a paused session | The last row is fully reachable, Clear search restores the list, and reopening the deck exposes Resume. |
| AI decision | Long reply followed by two proposals; one expanded | The reader can identify the affected cards, approve only the selected proposal and read earlier messages without being pulled to the bottom. |
| Study at 200% font | Long bilingual prompt, example and three rating labels on a 360 dp phone | The prompt, answer and all rating actions are readable and reachable. Single-word truncation never hides a rating’s meaning. |
| Short window and keyboard | 740 × 360 dp landscape; compose a multiline message and edit a long back field | Send, validation and Save stay reachable. Keyboard dismissal preserves draft and focus context. |
| Large window and resize | Resize a tablet-sized window with an open draft or partially revealed session | Same destination, scroll, draft and card survive. Reading width stays bounded. |
| Input and accessibility | TalkBack, external keyboard, reduced motion; both themes and system navigation modes | Predictable focus order, visible focus, one activation per control, accessible gesture alternatives, legible bars and no hidden action. |

Report observed results separately from proposed designs. Do not present a source review or an unrendered preview as a phone screenshot.

## AI creation interaction rules — November 15, 2026

- Add deck opens a sheet with two full-width buttons at least 64 dp high. The sheet has no title.
- Create with AI opens one creation draft for the signed-in user. If one exists, show Resume draft and Start fresh. Dismissal preserves the draft.
- Resume preserves the conversation, sent messages, proposal and recoverable request ID. Start fresh cancels local sends, tombstones old conversations, removes their cached messages and proposals, and assigns a new draft ID. An already-saved deck is never discarded.
- Legacy multiple drafts resolve to the canonical stored draft, otherwise the newest unfinished conversation/request. Starting fresh retires the old unfinished conversations.
- Chat offers no numbered conversation selectors or New conversation action.
- While the response is pending, show an assistant bubble with three dots and an accessible “AI is replying” label. Failures retain their recovery controls.
- The server claims a user-scoped active creation slot transactionally before any provider call. A second creation conversation is rejected until the first is discarded or its deck is saved. Approval checks the conversation tombstone, so discarded proposals cannot mutate cards.
- Android changes require an updated APK. Cross-device enforcement and discarded-proposal checks require deployment of the changed sendDeckMessage and confirmProposal functions to flashcards-6b1ac.
