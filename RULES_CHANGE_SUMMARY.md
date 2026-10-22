# Rules change summary

The U2U Market Firestore candidate is on `review/market-rules`. It has not been merged to `main` or deployed to Firebase.

## Source comparison

Compared with `feature/pachattar-design-foundation-phase-1` at base commit `759eed423e7046d7fe127226945557a8fe4fa918`:

- Base rules blob: `2944d790949a1fd93414a851eaf1f3ca6fef8922`.
- Current candidate rules blob: `4dd84e3cfcd9d2e91d1327b0ffd24e0ad2efbeb1`.
- GitHub compare reports **1,860 additions and 3 deletions** in `firestore.rules`.
- `firestore.base.rules` remains a byte-identical source-branch snapshot. This does not prove the live Firebase Rules match that baseline.

## Targeted security properties verified by CI

- Self-service Market profile preference changes require verified email.
- Task acceptance requires the accepted proposal, poster counter decrement and a new deterministic Market conversation in the same atomic write.
- Market config create/update requires a newly created matching `adminLogs` entry in the same atomic write.
- Task/offer admin pin/moderation updates require new log and owner-notice documents referenced from the listing document in the same atomic write. Rules validate current admin, action/target, new-document state, timestamp and notice/listing relationship.
- Report-status updates require a new matching `set_report_status` log and a new reporter notice linked to the report, in the same atomic write.
- Non-appeal support admin replies/resolutions require a new matching `reply_support_ticket`/`resolve_support_ticket` audit log and owner notice in the same atomic write. User reply constraints are retained; the appeal-specific update path remains separate.
- Bulk suspend/ban hides currently eligible tasks/offers using the aggregate user-level log and removal notice; the same after-state suspended/banned profile is required.
- Regression tests reject direct listing pins without log/notice and a mismatched audit target type; valid hide/restore, pin/unpin, bulk suspension, config changes and task acceptance remain green.

## Latest Rules and Android test evidence

[Rules run #32](https://github.com/Webster-02/Superior-Student/actions/runs/39865131476), tested commit `d0428fb7b6d6e2414a2d199d954564527193df78`:
- Static validation: **43/43 passed**.
- Firestore Rules Emulator: **68/68 passed**, 0 failed, 0 skipped.
- Expression-ceiling diagnostics: **0**; guard passed.
- Artifact ID: `12462414890`; current Rules blob: `4dd84e3cfcd9d2e91d1327b0ffd24e0ad2efbeb1`.

[Android run #103](https://github.com/Webster-02/Superior-Student/actions/runs/39864585562), app commit `d1d95c4d3c4e9ff3fc5d8596cb0e74eb87d0392c`:
- Route checks, unit tests, debug APK build and APK verification/upload passed.
- APK artifact ID: `12464190239`.

## Still open / no production approval

Appeal-ticket decision linkage and direct account warning/lift-suspension/profile-status mutations still need their log/notice obligations enforced by Rules. Lifecycle-counter semantics require clarification. Production Rules/index exports, Auth/profile prerequisites, historic proposal `proposerId` migration, separate staging verification and explicit approval remain outstanding.

No Firebase deployment or merge to `main` has occurred.
