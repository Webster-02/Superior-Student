# Rules change summary

The U2U Market Firestore candidate is on review/market-rules. It has not been merged to main or deployed to Firebase.

## Current base comparison

Compared with feature/pachattar-design-foundation-phase-1 at base commit 759eed423e7046d7fe127226945557a8fe4fa918:

- Base rules blob: 2944d790949a1fd93414a851eaf1f3ca6fef8922.
- Current candidate rules blob: 8324f3bde6ee72d051a5635ba44ae73388439e02.
- GitHub compare reports **1,737 added lines and 3 deleted lines** in firestore.rules. The three deleted lines are blank lines only; no existing rule expression was deleted. Existing U2U auth, friend-request, chat and Nearby rule logic is preserved.
- The selected baseline snapshot firestore.base.rules matches the base branch's rules blob. This is a source-branch comparison only, not a comparison to live Firebase production rules.
- The candidate blob is identical to the rules blob verified by CI run #22.

## Important Market behavior

- Regular student Market posting requires a verified email and an existing U2U profile at profiles/{uid}; admin operations resolve through admins/{uid} and do not require a student profile.
- Proposal proposerId is immutable and equals the proposal document ID and author UID at create. Applied collection-group reads are scoped to resource.data.proposerId == request.auth.uid; task-level poster access remains available.
- The assigned-helper completion alternative is absent; the Market assigned-to-completed transition requires the task poster UID. Both participants can rate a completed task with the matching stats mutation.
- Content edits are validated like creates and require marketUserCanPost(owner); allowed lifecycle cleanup remains separately authorized with paired counter checks.
- Market chat is separate from legacy U2U chat, uses deterministic participant-sorted IDs and text-only messages, and excludes admins.
- Admin Limits remain read-only. Only announcement, blocked words and non-negative rank weights are editable; weights must sum to 100.

## Latest CI evidence

[GitHub Actions run #22](https://github.com/Webster-02/Superior-Student/actions/runs/39603954521) tested commit 2a136f1cc8478a27b0ef101a7a3c0ec8bde86ad9, whose rules blob matches the current branch:

- npm ci: exit 0.
- Static validation: **37/37 passed**.
- Firestore Emulator: **65/65 passed**, zero failed or skipped.
- Expression-ceiling diagnostics: **0**; CI guard passed.
- Artifact: firestore-rules-test-results, ID 12346912626.

The earlier run 39533202614 is historical and contained 39 expression-ceiling diagnostics. It is not the latest result. See TEST_RESULTS.md for run history and remaining production gates.

The original MARKET_SPEC_ADDENDUM_RULES_PHASE.md is present and its exact source bytes are verified by the static test. Semantic contradictions/gaps identified during review are documented in PROGRESS.md and still need resolution before release.

No live Firebase deployment or merge to main has been performed.
