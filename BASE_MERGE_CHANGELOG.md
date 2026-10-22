# Base-to-candidate rules diff and merge changelog

Selected baseline: `feature/pachattar-design-foundation-phase-1`, commit `759eed423e7046d7fe127226945557a8fe4fa918`. This is a source-branch comparison, not a live Firebase export.

## Existing U2U chat and Nearby rules

- Baseline rules blob: `2944d790949a1fd93414a851eaf1f3ca6fef8922`.
- Current candidate rules blob: `05f2938af5782682cd81336a4e57b69501acbbd0`.
- Current GitHub compare reports **1,826 additions and 3 deletions** in `firestore.rules`.
- `DIFF_AGAINST_BASE.patch` is refreshed against the precise baseline and candidate blobs.
- This source diff does not establish that live production Rules/indexes match the selected baseline.

## Targeted Market hardening

- Market self-service profile preference updates require verified email.
- Task acceptance requires a new deterministic Market conversation to be created atomically with assignment, proposal acceptance and the poster counter change.
- Market config creation/update must link to a new immutable `adminLogs` entry in the same atomic write.
- Task/offer pin and moderation updates must link to a new immutable `adminLogs` entry and a new owner notice in the same write. Log identity, action, target, admin and timestamp are validated by Rules.
- Report-status updates must include a new immutable `set_report_status` audit log and a new reporter notice linked to that report in the same atomic write.
- The bulk suspension/ban flow links every listing hidden by that operation to the aggregate user-level log and removal notice and validates the same after-write suspended/banned profile.

## Latest test evidence

[Rules run #30](https://github.com/Webster-02/Superior-Student/actions/runs/39862784731), tested commit `b19472ad8d9d7ce0811285e66d9dfc0fadbc7fba`, passed:
- Static validation: **42 passed, 0 failed**.
- Firestore Emulator: **67 passed, 0 failed, 0 skipped**.
- Expression-ceiling diagnostics: **0**; guard passed.
- Artifact ID: `12462911143`; candidate Rules blob: `05f2938af5782682cd81336a4e57b69501acbbd0`.

[Android run #102](https://github.com/Webster-02/Superior-Student/actions/runs/39862887618) on app commit `3c6da1a713ec836d7b5229324ac3bcb65dc415af` passed route checks, unit tests, debug APK build and APK verification/upload (artifact ID `12463315396`).

## Release gates

Support reply/resolve operations and direct account warning/suspension-profile actions still require Rules-enforced log/notice linkage. Clarify lifecycle counters; export/hash live Rules/indexes; verify Auth/profile prerequisites; dry-run/back up/verify historic proposal `proposerId` backfill; deploy to a separate staging project; and obtain explicit approval. Use the actual live export for rollback unless its identity with the baseline is proven.

No Firebase deployment or merge to `main` has occurred.
