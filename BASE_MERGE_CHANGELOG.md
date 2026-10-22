# Base-to-candidate rules diff and merge changelog

Selected baseline: feature/pachattar-design-foundation-phase-1, commit 759eed423e7046d7fe127226945557a8fe4fa918. The current branch comparison was made against that source commit, not against a live Firebase export.

## Existing U2U chat and Nearby rules

- Baseline rules blob: 2944d790949a1fd93414a851eaf1f3ca6fef8922.
- Current candidate rules blob: 8324f3bde6ee72d051a5635ba44ae73388439e02.
- GitHub compare reports 1,737 added lines and 3 deleted lines in firestore.rules. The three deleted lines are blank lines; the existing rule expressions were preserved. The current diff artifact DIFF_AGAINST_BASE.patch has been refreshed from GitHub's compare output for these exact base and candidate blobs.
- The candidate preserves the existing U2U auth, friend, chat, read-state, reaction, typing and Nearby-transfer rule logic. The separate marketConversations collection adds the Market-specific policy.
- This verifies source-branch preservation only. Live production rules and indexes must still be exported and compared before any rollout.

## Additive Market behavior

- Market profile, listing, counter, proposal, report, rating/statistics, notice, support, admin config/log and separate marketConversations rule blocks are added.
- Regular student Market posting requires a verified email and profiles/{uid}; admin operations use admins/{uid} and do not require a student profile.
- Proposal proposerId is immutable and equals the proposal document ID and authenticated UID at create. Applied collection-group reads must include the proposerId == auth.uid constraint.
- The assigned-helper completion branch was removed from Market behavior; assigned-to-completed requires the task poster. Both participants can rate a completed task with the paired stats update.
- Content edits use create-equivalent validation and require marketUserCanPost(owner); allowed lifecycle cleanup uses separate counter-linked validation.
- Market chat uses sorted participant UIDs in deterministic IDs, text-only messages, validated previews/read state, and excludes admins.
- Admin Limits are read-only. Only announcement, blocked words and non-negative rank weights are editable; weights sum to 100.

## Latest test evidence

[Run #22](https://github.com/Webster-02/Superior-Student/actions/runs/39603954521), tested commit 2a136f1cc8478a27b0ef101a7a3c0ec8bde86ad9, passed:
- Static validation: 37 passed, 0 failed.
- Firestore Emulator: 65 passed, 0 failed, 0 skipped across 14 suites.
- 1,000-expression-ceiling diagnostics: 0; the workflow guard passed.
- Artifact ID: 12346912626 (firestore-rules-test-results).

The previous run 39533202614 had 39 such diagnostics in an older snapshot. That historical result is not the current CI result. The test and review branch have not been merged to main, and no Firebase deployment occurred.

## Addendum and release gates

MARKET_SPEC_ADDENDUM_RULES_PHASE.md is present on the branch and verified by the static test. Its semantic gap review remains open; see PROGRESS.md. Do not mark the package production-ready solely because CI is green.

Before production: export and hash actual live production rules/indexes; verify email-verification and U2U-profile prerequisites; dry-run/back up/backfill/verify legacy proposal proposerId values using a trusted one-off Admin SDK operation; test on a separate staging project; attach the test artifacts; and obtain explicit approval. Use the saved live export, not the branch snapshot, as the rollback source unless byte-identity with production has been proven.
