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

Compared with `feature/pachattar-design-foundation-phase-1` at commit `759eed423e7046d7fe127226945557a8fe4fa918`, the source diff reports 1,826 additions and 3 deletions in `firestore.rules`. The actual live Firebase Rules/indexes must still be independently exported and compared.

## Current verified security properties

- Self-service Market profile preference changes require verified email.
- Task acceptance must atomically assign the task, accept the chosen proposal, decrement the poster counter and create a new deterministic Market conversation.
- Market config edits require a matching new immutable audit log in the same atomic write.
- Task/offer pin and moderation updates require matching new immutable admin logs and owner notices in the same write.
- Report-status changes require a new matching audit log and a reporter notice linked to the report in the same atomic write.
- Bulk suspension/ban links listings hidden by the operation to the aggregate user-level audit log and removal notice.
- Existing U2U chat and Nearby-transfer rules remain separate from Market chat.

## Latest CI evidence

[Rules run #30](https://github.com/Webster-02/Superior-Student/actions/runs/39862784731), tested commit `b19472ad8d9d7ce0811285e66d9dfc0fadbc7fba`:

- Static validation: **42 passed, 0 failed**.
- Firestore Rules Emulator: **67 passed, 0 failed, 0 skipped**.
- Expression-ceiling diagnostic count: **0**; workflow guard passed.
- Artifact ID: `12462911143`; current rules blob: `05f2938af5782682cd81336a4e57b69501acbbd0`.

[Android run #102](https://github.com/Webster-02/Superior-Student/actions/runs/39862887618) passed route checks, unit tests, debug APK build and APK verification for app commit `3c6da1a713ec836d7b5229324ac3bcb65dc415af` (artifact ID `12463315396`).

## Still blocking production

Support reply/resolve and direct account warning/lift-suspension/profile-status actions still require Rules-enforced log/notice linkage. Lifecycle-counter semantics require clarification. Live Rules/index exports, Auth/profile prerequisites, historical proposal `proposerId` migration, separate staging tests and explicit product-owner approval remain required.

No Firebase Rules or indexes have been deployed and no merge to `main` has occurred.
