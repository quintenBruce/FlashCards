# Implementation decisions

Package `com.qcbru.flashcards`; minSdk 26, compile/target 37. Kotlin/Compose,
Navigation 3, Room, WorkManager, Firebase Auth/Firestore/Functions/FCM/App Check.
Separate Firebase project, functions and Firestore co-located in us-south1.

Cloud reads are private and client writes denied. Manual content writes require
connectivity and expected versions. Server operations use caller-owned request
IDs for idempotency. AI proposals contain at most 100 immutable operations and
are applied in one version-checked transaction. Proposal edits require a new
preview. Account deletion immediately revokes cloud access, then paginates data.

Room stores downloaded decks, snapshots, pending review events and grades by UID.
Session save and review outbox insertion are atomic. Cloud confidence conflicts
preserve event history and retain server state. Changed card versions do not
receive old session confidence. Grade scores do not change confidence.

Low requeues after 2 other items, Medium after 5; High completes one directional
item. Tiny queues shorten spacing. Both directions are separate items. Quiz
snapshots survive content edits. Exact matching preserves accents/ñ. Non-exact
answers use Luna; network/budget errors stay pending. Needs-review is unscored.

AI monthly ledger uses America/Chicago months, a $10 cap and $8 warning. Reserve
the maximum input/output cost before provider calls, settle returned usage, keep
uncertain reservations. Unknown models are rejected until rates are configured.
Only model costs are capped, not Firebase billing.

Source is delivered without executing applications or build/test tools. Refer to
VERIFICATION.md and SETUP.md for required user-operated validation/provisioning.
