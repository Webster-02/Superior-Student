# Rules change summary

The U2U Market Firestore candidate is on `review/market-rules`. It has not been merged to `main` or deployed to Firebase.

## Source comparison

Compared with `feature/pachattar-design-foundation-phase-1` at base commit `759eed423e7046d7fe127226945557a8fe4fa918`:

- Base rules blob: `2944d790949a1fd93414a851eaf1f3ca6fef8922`.
- Current candidate rules blob: `3bd08c780ebdb2eef69033260d6a86f4ceee4c2c`.
- GitHub compare reports **1,799 additions and 3 deletions** in `firestore.rules`.
- `firestore.base.rules` remains a byte-identical source-branch snapshot. This does not prove the live Firebase Rules match that baseline.

## Targeted security properties verified by CI

- Self-service Market profile preference changes require verified email.
- Task acceptance requires the accepted proposal, poster counter decrement and a new deterministic Market conversation in the same atomic write.
- Market config create/update requires a newly created matching `adminLogs` entry in the same atomic write.
- Task/offer admin pin/moderation updates require new log and owner-notice documents referenced from the listing document in the same atomic write. Rules validate current admin, action/target, new-document state, timestamp and notice/listing relationship.
- Bulk suspend/ban hides currently eligible tasks/offers using the aggregate user-level log and removal notice; the same after-state suspended/banned profile is required.
- Regression tests reject direct listing pins without log/notice and a mismatched audit target type; valid hide/restore, pin/unpin, bulk suspension, config changes and task acceptance remain green.

## Latest Rules and Android test evidence

[Rules run #29](https://github.com/Webster-02/Superior-Student/actions/runs/39860003494), tested commit `181e347c14032a8b8ddfd5a0c314afe17111a016`:
- Static validation: **41/41 passed**.
- Firestore Rules Emulator: **67/67 passed**, 0 failed, 0 skipped.
- Expression-ceiling diagnostics: **0**; guard passed.
- Artifact ID: `12461586838`; current Rules blob: `3bd08c780ebdb2eef69033260d6a86f4ceee4c2c`.

[Android run #101](https://github.com/Webster-02/Superior-Student/actions/runs/39859898773), app commit `a8b8e55d0b3cfec5f19323b31091572282c18d00`:
- Route checks, unit tests and debug APK build passed.
- APK artifact ID: `12461825353`.

## Still open / no production approval

Report-status operations, support reply/resolve, and direct account warning/lift-suspension/profile-status mutations still need their log/notice obligations enforced by Rules. Lifecycle-counter semantics require clarification. Production Rules/index exports, Auth/profile prerequisites, historic proposal `proposerId` migration, separate staging verification and explicit approval remain outstanding.

No Firebase deployment or merge to `main` has occurred.
