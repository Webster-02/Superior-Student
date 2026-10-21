# U2U Market Firestore rules — review package

**Review-only package.** No repository file, branch, GitHub Action run, or Firebase project was modified or deployed. `firestore.rules` is the canonical candidate. `firestore.market.rules.fragment` is an updated insertion reference, not a deployable standalone Rules file.

## Package contents

- `firestore.rules` — merged candidate with Market rules added to the selected base.
- `firestore.base.rules` and `rollback/firestore.rollback.branch-snapshot.rules` — selected branch snapshot for diff and rollback reference.
- the GitHub compare view and `BASE_DIFF_VALIDATION.txt` — unified diff and validation showing only additive Market insertions.
- `firestore.indexes.json` and `INDEX_QUERY_MAP.md` — 46 composite indexes and query-to-index/rules mapping.
- `tests/firestore.rules.test.mjs` — Emulator security-rule tests, including allow/deny, chat/Nearby regressions and five high-cost batch/transaction paths.
- `tests/static-validate.mjs` — offline preflight checks; this does not compile the rules.
- `.github/workflows/firestore-rules-test.yml` — GitHub Actions workflow file only. It has not been added to a repository or run because this task explicitly forbids repository changes.
- `TEST_RESULTS.md`, `TEST_RESULTS_STATIC_CHECK.txt`, `artifacts/` — actual local command results and the current blocker.
- `reference/MARKET_FINAL_SPEC.md` — updated panel/schema spec, including read-only limits.

## Requested changes applied

### Existing U2U Chat / Nearby

The existing `activeChatConversationMember()` function and existing `chatConversations/{conversationId}` parent rules are byte-identical to the base. The previous admin checks were removed from those existing rules. Existing friend chat and Nearby transfer positive/negative regression tests remain; the admin is tested as an account that is not a chat participant. Admin exclusion remains on the new `marketConversations` feature, as required.

### Applied proposals

A proposal created at `tasks/{taskId}/proposals/{userId}` must set `proposerId == userId == request.auth.uid`. Existing update allow-lists leave that field immutable. The recursive collection-group rule authorizes get/list only for documents where `resource.data.proposerId == request.auth.uid`; therefore, the collection-group Applied query must constrain `proposerId == auth.uid`. The existing task poster can still list proposals at a specific task through the nested rule.

**Migration note:** old proposal documents created before this field existed will not appear in the new Applied collection-group query. Before rollout, identify and backfill historical proposal documents with `proposerId` equal to their document ID using a trusted migration/Admin SDK; client rules intentionally do not allow adding or changing this immutable field. The task poster’s task-specific proposal list remains governed by its existing nested rule.

### Config and content edit validation

Config limits remain hard-coded in Rules at `openTasks=3`, `proposalsPerDay=10`, `proposalsPerTask=15`, `activeOffers=5`, and `reportsToHide=3`. The admin spec now marks Limits read-only; only `announcement`, `blockedWords`, and `rankWeights` are editable. Each weight is checked for non-negativity and the sum remains 100.

Task and offer creation use the same reusable content validators as content edits. A content edit requires `marketUserCanPost(owner)` and valid content values. Lifecycle/cleanup-only transitions stay on separate rule branches, including counter-linked cancel, pause, expire and remove paths, so a suspended/banned owner can clean up a listing without being allowed to edit its content.

### Market chats

The ID is enforced as `kind_listingId_uidLow_uidHigh` with participants sorted by UID. Creating a task chat in the same batch as task acceptance is supported through `getAfter()` for the assigned state while retaining `get()` for already-assigned tasks. Only `updatedAt`, a validated `lastMessage` preview and the caller’s own `lastReadAt` map value may change; other fields are immutable. Admins have no Market conversation/message access.

## Access-call limits

The Emulator suite includes assertions intended to verify high-cost paths for task create, proposal create, accept transaction plus same-transaction conversation, report plus counters and admin suspend/hide plus notice/log. Firestore itself enforces the per-operation access-call limits (10 for a single document operation; at most 20 across a batch/transaction, with the per-write 10 limit also applying). A successful Emulator assertion is evidence the tested operation did not hit that limit; it does not print the exact internal count. **Because the Emulator suite could not run here, these cases remain unverified in this environment. Do not treat static checks as access-call proof.**

## Test commands and current status

