# U2U Market Firestore rules — review branch

**Review branch:** `review/market-rules`. This is a candidate for review only; it has not been merged to `main` or deployed to Firebase. No Firebase publish/deploy is authorized by this review package.

## Package contents

- `firestore.rules`, `firestore.indexes.json`: candidate Rules and composite indexes.
- `firestore.base.rules`, `rollback/firestore.rollback.branch-snapshot.rules`: selected source-branch snapshot, not proof that live production matches it.
- `DIFF_AGAINST_BASE.patch`, `BASE_DIFF_VALIDATION.txt`: source diff and baseline hashes.
- `tests/firestore.rules.test.mjs`, `tests/static-validate.mjs`, `package.json`, `package-lock.json`, `firebase.json`: Emulator tests, static checks and pinned dependencies.
- `.github/workflows/firestore-rules-test.yml`: review workflow runs static and Emulator tests, checks expression-ceiling diagnostics, and retains logs.
- `MARKET_FINAL_SPEC.md`, `MARKET_SPEC_ADDENDUM_RULES_PHASE.md`, `INDEX_QUERY_MAP.md`, `DEPLOY_CHECKLIST.md`, `PROGRESS.md`: spec, query/index map, checklist and review findings.
- `TEST_RESULTS.md`: current verified CI result, history and outstanding production gates.

## Selected-baseline comparison

Compared with `feature/pachattar-design-foundation-phase-1`, the source diff reports 1,743 added lines and 3 deleted blank lines in `firestore.rules`. No pre-existing U2U rule expression was deleted. The live Firebase project must still be independently exported and compared.

## Current targeted security properties

- Self-service Market profile preference updates require verified email.
- Task acceptance must atomically assign the task, accept the chosen proposal, decrement the poster's open-task counter and create a new deterministic Market conversation.
- The Emulator suite tests both successful acceptance with a conversation and denial when the same transaction omits it.
- Applied queries require `proposerId == auth.uid`; Market chat is separate and text-only in v1.
- Existing U2U chat and Nearby-transfer rules remain unchanged.

## Latest CI evidence

[Run #24](https://github.com/Webster-02/Superior-Student/actions/runs/39856525223), tested commit `46ce86ce325135814963fb5e077b6257694731cb`:

- Static validation: **39 passed, 0 failed**.
- Firestore Rules Emulator: **67 passed, 0 failed, 0 skipped**.
- Expression-ceiling diagnostic count: **0**; workflow guard passed.
- Artifact ID: `12459119458`.

## Still blocking production

Admin audit-log/notice atomicity and exact lifecycle-counter semantics need review and regression coverage. Live production Rules/index exports, Auth/profile verification, historic proposal `proposerId` migration, and separate staging tests remain outstanding. The original addendum is present and hash-checked, but its semantic gap review is unfinished.

No Firebase deployment or merge to `main` has occurred.
