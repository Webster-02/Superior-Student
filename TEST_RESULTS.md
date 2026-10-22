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
