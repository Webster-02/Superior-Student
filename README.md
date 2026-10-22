# U2U Market Firestore rules — review branch

**Review branch:** review/market-rules. The candidate has not been merged to main and has not been deployed to Firebase. No Firebase publish or deploy is authorized by this review package.

## Package contents

- firestore.rules and firestore.indexes.json: candidate rules and composite indexes.
- firestore.base.rules and rollback/firestore.rollback.branch-snapshot.rules: snapshots of the selected source-branch rules, not proof that live production matches them.
- DIFF_AGAINST_BASE.patch and BASE_DIFF_VALIDATION.txt: current source-branch rules diff and its baseline hashes.
- tests/firestore.rules.test.mjs, tests/static-validate.mjs, package.json, package-lock.json, and firebase.json: Emulator tests, static validation and pinned dependencies.
- .github/workflows/firestore-rules-test.yml: review-branch workflow that runs both suites, checks the expression ceiling, and uploads raw outputs.
- MARKET_FINAL_SPEC.md, MARKET_SPEC_ADDENDUM_RULES_PHASE.md, INDEX_QUERY_MAP.md, DEPLOY_CHECKLIST.md and PROGRESS.md: specification, query/index map, release checklist and review findings.
- TEST_RESULTS.md: current verified CI result, historical run notes and remaining production gates.

## Existing U2U chat and Nearby

Compared with feature/pachattar-design-foundation-phase-1, the source diff reports 1,737 added lines and 3 deleted blank lines in firestore.rules. No existing rule expression was deleted; the original U2U authentication, friend-chat and Nearby-transfer rule logic is preserved. This comparison is against a selected Git baseline, not a live Firebase export.

Admin exclusion applies to the new marketConversations collection. Market admin operations use admins/{uid} and do not require admin profiles/{uid} or marketProfiles/{uid}. Regular student Market writes require an existing profiles/{uid} U2U profile.

## Applied proposals and task completion

A proposal must carry proposerId == proposal document ID == request.auth.uid, and updates cannot change proposerId. The collection-group Applied query must be filtered by proposerId equal to the signed-in user's UID. The task poster retains task-level proposal access. Historical proposal data needs an audited proposerId backfill before rollout.

The assigned-to-completed Market task transition is poster-only. Both participants may rate the completed task with a matching userStats update. Market chat is a separate, text-only v1 feature; existing U2U chat and Nearby behavior remain independent.

## Current CI evidence

Latest verified run: [Firestore Rules Review Tests — run #22](https://github.com/Webster-02/Superior-Student/actions/runs/39603954521), testing commit 2a136f1cc8478a27b0ef101a7a3c0ec8bde86ad9. The current rules blob is identical to the tested blob.

- npm ci: exit 0.
- Static preflight: **37 passed, 0 failed**.
- Firestore Rules Emulator: **65 passed, 0 failed, 0 skipped**, across 14 suites.
- Expression-ceiling diagnostic count: **0**; workflow guard passed.
- Artifact: firestore-rules-test-results, ID 12346912626.

An older run (39533202614) produced 39 expression-ceiling diagnostics and is retained only as historical evidence. It must not be described as the latest test result.

## Addendum and production gate

MARKET_SPEC_ADDENDUM_RULES_PHASE.md is present on this branch and checked by the static test. Its semantic contradiction/gap review is still open; see PROGRESS.md.

Before any production deployment, export and hash currently published production rules and indexes, compare them with the reviewed source baseline, confirm Auth/profile prerequisites, safely backfill and verify historical proposal proposerId values, and test all required flows in a separate staging project. Keep rollback copies of actual live rules/indexes. Obtain explicit product-owner approval before touching production.

No Firebase deployment or merge to main has occurred.
