# Base-to-candidate rules diff and merge changelog

Selected baseline: `feature/pachattar-design-foundation-phase-1`, commit `759eed423e7046d7fe127226945557a8fe4fa918`. This is a source-branch comparison, not a live Firebase export.

## Existing U2U chat and Nearby rules

- Baseline rules blob: `2944d790949a1fd93414a851eaf1f3ca6fef8922`.
- Current candidate rules blob: `b039f9f896c27314eb1f57594e0c1fe911be92a6`.
- Current GitHub compare reports **1,743 added lines and 3 deleted blank lines** in `firestore.rules`. No existing rule expression was deleted.
- `DIFF_AGAINST_BASE.patch` has been refreshed to represent the exact base and current candidate rules blobs.
- Original U2U authentication, friend request, chat, read-state, reactions, typing and Nearby-transfer rule logic remains preserved.
- This source-branch comparison does not establish that live production Rules/indexes match the selected baseline.

## Targeted Market hardening

- Market self-service profile preference changes require a verified email.
- Task acceptance now requires a new deterministic Market conversation to be created atomically with the task assignment, accepted proposal and poster counter update.
- The positive acceptance transaction test succeeds; a new negative test denies the transaction when the conversation is omitted.
- Existing student/admin, proposal, report, rating and Market chat authorization tests remain in the Emulator suite.

## Latest test evidence

[Run #24](https://github.com/Webster-02/Superior-Student/actions/runs/39856525223), tested commit `46ce86ce325135814963fb5e077b6257694731cb`, passed:
- Static validation: 39 passed, 0 failed.
- Firestore Emulator: 67 passed, 0 failed, 0 skipped.
- 1,000-expression-ceiling diagnostics: 0; the CI guard passed.
- Artifact ID: `12459119458`.

Older successful run #22 was the verified baseline. The much older run 39533202614 contained 39 diagnostics in an earlier rules snapshot and is retained only as historical evidence.

## Release gates

Admin audit log and notice atomicity remains an open specification gap, as does clearer lifecycle-counter wording. Before production: export/hash live Rules/indexes; verify email-verification and U2U-profile prerequisites; dry-run, back up and validate historical proposal `proposerId` backfill; test all required flows on a separate staging project; and obtain explicit approval. Use the actual live export for rollback unless byte identity with the branch baseline has been proven.

No Firebase deployment or merge to `main` has occurred.
