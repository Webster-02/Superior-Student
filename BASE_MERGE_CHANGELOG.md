# Base-to-candidate rules diff and merge changelog

Selected baseline: `feature/pachattar-design-foundation-phase-1`, commit `759eed423e7046d7fe127226945557a8fe4fa918`. This is a source-branch comparison, not a live Firebase export.

## Existing U2U chat and Nearby rules

- Baseline rules blob: `2944d790949a1fd93414a851eaf1f3ca6fef8922`.
- Current candidate rules blob: `4dd84e3cfcd9d2e91d1327b0ffd24e0ad2efbeb1`.
- Current GitHub compare reports **1,860 additions and 3 deletions** in `firestore.rules`.
- `DIFF_AGAINST_BASE.patch` is refreshed against the precise baseline and candidate blobs.
- This source diff does not establish that live production Rules/indexes match the selected baseline.

## Targeted Market hardening

- Market self-service profile preference updates require verified email.
- Task acceptance requires a new deterministic Market conversation to be created atomically with assignment, proposal acceptance and the poster counter change.
- Market config creation/update must link to a new immutable `adminLogs` entry in the same atomic write.
- Task/offer pin and moderation updates must link to a new immutable `adminLogs` entry and a new owner notice in the same write. Log identity, action, target, admin and timestamp are validated by Rules.
- Report-status updates must include a new immutable `set_report_status` audit log and a new reporter notice linked to that report in the same atomic write.
- Non-appeal support-ticket admin replies and resolutions must link to a new immutable audit log and a new owner notice in the same atomic write; owner reply restrictions stay separate.
- The bulk suspension/ban flow links every listing hidden by that operation to the aggregate user-level log and removal notice and validates the same after-write suspended/banned profile.

## Latest test evidence

[Rules run #32](https://github.com/Webster-02/Superior-Student/actions/runs/39865131476), tested commit `d0428fb7b6d6e2414a2d199d954564527193df78`, passed:
- Static validation: **43 passed, 0 failed**.
- Firestore Emulator: **68 passed, 0 failed, 0 skipped**.
- Expression-ceiling diagnostics: **0**; guard passed.
- Artifact ID: `12462414890`; candidate Rules blob: `4dd84e3cfcd9d2e91d1327b0ffd24e0ad2efbeb1`.

[Android run #103](https://github.com/Webster-02/Superior-Student/actions/runs/39864585562) on app commit `d1d95c4d3c4e9ff3fc5d8596cb0e74eb87d0392c` passed route checks, unit tests, debug APK build and APK verification/upload (artifact ID `12464190239`).

## Release gates

Appeal-ticket decision linkage and direct account warning/suspension-profile actions still require Rules-enforced log/notice linkage. Clarify lifecycle counters; export/hash live Rules/indexes; verify Auth/profile prerequisites; dry-run/back up/verify historic proposal `proposerId` backfill; deploy to a separate staging project; and obtain explicit approval. Use the actual live export for rollback unless its identity with the baseline is proven.

No Firebase deployment or merge to `main` has occurred.
