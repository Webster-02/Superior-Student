# U2U Market Firestore rules — review branch

**Review branch:** `review/market-rules`. This is a candidate for review only; it has not been merged to `main` or deployed to Firebase.

## Package contents

- `firestore.rules`, `firestore.indexes.json`: candidate Rules and composite indexes.
- `firestore.base.rules`, `rollback/firestore.rollback.branch-snapshot.rules`: selected source-branch snapshots, not proof that live production matches them.
- `DIFF_AGAINST_BASE.patch`, `BASE_DIFF_VALIDATION.txt`: source diff and baseline hashes.
- `tests/firestore.rules.test.mjs`, `tests/static-validate.mjs`, `package.json`, `package-lock.json`, `firebase.json`: Emulator tests, static checks and pinned dependencies.
- `.github/workflows/firestore-rules-test.yml`: review workflow runs static and Emulator tests, checks expression-ceiling diagnostics, and retains logs.
- `MARKET_FINAL_SPEC.md`, `MARKET_SPEC_ADDENDUM_RULES_PHASE.md`, `INDEX_QUERY_MAP.md`, `DEPLOY_CHECKLIST.md`, `PROGRESS.md`: specification, query/index map, checklist and review findings.
- `TEST_RESULTS.md`: current verified CI results and production gates.

## Selected source baseline

Compared with `feature/pachattar-design-foundation-phase-1` at commit `759eed423e7046d7fe127226945557a8fe4fa918`, the source diff reports 1,758 additions and 3 deletions in `firestore.rules`. The actual live Firebase Rules/indexes must still be independently exported and compared.

## Current verified security properties

- Self-service Market profile preference updates require verified email.
- Task acceptance must atomically assign the task, accept the chosen proposal, decrement the poster's open-task counter and create a new deterministic Market conversation.
- Market config create/update requires a newly created immutable `adminLogs` entry in the same atomic write; `lastAuditLogId` must match the log's action, target, admin and request timestamp.
- Existing U2U chat and Nearby-transfer rules remain separate from Market chat.

## Latest CI evidence

[Rules run #27](https://github.com/Webster-02/Superior-Student/actions/runs/39858637212), tested Rules commit `1f5536f4387cb7254e4a0ccae04c896b1a68b511`:

- Static validation: **40 passed, 0 failed**.
- Firestore Rules Emulator: **67 passed, 0 failed, 0 skipped**.
- Expression-ceiling diagnostic count: **0**; workflow guard passed.
- Artifact ID: `12459668968`.

[Android run #100](https://github.com/Webster-02/Superior-Student/actions/runs/39858373895) passed unit tests and built a debug APK from app commit `4a74df5e6d7d383f4f38fda716b8eaafed4ebdb0` (artifact ID `12459833836`).

## Still blocking production

Task/offer pin and moderation, report status, support and account actions still need enforced admin audit-log/notice linkage and regression coverage. Lifecycle-counter wording needs clarification. Live Rules/index exports, Auth/profile prerequisites, historic proposal `proposerId` migration, separate staging tests and explicit product-owner approval remain required.

No Firebase Rules or indexes have been deployed and no merge to `main` has occurred.
