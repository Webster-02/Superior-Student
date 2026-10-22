# Base-to-candidate rules diff and merge changelog

Selected baseline: `feature/pachattar-design-foundation-phase-1`, commit `759eed423e7046d7fe127226945557a8fe4fa918`. This is a source-branch comparison, not a live Firebase export.

## Existing U2U chat and Nearby rules

- Baseline rules blob: `2944d790949a1fd93414a851eaf1f3ca6fef8922`.
- Current candidate rules blob: `3bd08c780ebdb2eef69033260d6a86f4ceee4c2c`.
- Current GitHub compare reports **1,799 additions and 3 deletions** in `firestore.rules`.
- `DIFF_AGAINST_BASE.patch` is refreshed against the precise baseline and candidate blobs.
- This source diff does not establish that live production Rules/indexes match the selected baseline.

## Targeted Market hardening

- Market self-service profile preference updates require verified email.
- Task acceptance requires a new deterministic Market conversation to be created atomically with assignment, proposal acceptance and the poster counter change.
- Market config creation/update must link to a new immutable `adminLogs` entry in the same atomic write.
- Task/offer pin and moderation updates must link to a new immutable `adminLogs` entry and a new owner notice in the same write. Log identity, action, target, admin and timestamp are validated by Rules.
- The bulk suspension/ban flow links every listing hidden by that operation to the aggregate user-level log and removal notice and validates the same after-write suspended/banned profile.

## Latest test evidence

[Rules run #29](https://github.com/Webster-02/Superior-Student/actions/runs/39860003494), tested commit `181e347c14032a8b8ddfd5a0c314afe17111a016`, passed:
- Static validation: **41 passed, 0 failed**.
- Firestore Emulator: **67 passed, 0 failed, 0 skipped**.
- Expression-ceiling diagnostics: **0**; guard passed.
- Artifact ID: `12461586838`; candidate Rules blob: `3bd08c780ebdb2eef69033260d6a86f4ceee4c2c`.

[Android run #101](https://github.com/Webster-02/Superior-Student/actions/runs/39859898773) on app commit `a8b8e55d0b3cfec5f19323b31091572282c18d00` passed route checks, unit tests and debug APK build (artifact ID `12461825353`).

## Release gates

Report status, support operations and direct account warning/suspension-profile actions still require Rules-enforced log/notice linkage. Clarify lifecycle counters; export/hash live Rules/indexes; verify Auth/profile prerequisites; dry-run/back up/verify historic proposal `proposerId` backfill; deploy to a separate staging project; and obtain explicit approval. Use the actual live export for rollback unless its identity with the baseline is proven.

No Firebase deployment or merge to `main` has occurred.
