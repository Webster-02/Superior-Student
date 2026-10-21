# Base-to-candidate rules diff and merge changelog

Selected baseline: `feature/pachattar-design-foundation-phase-1`, whose `firestore.rules` Git blob is `2944d790949a1fd93414a851eaf1f3ca6fef8922`.

## Existing U2U chat and Nearby rules are preserved

The final candidate's prefix before the Market insertion and suffix from `match /profiles/{uid}` onward are byte-identical to the selected baseline. The `activeChatConversationMember(conversationId)` helper is also byte-identical. The branch compare reports **1,287 additions and 0 deletions in `firestore.rules`**. This is proven against the selected Git branch snapshot, not against live production rules.

## Additive Market additions

- Market profile, listing, counter, proposal, report, rating/statistics, notice, support, admin config/log and separate `marketConversations` rule blocks.
- Regular student Market posting requires a verified email plus an existing `profiles/{uid}` U2U profile; admin operations resolve through `admins/{uid}` and don't require student profile documents.
- Proposal `proposerId` is immutable and equals the proposal document ID and authenticated UID at create. Applied collection-group reads require `resource.data.proposerId == request.auth.uid`; task-specific poster access remains available.
- The assigned-helper completion branch was removed; the `assigned -> completed` Market task transition requires `request.auth.uid == resource.data.posterId`. Both participants can still rate a completed task with the corresponding same-batch stats mutation.
- Content edits use create-equivalent validation and require `marketUserCanPost(owner)`; lifecycle cleanup remains separately authorized with paired counter updates.
- Market chat uses deterministic IDs with sorted participant UIDs, text-only messages, validated previews/read state and no admin access. Legacy U2U chat/Nearby rules remain unchanged.
- Admin Limits are read-only. Only announcement, blocked words and non-negative rank weights are editable; weights sum to 100.

## Test-fix history

The first real CI run [39531687165](https://github.com/Webster-02/Superior-Student/actions/runs/39531687165) failed 9 of 65 Emulator assertions. Follow-up changes corrected owner-denial fixtures, restored a valid legacy Nearby public-key test fixture, split Market counter validation by mutation kind, cached repeated reads, made optional `assignedTo` checks safe, and reduced redundant Market conversation validation.

The latest run [39533202614](https://github.com/Webster-02/Superior-Student/actions/runs/39533202614) succeeded: `npm ci` exit 0, static 31/31, Emulator 65/65, zero failed/skipped tests. [Artifact zip](https://github.com/Webster-02/Superior-Student/actions/runs/39533202614/artifacts/12315439964), SHA-256 `f459f2730e9bb09e3ae311859c33ce90b17a5477bc01135ef9fcf0429a635060`.

**Raw log caveat:** the latest log still contains 39 diagnostics referencing the 1,000-expression evaluator ceiling during some rule evaluations. They did not cause test assertions to fail, but the log is not warning-free. This differs from document access-call limits; the positive high-cost transaction and batch scenarios passed. The affected expressions should be reviewed before any production approval.

## Missing source document and deployment gate

The requested `MARKET_SPEC_ADDENDUM_RULES_PHASE.md` was not accessible in this session and is absent from the starting branch. It has not been recreated or marked verbatim. Supply the original before addendum reconciliation is complete.

Before any deployment, export live production rules and indexes, compare them to this selected baseline, verify email-verification coverage, backfill historical `proposerId` fields using a trusted one-off Admin SDK migration, and validate the package in a separate staging Firebase project. Save timestamped live production exports separately for rollback. This branch is review only: no merge to `main` and no Firebase deployment has been performed.
