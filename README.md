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

Compared with `feature/pachattar-design-foundation-phase-1` at commit `759eed423e7046d7fe127226945557a8fe4fa918`, the source diff reports 1,860 additions and 3 deletions in `firestore.rules`. The actual live Firebase Rules/indexes must still be independently exported and compared.

## Current verified security properties

- Self-service Market profile preference changes require verified email.
- Task acceptance must atomically assign the task, accept the chosen proposal, decrement the poster counter and create a new deterministic Market conversation.
- Market config edits require a matching new immutable audit log in the same atomic write.
- Task/offer pin and moderation updates require matching new immutable admin logs and owner notices in the same write.
- Report-status changes require a new matching audit log and a reporter notice linked to the report in the same atomic write.
- Non-appeal support admin replies/resolutions require a new matching audit log and owner notice in the same atomic write.
- Bulk suspension/ban links listings hidden by the operation to the aggregate user-level audit log and removal notice.
- Existing U2U chat and Nearby-transfer rules remain separate from Market chat.

## Latest CI evidence

[Rules run #32](https://github.com/Webster-02/Superior-Student/actions/runs/39865131476), tested commit `d0428fb7b6d6e2414a2d199d954564527193df78`:

- Static validation: **43 passed, 0 failed**.
- Firestore Rules Emulator: **68 passed, 0 failed, 0 skipped**.
- Expression-ceiling diagnostic count: **0**; workflow guard passed.
- Artifact ID: `12462414890`; current rules blob: `4dd84e3cfcd9d2e91d1327b0ffd24e0ad2efbeb1`.

[Android run #103](https://github.com/Webster-02/Superior-Student/actions/runs/39864585562) passed route checks, unit tests, debug APK build and APK verification for app commit `d1d95c4d3c4e9ff3fc5d8596cb0e74eb87d0392c` (artifact ID `12464190239`).

## Still blocking production

Appeal-ticket decision linkage and direct account warning/lift-suspension/profile-status actions still require Rules-enforced log/notice linkage. Lifecycle-counter semantics require clarification. Live Rules/index exports, Auth/profile prerequisites, historical proposal `proposerId` migration, separate staging tests and explicit product-owner approval remain required.

No Firebase Rules or indexes have been deployed and no merge to `main` has occurred.
 
Step 13 status: appeal-decision Rules linkage passed Rules CI (44 static checks, 69 Emulator tests, zero expression-ceiling diagnostics). The matching Android transaction pointer write passed unit tests and debug APK build/verification in run #104 (https://github.com/Webster-02/Superior-Student/actions/runs/39868829292); APK artifact `superior-student-debug-apk` ID `12464259740`.

 
## Phase 2, Step 14 — Latest candidate verification

- Rules run #36: https://github.com/Webster-02/Superior-Student/actions/runs/39880626698 — **45 static checks passed, 71 Emulator tests passed, zero expression-ceiling diagnostics**.
- Android run #105: https://github.com/Webster-02/Superior-Student/actions/runs/39880533430 — unit tests, debug APK build, APK verification and artifact upload passed. Artifact ID: `12471070183`.
- Account warning, suspension/ban, and lift-suspension updates now require linked new audit logs and notices in the same atomic write.
- The implementation stays on `review/market-rules` and `feature/market-ui-reference`. No merge to `main`, Firebase deployment, or live data change occurred.

Before testing against a Firebase project, complete the lifecycle-counter review, independently export/compare live Rules/indexes, confirm Auth/profile prerequisites, and validate historical proposal `proposerId` records. Use a separate staging Firebase project for deployment and smoke tests; production deployment still requires explicit approval.
