# Test and validation results

## Latest verified Rules CI

**Workflow:** [Firestore Rules Review Tests — run #32](https://github.com/Webster-02/Superior-Student/actions/runs/39865131476)  
**Rules-tested commit:** `d0428fb7b6d6e2414a2d199d954564527193df78` on `review/market-rules`  
**Rules blob:** `4dd84e3cfcd9d2e91d1327b0ffd24e0ad2efbeb1`.

| Check | Actual result |
|---|---|
| `npm ci` | Exit 0 |
| Static validation | **43 passed, 0 failed** |
| Firestore Rules Emulator | **68 passed, 0 failed, 0 skipped**, across 14 suites |
| 1,000-expression-ceiling diagnostics | **0** |
| Expression-ceiling guard | Exit 0 |
| Overall Rules workflow | **Success** |
| Firebase deployment / merge to `main` | Not performed |

Artifact: `firestore-rules-test-results`, ID `12462414890` (186,118 bytes). The workflow log confirms both suites exited 0, diagnostic count 0 and `Overall: PASS`.

## Latest verified Android CI

**Workflow:** [Market Data Step Check — run #103](https://github.com/Webster-02/Superior-Student/actions/runs/39864585562)  
**App commit:** [`d1d95c4d3c4e9ff3fc5d8596cb0e74eb87d0392c`](https://github.com/Webster-02/Superior-Student/commit/d1d95c4d3c4e9ff3fc5d8596cb0e74eb87d0392c) on `feature/market-ui-reference`.  
**Result:** Success. Embedded JavaScript validation, Market route checks, unit tests, debug APK build, APK verification and upload all passed. Artifact `superior-student-debug-apk`, ID `12464190239` (41,114,004 bytes).

## Security properties verified in the current Rules candidate

1. Self-service `marketProfiles/{uid}` preference changes require verified email.
2. Task acceptance requires the proposal, counter decrement and new deterministic Market conversation in the same atomic write.
3. Market configuration edits require a new linked immutable `adminLogs` record in the same atomic write.
4. Task/offer pin and moderation updates require a matching new immutable `adminLogs` record and a new owner notice in the same atomic write.
5. Report-status changes must atomically write a new `set_report_status` audit log and a new reporter notice linked to the correct report.
6. **Non-appeal admin support-ticket replies and resolutions** require a new linked audit log and a new notice for the ticket owner in the same atomic write. Owner-authored replies retain their existing restricted write path.
7. The aggregate suspend/ban flow remains allowed only when hidden task/offer listings link to the new aggregate log and removal notice and the target profile is suspended/banned in the same write.
8. Tests reject missing notices, missing audit pointers and mismatched targets. Existing task acceptance/chat and verified-email checks remain green, and the legacy U2U chat and Nearby-transfer rules remain separate from Market paths.

## Recent Rules CI history

- [Run #32](https://github.com/Webster-02/Superior-Student/actions/runs/39865131476): support reply/resolve audit linkage after the owner/admin update dispatcher refactor; static 43/43, Emulator 68/68, zero expression-ceiling diagnostics.
- [Run #31](https://github.com/Webster-02/Superior-Student/actions/runs/39864691796): intermediate support rules passed all 68 Emulator tests but emitted four expression-ceiling diagnostics; the update dispatcher was refactored and run #32 is clean.
- [Run #30](https://github.com/Webster-02/Superior-Student/actions/runs/39862784731): report-status audit/notice linkage; static 42/42, Emulator 67/67, zero expression-ceiling diagnostics.
- [Run #29](https://github.com/Webster-02/Superior-Student/actions/runs/39860003494): static 41/41, Emulator 67/67, zero expression-ceiling diagnostics.
- [Run #28](https://github.com/Webster-02/Superior-Student/actions/runs/39859803552): a negative-test fixture initially mixed references from different Firestore instances; fixture corrected and run #29 passed.
- [Run #27](https://github.com/Webster-02/Superior-Student/actions/runs/39858637212): Market config audit linkage; static 40/40, Emulator 67/67.
- [Run #24](https://github.com/Webster-02/Superior-Student/actions/runs/39856525223): task acceptance/chat guard; static 39/39, Emulator 67/67.
- [Run #23](https://github.com/Webster-02/Superior-Student/actions/runs/39855566823): verified-email guard; static 38/38, Emulator 66/66.
- [Run #22](https://github.com/Webster-02/Superior-Student/actions/runs/39603954521): restored baseline; static 37/37, Emulator 65/65.
- Older run [39533202614](https://github.com/Webster-02/Superior-Student/actions/runs/39533202614) used an older Rules snapshot with expression-ceiling diagnostics; run #32 has zero.

## Remaining production gates

Passing CI does **not** authorize production deployment. Before any production change:

- Export live production Rules and indexes, compare them to the selected source baseline, and save protected rollback copies/hashes. Git comparison does not prove live project identity.
- Verify email-verification readiness and required `profiles/{uid}` documents against current Auth/data.
- Dry-run, back up, apply and verify any historic proposal `proposerId` migration using an authorized one-off Admin SDK operation; reconcile mismatches rather than guessing.
- Complete the separate appeal-ticket decision linkage review, and add Rules-enforced log/notice linkage for direct account warning/lift-suspension/profile-status actions.
- Clarify exact lifecycle-counter transitions.
- Deploy to a separate staging Firebase project and validate actual student/admin/query/index flows.
- Obtain explicit product-owner approval before production deployment.

The original `MARKET_SPEC_ADDENDUM_RULES_PHASE.md` remains byte-identical to its integrity-checked reference. Its production readiness review remains open.

No Firebase Rules or indexes have been deployed and no merge to `main` has occurred.

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

**Rules implementation:** `d19480430c8aab5aba4585b89b926968dd2d48aa`, with short-circuit fix `a40a25847a23453f0b24c0510b282ce2e568ef25`.  
**Rules CI run #36:** https://github.com/Webster-02/Superior-Student/actions/runs/39880626698 — **success**.  
**Android commit:** `de591fdc6d987cf614a7a0011d2fc1490b295160`.  
**Android CI run #105:** https://github.com/Webster-02/Superior-Student/actions/runs/39880533430 — **success**. APK artifact `superior-student-debug-apk`, ID `12471070183`.

- Static validation: **45/45 passed**.
- Firestore Emulator: **71/71 passed**, 0 skipped.
- Expression-ceiling diagnostics: **0**.
- The profile audit pointers now bind account warning, suspension/ban, and lift-suspension updates to their newly created audit logs and notices in the same atomic write.
- Warnings must increase by exactly one; suspension/ban require the expected resulting status, zero open listing counters, matching user mutation marker, and action-specific notice; lift-suspension can only transition a suspended account to active with its linked audit and activity notice.
- Negative and positive tests cover missing pointers, wrong audit target, valid warning, lift without links, valid lift, and bulk suspension linkage.
- Android unit tests and debug APK build/verification/upload passed. The profile decoder accepts optional bounded audit pointer fields while keeping them out of the student-facing model.

## Current production gates (updated after Step 14)

Rules linkage for config, task/offer moderation, report status, ordinary support, appeals, and account moderation is now covered by Emulator-tested same-write audit/notice requirements. Remaining gates are lifecycle-counter specification review; export/hash/compare of currently published production Rules/indexes; Auth and profile readiness checks; historic proposal `proposerId` dry-run/backup/mismatch reconciliation; separate staging deployment and end-to-end smoke tests; then explicit owner approval for production. No Firebase deployment or live data change has occurred.


## Phase 2, Step 15 — Lifecycle-counter contract

[Rules CI run #37](https://github.com/Webster-02/Superior-Student/actions/runs/39881170691), tested commit `b37032cf3198723fe58d25d4bc84302677161a0f`:
- Static validation: **46/46 passed**.
- Firestore Rules Emulator: **73/73 passed**, 0 skipped.
- Expression-ceiling diagnostics: **0**.
- Artifact: `firestore-rules-test-results`, ID `12471180011`.

The counter contract is now documented in `MARKET_FINAL_SPEC.md`. New tests cover no second `openTasks` decrement after task acceptance and no `activeOffers` decrement on paused-offer removal. The production and staging deployment gates remain open; these are candidate review results only.


## Staging instructions revalidated — Rules run #38

[Run #38](https://github.com/Webster-02/Superior-Student/actions/runs/39881528899) for staging checklist commit `d00cc23a02b192b2f414de88ce27dae453b5ff03` passed **46 static checks, 73 Emulator tests, and zero expression-ceiling diagnostics**. Artifact `12470767412`. The checked-in app config uses project ID `pachattarpercent`; the phone smoke test must use a separately created staging project and staging-specific Google Services config.