Requirements: Node.js 20+ and Java, plus npm registry/network access.

```bash
npm install --no-audit --no-fund
npm run test:static
npm run test:emulator
```

The local static suite passed 23/23 checks. The dependency install failed with `EAI_AGAIN` resolving `registry.npmjs.org`; consequently, the local Emulator command returned exit code 127 (`firebase: not found`). The real output, including a second dependency-install retry, is saved in `TEST_RESULTS.md`, `TEST_RESULTS_STATIC_CHECK.txt`, and `artifacts/`. This is **blocked/not run**, not a claim that Emulator tests passed. The workflow is included in the package so it can be placed on an approved review branch; GitHub Actions cannot run a file that has not been added to a repository, and no repository changes were made here.

## Index/query compatibility

See `INDEX_QUERY_MAP.md` for every composite index and the corresponding query/rule constraint. The important list-query proofs are:

- Public task feed: `moderation == 'ok'` and `status == 'open'`.
- Public offer feed: `moderation == 'ok'` and `status == 'active'`.
- My Tasks: `posterId == auth.uid`; My Offers: `sellerId == auth.uid`.
- Applied: collection group `proposals` with `proposerId == auth.uid` and `orderBy(updatedAt, desc)`.
- Proposals for one task: use that task’s proposal subcollection; listing is allowed to the task poster.
- Market Chats: `participants array-contains auth.uid`, with verified, non-admin and blocking rules applied per document.
- Reports: student queries require `reporterId == auth.uid`; admin moderation queues require admin membership.
- Support and notices: user queries must be scoped to their own `userId`/notice path; broader support queues are admin-only.

The package does not contain production student/admin query implementations, so index shape compatibility is mapped from the approved Market spec/rules rather than independently verified against live app call sites.

## Before any deployment

1. Export currently deployed production rules and indexes from the correct Firebase Console project. Save them separately as `rollback/firestore.production-before-market.rules` and `rollback/firestore.production-before-market.indexes.json`, recording project ID and timestamp. The checked-in branch snapshot is not proven to equal production.
2. Compare those exports with `firestore.base.rules`. If they differ, reconcile/rebase the candidate on the live source before considering deployment.
3. Backfill existing proposal documents with immutable `proposerId` using a trusted Admin SDK migration, after verifying the intended document IDs. Check every existing Firebase Authentication user’s email verification status; Market posting/chat rules require `email_verified == true`.
4. Put this package on an approved review branch (not main) and run the included GitHub Actions workflow with Node 20 and Java. Require `npm run test:static` and `npm run test:emulator` to pass before approval. Investigate any access-call-limit failure rather than raising limits.
5. Verify implemented app/admin queries against `INDEX_QUERY_MAP.md`, confirm indexes build, then obtain explicit approval for a staging deployment first.

Only after those gates and explicit approval:

```bash
firebase deploy --only firestore:rules,firestore:indexes --project <PROJECT_ID>
```

No deploy command was run in this review.

## Rollback

1. Use the saved **live production export** as the source of truth. Copy `rollback/firestore.production-before-market.rules` back to `firestore.rules` and restore `rollback/firestore.production-before-market.indexes.json` to `firestore.indexes.json` if indexes changed. Do not assume `rollback/firestore.rollback.branch-snapshot.rules` matches production unless separately proven.
2. Run static and Emulator suites against the rollback candidate when the local Firebase toolchain/network is available.
3. After explicit rollback approval, run `firebase deploy --only firestore:rules,firestore:indexes --project <PROJECT_ID>` (or `--only firestore:rules` if indexes do not need restoration).
4. Verify the Console rules/index deployment and test friend chat, Nearby transfer, task/offer feeds, Applied/proposal views, accept/chat, reports, support and admin moderation. Rules rollback does not delete Market data or reverse already-written app documents.

## Production readiness caveats

This package remains review-only and is not production-approved. The local static check cannot prove Firebase Rules compilation, query correctness, access-call counts, live production equivalence, Firebase Auth verification coverage or the state of deployed indexes. Emulator test execution and review on a staging project are required before any deployment.

## Missing source input

The requested `MARKET_SPEC_ADDENDUM_RULES_PHASE.md` was not present in the files received by this session or in either repository branch. It has not been fabricated or marked as copied verbatim. Attach the original file before this documentation task can be considered complete.
