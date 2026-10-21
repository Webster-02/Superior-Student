# U2U Market Firestore rules — review branch

**Review branch:** `review/market-rules`. The candidate is committed for review but has not been merged to `main` and has not been deployed to Firebase. `firestore.rules` is canonical; `firestore.market.rules.fragment` is a reference fragment and is not deployable alone.

## Package contents

- `firestore.rules`, `firestore.indexes.json`: merged candidate rules and composite indexes.
- `firestore.base.rules`, `rollback/firestore.rollback.branch-snapshot.rules`: byte-for-byte snapshot of the selected source-branch rules. This is **not** proof that live production rules match the branch.
- `DIFF_AGAINST_BASE.patch`, `BASE_DIFF_VALIDATION.txt`, `RULES_CHANGE_SUMMARY.md`: diff, base-integrity checks and change summary.
- `tests/firestore.rules.test.mjs`, `tests/static-validate.mjs`, `package.json`, `package-lock.json`, `firebase.json`: Emulator tests, static validation and pinned dependencies.
- `.github/workflows/firestore-rules-test.yml`: review-branch CI workflow that runs both test suites and uploads real output.
- `MARKET_FINAL_SPEC.md`, `INDEX_QUERY_MAP.md`, `DEPLOY_CHECKLIST.md`: spec, query/index mapping, and deployment safety checklist.
- `TEST_RESULTS.md`: successful CI result, failed-first-run history and remaining diagnostics.

## Existing U2U chat and Nearby

Compared with `feature/pachattar-design-foundation-phase-1`, the non-Market prefix and the suffix from `match /profiles/{uid}` are byte-identical. The legacy `activeChatConversationMember()` helper and existing `chatConversations`, messages, read-state, reaction, typing and Nearby-transfer rules are preserved. The full compare reports **1,287 additions and 0 deletions in `firestore.rules`**. Legacy friend-chat and Nearby positive-path tests pass.

Admin exclusion is applied to the new `marketConversations` collection; Market admin operations are authorized through `admins/{uid}` and do not require an admin `profiles/{uid}` or `marketProfiles/{uid}`. Regular student Market writes do require a U2U profile at `profiles/{uid}`.

## Applied proposals and task completion

A proposal must be created with `proposerId == proposal document ID == request.auth.uid`; updates cannot change `proposerId`. The recursive collection-group `get/list` rule authorizes only documents where `resource.data.proposerId == request.auth.uid`, so the Applied query must include `where('proposerId', '==', auth.uid)`. The task poster retains task-level proposal access. Historical proposal documents need a trusted Admin SDK backfill before rollout.

The `assigned -> completed` Market task transition is poster-only; the assigned-helper completion alternative is absent. Ratings for completed tasks remain available to both participants with the matching `userStats` change in the same batch.

## Posting validation and configuration

Task and offer content edits reuse create-equivalent validation and require `marketUserCanPost(owner)`. Suspended/banned owners cannot edit content but may perform allowed, counter-linked lifecycle cleanup. Limits enforced in Rules are 3 open tasks, 10 proposals per rolling 24-hour window, 15 proposals per task, 5 active offers and 3 reports to auto-hide. The admin UI must render Limits read-only. Only `announcement`, `blockedWords` and non-negative `rankWeights` are editable; weights must sum to 100.

Market chat is a separate collection and **text-only for v1**. It excludes files, reactions, typing indicators and Nearby transfer. Conversation IDs encode the kind, listing and sorted participant UIDs; only the validated message preview, participant read state and `updatedAt` can change.

## Real GitHub Actions result

Latest run: [Firestore Rules Review Tests — run 39533202614](https://github.com/Webster-02/Superior-Student/actions/runs/39533202614), commit `d5490826a6ee55b5d5861382068fb9571e769227`.

- `npm ci`: exit 0 (781 packages installed).
- Static preflight: **31 passed, 0 failed**.
- Firestore Rules Emulator: **65 tests, 65 passed, 0 failed, 0 skipped**, across 14 suites.
- GitHub Actions job: success.
- Artifact: [firestore-rules-test-results.zip](https://github.com/Webster-02/Superior-Student/actions/runs/39533202614/artifacts/12315439964), SHA-256 `f459f2730e9bb09e3ae311859c33ce90b17a5477bc01135ef9fcf0429a635060`.

**Important log caveat:** the successful Emulator log also contains 39 diagnostic lines mentioning that evaluation reached Firestore Rules' 1,000-expression ceiling while evaluating some requests. They did not cause a test assertion to fail in this run, but this means the log is not warning-free. Review and simplify these paths before production approval. This is distinct from the per-operation/batch document-access-call limits; passing the required positive-path tests provides evidence for those specific tested flows, not every possible request shape.

## Missing source addendum

The requested `MARKET_SPEC_ADDENDUM_RULES_PHASE.md` was not present in the accessible attachments or the source branch. It has not been fabricated or represented as a verbatim copy. The supplied explicit decisions—immutable `proposerId`, read-only limits, non-negative rank weights and text-only Market chat—are reflected in `MARKET_FINAL_SPEC.md`, but the original addendum still needs to be supplied before the documentation reconciliation can be considered complete.

## Production gate

Do not deploy until the live production Rules and indexes have been exported and compared, existing email-verification coverage has been checked in Firebase Authentication, historical `proposerId` documents have been backfilled with a trusted one-off Admin SDK operation, and the package has been verified on a separate staging Firebase project. Store live production exports separately from the selected branch snapshot for rollback. No deployment or merge to `main` has been performed.
