# U2U Market Firestore rules — review branch

**Review branch:** `review/market-rules`. This is a candidate for review only; it has not been merged to `main` or deployed to Firebase.

## Package contents

- `firestore.rules`, `firestore.indexes.json`: candidate Rules and composite indexes.
- `firestore.base.rules`, `rollback/firestore.rollback.branch-snapshot.rules`: selected source-branch snapshots, not proof that live production matches them.
- `DIFF_AGAINST_BASE.patch`, `BASE_DIFF_VALIDATION.txt`: source diff and baseline hashes.
- `tests/firestore.rules.test.mjs`, `tests/static-validate.mjs`, `package.json`, `package-lock.json`, `firebase.json`: Emulator tests, static checks and pinned dependencies.
- `.github/workflows/firestore-rules-test.yml`: review workflow runs static and Emulator tests, checks expression-ceiling diagnostics and retains logs.
- `MARKET_FINAL_SPEC.md`, `MARKET_SPEC_ADDENDUM_RULES_PHASE.md`, `INDEX_QUERY_MAP.md`, `DEPLOY_CHECKLIST.md`, `PROGRESS.md`: specifications, query/index map, checklist and review findings.
- `TEST_RESULTS.md`: current verified CI results and production gates.

## Selected source baseline

Compared with `feature/pachattar-design-foundation-phase-1` at commit `759eed423e7046d7fe127226945557a8fe4fa918`, the source diff reports 1,799 additions and 3 deletions in `firestore.rules`. The actual live Firebase Rules/indexes must still be independently exported and compared.

## Current verified security properties

- Self-service Market profile preference changes require verified email.
- Task acceptance must atomically assign the task, accept the chosen proposal, decrement the poster counter and create a new deterministic Market conversation.
- Market config edits require a matching new immutable audit log in the same atomic write.
- Task/offer pin and moderation updates require matching new immutable admin logs and owner notices in the same write.
- Bulk suspension/ban links listings hidden by the operation to the aggregate user-level audit log and removal notice.
- Existing U2U chat and Nearby-transfer rules remain separate from Market chat.

## Latest CI evidence

[Rules run #29](https://github.com/Webster-02/Superior-Student/actions/runs/39860003494), tested commit `181e347c14032a8b8ddfd5a0c314afe17111a016`:

- Static validation: **41 passed, 0 failed**.
- Firestore Rules Emulator: **67 passed, 0 failed, 0 skipped**.
- Expression-ceiling diagnostic count: **0**; workflow guard passed.
- Artifact ID: `12461586838`; current rules blob: `3bd08c780ebdb2eef69033260d6a86f4ceee4c2c`.

[Android run #101](https://github.com/Webster-02/Superior-Student/actions/runs/39859898773) passed route checks, unit tests and debug APK build for app commit `a8b8e55d0b3cfec5f19323b31091572282c18d00` (artifact ID `12461825353`).

## Still blocking production

Report-status, support reply/resolve and direct account warning/lift-suspension/profile-status actions still require Rules-enforced log/notice linkage. Lifecycle-counter semantics require clarification. Live Rules/index exports, Auth/profile prerequisites, historical proposal `proposerId` migration, separate staging tests and explicit product-owner approval remain required.

No Firebase Rules or indexes have been deployed and no merge to `main` has occurred.
