# Rules change summary

The U2U Market Firestore candidate is on `review/market-rules`. It has not been merged to `main` or deployed to Firebase.

## Current source comparison

Compared with `feature/pachattar-design-foundation-phase-1` at base commit `759eed423e7046d7fe127226945557a8fe4fa918`:

- Base rules blob: `2944d790949a1fd93414a851eaf1f3ca6fef8922`.
- Current candidate rules blob: `b039f9f896c27314eb1f57594e0c1fe911be92a6`.
- GitHub compare reports **1,743 additions and 3 deletions** in `firestore.rules`; the deletions are blank-line formatting only and no existing U2U rule expression was deleted.
- `firestore.base.rules` remains a byte-for-byte copy of the selected source-branch baseline. This does not prove the live Firebase rules match that baseline.

## Current targeted security properties

- Self-service `marketProfiles/{uid}` changes require verified email.
- Task acceptance requires the selected proposal to be accepted, the poster open-task counter to decrement and a new deterministic Market conversation to be created in the same transaction.
- The Market conversation create rule validates the conversation shape, participants and task post-state. The task acceptance rule requires `existsAfter(conversationPath)` and `!exists(conversationPath)`.
- Emulator coverage contains both a successful acceptance transaction including the conversation and a failing transaction that omits it.
- Applied collection-group reads are scoped by immutable `proposerId == request.auth.uid`; the assigned-to-completed Market task transition is poster-only.
- Existing U2U friend/chat and Nearby-transfer rules remain separate from Market chat.

## Latest CI evidence

[Run #24](https://github.com/Webster-02/Superior-Student/actions/runs/39856525223), tested commit `46ce86ce325135814963fb5e077b6257694731cb`:
- Static validation: **39/39 passed**.
- Firestore Rules Emulator: **67/67 passed**, 0 failed and 0 skipped.
- Expression-ceiling diagnostics: **0**; guard passed.
- Artifact ID: `12459119458`.

## Outstanding production gates

Admin audit-log/notice linkage and precise lifecycle-counter semantics still need product/policy resolution and regression coverage. Live production Rules/indexes have not been exported or compared. Existing Auth/profile prerequisites and historical proposal `proposerId` values have not been audited/migrated. Separate staging deployment and explicit approval remain required.

No Firebase deployment or merge to `main` has occurred.
