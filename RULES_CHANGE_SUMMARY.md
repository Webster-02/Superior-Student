# Rules change summary

The U2U Market Firestore candidate is on `review/market-rules`. It has not been merged to `main` or deployed to Firebase.

## Source comparison

Compared with `feature/pachattar-design-foundation-phase-1` at base commit `759eed423e7046d7fe127226945557a8fe4fa918`:

- Base rules blob: `2944d790949a1fd93414a851eaf1f3ca6fef8922`.
- Current candidate rules blob: `05f2938af5782682cd81336a4e57b69501acbbd0`.
- GitHub compare reports **1,826 additions and 3 deletions** in `firestore.rules`.
- `firestore.base.rules` remains a byte-identical source-branch snapshot. This does not prove the live Firebase Rules match that baseline.

## Targeted security properties verified by CI

- Self-service Market profile preference changes require verified email.
- Task acceptance requires the accepted proposal, poster counter decrement and a new deterministic Market conversation in the same atomic write.
- Market config create/update requires a newly created matching `adminLogs` entry in the same atomic write.
- Task/offer admin pin/moderation updates require new log and owner-notice documents referenced from the listing document in the same atomic write. Rules validate current admin, action/target, new-document state, timestamp and notice/listing relationship.
- Report-status updates require a new matching `set_report_status` log and a new reporter notice linked to the report, in the same atomic write.
- Bulk suspend/ban hides currently eligible tasks/offers using the aggregate user-level log and removal notice; the same after-state suspended/banned profile is required.
- Regression tests reject direct listing pins without log/notice and a mismatched audit target type; valid hide/restore, pin/unpin, bulk suspension, config changes and task acceptance remain green.

## Latest Rules and Android test evidence

[Rules run #30](https://github.com/Webster-02/Superior-Student/actions/runs/39862784731), tested commit `b19472ad8d9d7ce0811285e66d9dfc0fadbc7fba`:
- Static validation: **42/42 passed**.
- Firestore Rules Emulator: **67/67 passed**, 0 failed, 0 skipped.
- Expression-ceiling diagnostics: **0**; guard passed.
- Artifact ID: `12462911143`; current Rules blob: `05f2938af5782682cd81336a4e57b69501acbbd0`.

[Android run #102](https://github.com/Webster-02/Superior-Student/actions/runs/39862887618), app commit `3c6da1a713ec836d7b5229324ac3bcb65dc415af`:
- Route checks, unit tests, debug APK build and APK verification/upload passed.
- APK artifact ID: `12463315396`.

## Still open / no production approval

Support reply/resolve and direct account warning/lift-suspension/profile-status mutations still need their log/notice obligations enforced by Rules. Lifecycle-counter semantics require clarification. Production Rules/index exports, Auth/profile prerequisites, historic proposal `proposerId` migration, separate staging verification and explicit approval remain outstanding.

No Firebase deployment or merge to `main` has occurred.
