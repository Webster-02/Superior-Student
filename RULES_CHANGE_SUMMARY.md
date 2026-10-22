# Rules change summary

The U2U Market Firestore candidate is on `review/market-rules`. It has not been merged to `main` or deployed to Firebase.

## Source comparison

Compared with `feature/pachattar-design-foundation-phase-1` at base commit `759eed423e7046d7fe127226945557a8fe4fa918`:

- Base rules blob: `2944d790949a1fd93414a851eaf1f3ca6fef8922`.
- Current candidate rules blob: `15ae62777f1d9486c3bd15f364b03a82b934b34e`.
- GitHub compare reports **1,758 additions and 3 deletions** in `firestore.rules`.
- `firestore.base.rules` remains a byte-for-byte source-branch snapshot. It does not prove the live Firebase Rules match that baseline.

## Targeted security properties verified by CI

- Self-service Market profile preference changes require verified email.
- Task acceptance requires a new deterministic Market conversation in the same atomic write as assignment, proposal acceptance and poster counter decrement.
- Market config create/update requires a newly created `adminLogs` entry in the same atomic write. Config contains `lastAuditLogId` pointing to the new immutable log; Rules verify the action, target, admin ID and request timestamp.
- Positive and negative tests cover linked config edits, missing/stale/mismatched links, invalid rank weights/limits, and task acceptance with/without the chat.
- Existing U2U friend/chat and Nearby-transfer rules remain separate from Market chat.

## Latest Rules and Android test evidence

[Rules run #27](https://github.com/Webster-02/Superior-Student/actions/runs/39858637212), tested commit `1f5536f4387cb7254e4a0ccae04c896b1a68b511`:
- Static validation: **40/40 passed**.
- Firestore Rules Emulator: **67/67 passed**, 0 failed, 0 skipped.
- Expression-ceiling diagnostics: **0**; guard passed.
- Artifact ID: `12459668968`.
- Tested Rules blob: `15ae62777f1d9486c3bd15f364b03a82b934b34e`.

[Android run #100](https://github.com/Webster-02/Superior-Student/actions/runs/39858373895), app commit `4a74df5e6d7d383f4f38fda716b8eaafed4ebdb0`:
- Unit tests and debug APK build passed.
- APK artifact ID: `12459833836`.

## Still open / no production approval

Config audit linkage is now enforced, but task/offer pin/moderation, report status, support and user warning/suspension still need linked admin logs and required notices enforced by Rules and covered by positive/negative tests. Exact lifecycle-counter semantics need clarification. Production Rules/index exports, Auth/profile prerequisites, historic `proposerId` migration, separate staging verification and explicit approval remain outstanding.

No Firebase deployment or merge to `main` has occurred.
