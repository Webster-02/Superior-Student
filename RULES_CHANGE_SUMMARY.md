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

## Phase 2, Step 13 — Appeal-ticket decision audit linkage

### Rules implementation and verification
- Rules/test commit: `dc64e050e9229fa0f5ab8e3a44f88d01ff1a49b4`.
- Workflow run #34: https://github.com/Webster-02/Superior-Student/actions/runs/39868612662
- Static suite: **44 passed, 0 failed**.
- Firestore Emulator suite: **69 passed, 0 failed**.
- Expression-ceiling diagnostics: **0**; expression-ceiling guard passed.
- Appeal ticket resolution now requires a newly created `approve_appeal` or `reject_appeal` audit log and a new owner `appeal_result` notice. The support ticket's `lastAuditLogId` and `lastAdminNoticeId` pointers must reference those new documents in the same atomic ticket-resolution write. Rules validate the acting admin, action, support-ticket target, action-specific notice title, notice relation, and request timestamps.
- Negative cases cover missing pointers, missing notice, wrong audit target, wrong action, and wrong notice target; positive cases cover approval and rejection.
- Step 12's non-appeal support helpers and profile-counter constraints remain unchanged. Listing restoration still happens in its separate transaction with its own `approve_appeal_restore` audit log and listing notice.

### Android linkage
- Android commit: `83cee57686bc91654a143b1d62c0ce926eb15c23` on `feature/market-ui-reference`.
- The final appeal-resolution transaction writes the generated decision-audit ID to `lastAuditLogId` and the generated `appeal_result` notice ID to `lastAdminNoticeId`, matching the actual log and notice writes.
- Android workflow run #104: https://github.com/Webster-02/Superior-Student/actions/runs/39868829292
- Android CI result: **passed** (run #104, https://github.com/Webster-02/Superior-Student/actions/runs/39868829292). Embedded JavaScript validation, Market route verification, `gradle clean`, `testDebugUnitTest`, debug APK build, APK verification, and artifact upload all passed. APK artifact: `superior-student-debug-apk`, ID `12464259740`.

No Firebase Rules or indexes were deployed; no live Firebase data was changed; neither branch was merged into `main`. No unrelated account warning/lift-suspension/profile-status change was included.

 
## Phase 2, Step 14 — Account moderation audit linkage

- Implementation commit: `d19480430c8aab5aba4585b89b926968dd2d48aa`; short-circuit expression-cost fix: `a40a25847a23453f0b24c0510b282ce2e568ef25`.
- Final [Rules CI run #36](https://github.com/Webster-02/Superior-Student/actions/runs/39880626698) passed: **45 static checks, 71 Emulator tests, zero expression-ceiling diagnostics**.
- Warning increments, suspension/ban profile changes and lift-suspension now require linked new audit records and action-specific notices in the same atomic write.
- [Android run #105](https://github.com/Webster-02/Superior-Student/actions/runs/39880533430) passed unit tests, debug APK build, APK verification and artifact upload. Artifact ID: `12471070183`.
- The Rules/app candidate branches remain separate; no merge to `main`, Firebase deployment or live data change occurred.

### Current remaining gates
Lifecycle-counter specification clarification; production Rules/index export and source-baseline comparison; Auth/profile readiness; historical proposal `proposerId` migration review; isolated staging deployment and end-to-end student/admin smoke tests; explicit owner approval before production.


## Phase 2, Step 15 — Lifecycle-counter contract

- [Rules CI run #37](https://github.com/Webster-02/Superior-Student/actions/runs/39881170691) passed: **46 static checks, 73 Emulator tests, zero expression-ceiling diagnostics**.
- `MARKET_FINAL_SPEC.md` now documents the meaning and transition rules for task, offer, proposal, report, support and warning counters.
- Regression tests prove task acceptance decrements `openTasks` only once and removing a paused offer does not decrement `activeOffers`.
- No Firebase deployment, live-data change or merge to `main` occurred.
