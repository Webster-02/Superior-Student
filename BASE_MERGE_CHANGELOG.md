# Base-to-candidate rules diff and merge changelog

Selected baseline: `feature/pachattar-design-foundation-phase-1`.

## Existing U2U chat/Nearby rules: unchanged

The requested admin exclusions were removed from the existing U2U rules. These base sections are byte-identical in `firestore.base.rules` and the merged candidate:

- `activeChatConversationMember(conversationId)`
- The existing `match /chatConversations/{conversationId}` parent block through the start of `readStates/{uid}`

The static preflight extracts and compares both sections exactly. The unified diff in `DIFF_AGAINST_BASE.patch` has one insertion hunk only, containing the additive Market blocks; it does not patch an existing chat/Nearby rule. Regular accepted-friend chat and Nearby-transfer regression tests remain. The admin regression test uses an admin UID that is not a participant, matching the stated operating model (admin account is separate from student accounts); no special admin exclusion has been added to legacy chat rules.

## Additive Market blocks

- Market helper functions, `marketProfiles/{uid}`, listings, counters and cleanup, reports, ratings/statistics, notices, support, admin membership/logging, config and `marketConversations`.
- Task and offer content validators are reused at create and whenever an owner changes a content field. `marketUserCanPost(owner)` is required for content edits; lifecycle-only cancel/pause/expire/remove transitions remain separately available with the linked counter updates.
- Proposals now store immutable `proposerId`, equal to the proposal document ID and authenticated UID at creation. A recursive collection-group `get/list` rule allows only `resource.data.proposerId == request.auth.uid`; the nested task-level rule still lets the task poster list that task's proposals.
- The single composite index added is `proposals` at `COLLECTION_GROUP` scope on `proposerId ASC, updatedAt DESC`.
- Market conversation IDs are deterministic: `kind_listingId_uidLow_uidHigh`, with sorted participant UIDs. Task conversation creation supports both already-assigned tasks and an accept batch by checking `get()` for the prior state and `getAfter()` for the resulting assigned state. `lastMessage` preview and per-participant `lastReadAt` are the only mutable metadata beyond `updatedAt`; the IDs, participants, listing association and creation time are immutable. Admins remain excluded from Market conversations/messages.
- Config `limits` remain rules-enforced constants (`3/10/15/5/3`) and must be shown read-only in the admin panel. Only announcement, blockedWords and rankWeights are editable; every rank weight must be non-negative and their sum must equal 100.

## Provenance and limitations

`firestore.base.rules` and `rollback/firestore.rollback.branch-snapshot.rules` are byte-identical to the selected branch snapshot. Static validation compares the original Git blob SHA. This is not proof that the selected branch matches live Firebase production rules; export the live Console rules/indexes and reconcile them before any deployment.

The static test suite validates the additive-only diff shape and Market invariants but is not the Firebase Rules compiler. The Emulator suite has not run in this environment because `registry.npmjs.org` could not be resolved. See `TEST_RESULTS.md` for real command output. This review follow-up adds a single commit to `review/market-rules` only. It does not merge to `main` or deploy to Firebase.


## Follow-up: first real Emulator run and evaluation-budget fixes

The first committed candidate's real GitHub Actions run was `39531687165` (65 tests: 56 passed, 9 failed; static preflight passed). Its artifacts are linked in `TEST_RESULTS.md`. The rerun candidate:
- fixes test fixtures that were not actually changing owner-protected fields;
- fixes the Nearby transfer public-key fixture to meet the preserved baseline validation;
- dispatches profile-counter validation by mutation kind, keeping the common immutable/allowed-key/timestamp checks while evaluating the matching operation helper;
- caches profile/counter/profile-name reads in task/offer/posting validators;
- makes equality on optional `assignedTo` null-safe for unassigned tasks;
- removes duplicate create-time content checks already covered by the canonical validator; and
- avoids running the full participant-shape validator twice on every Market conversation metadata update while retaining member, verified-email, admin, block, and changed-key checks.

The candidate fixes are intentionally limited to Market rule helpers and tests. Existing U2U chat/Nearby rule blocks remain unchanged.
