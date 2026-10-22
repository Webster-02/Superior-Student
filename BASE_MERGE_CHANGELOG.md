# Base-to-candidate rules diff and merge changelog

Selected baseline: `feature/pachattar-design-foundation-phase-1`, commit `759eed423e7046d7fe127226945557a8fe4fa918`. This is a source-branch comparison, not a live Firebase export.

## Existing U2U chat and Nearby rules

- Baseline rules blob: `2944d790949a1fd93414a851eaf1f3ca6fef8922`.
- Current candidate rules blob: `15ae62777f1d9486c3bd15f364b03a82b934b34e`.
- Current GitHub compare reports **1,758 additions and 3 deletions** in `firestore.rules`; existing U2U rule expressions remain preserved.
- `DIFF_AGAINST_BASE.patch` is refreshed against the precise baseline and candidate blobs.
- This source diff does not establish that live production Rules or indexes match the selected baseline.

## Targeted Market hardening

- Market self-service profile preference updates require verified email.
- Task acceptance requires the new deterministic Market conversation to be created atomically with task assignment, selected proposal acceptance and poster counter update.
- Market config updates require `lastAuditLogId` to point to a newly created immutable `adminLogs` entry written in the same atomic write. The linked action and target must be `update_market_config` / `config` / `market`, with matching admin and request timestamp.
- The app repository writes the audit reference and immutable log in one transaction. The strict config decoder accepts the optional metadata without adding it to ordinary config serialization.
- Other admin operations still need the equivalent Rules-enforced log/notice linkage; the config safeguard does not cover them.

## Latest test evidence

[Rules run #27](https://github.com/Webster-02/Superior-Student/actions/runs/39858637212), tested commit `1f5536f4387cb7254e4a0ccae04c896b1a68b511`, passed:
- Static validation: **40 passed, 0 failed**.
- Firestore Emulator: **67 passed, 0 failed, 0 skipped**.
- Expression-ceiling diagnostics: **0**; guard passed.
- Artifact ID: `12459668968`.

[Android run #100](https://github.com/Webster-02/Superior-Student/actions/runs/39858373895) on app commit `4a74df5e6d7d383f4f38fda716b8eaafed4ebdb0` passed unit tests and built the debug APK (artifact ID `12459833836`).

## Release gates

Finish audit linkage for listing moderation/pins, report status, support and account actions; clarify lifecycle counters; export/hash live Rules/indexes; verify Auth/profile prerequisites; dry-run/back up/verify any historical proposal `proposerId` backfill; deploy to a separate staging project; and obtain explicit approval. Use the actual live export for rollback unless byte identity with the branch baseline is proven.

No Firebase deployment or merge to `main` has occurred.
