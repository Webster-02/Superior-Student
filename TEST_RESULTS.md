# Test and validation results

## Latest verified Rules CI

**Workflow:** [Firestore Rules Review Tests — run #27](https://github.com/Webster-02/Superior-Student/actions/runs/39858637212)  
**Rules-tested commit:** `1f5536f4387cb7254e4a0ccae04c896b1a68b511` on `review/market-rules`  
**Rules blob:** `15ae62777f1d9486c3bd15f364b03a82b934b34e`.

| Check | Actual result |
|---|---|
| `npm ci` | Exit 0 |
| Static validation | **40 passed, 0 failed** |
| Firestore Rules Emulator | **67 passed, 0 failed, 0 skipped**, across 14 suites |
| 1,000-expression-ceiling diagnostics | **0** |
| Expression-ceiling guard | Exit 0 |
| Overall Rules workflow | **Success** |
| Firebase deployment / merge to `main` | Not performed |

Artifact: `firestore-rules-test-results`, ID `12459668968` (185,402 bytes). The downloaded raw artifact was inspected directly; its summary reports both suites exit 0, diagnostic count 0 and `Overall: PASS`.

## Latest verified Android CI

**Workflow:** [Market Data Step Check — run #100](https://github.com/Webster-02/Superior-Student/actions/runs/39858373895)  
**App commit:** [`4a74df5e6d7d383f4f38fda716b8eaafed4ebdb0`](https://github.com/Webster-02/Superior-Student/commit/4a74df5e6d7d383f4f38fda716b8eaafed4ebdb0) on `feature/market-ui-reference`.  
**Result:** Success. Unit tests and debug APK build completed. Artifact `superior-student-debug-apk`, ID `12459833836`.

The Rules/app commits live on separate branches and are not merged to `main`.

## Security changes currently verified

1. Self-service `marketProfiles/{uid}` preference changes require a verified email.
2. Task acceptance requires a selected accepted proposal, poster open-task counter decrement, and a brand-new deterministic Market conversation created in the same transaction. A negative test without the conversation is denied.
3. Market config creation/update requires `lastAuditLogId` to point to a newly created immutable `adminLogs` entry in the same write. The action and target must match the config change, and the log identity/timestamp must match the current admin/request.
4. Valid config updates through the transaction still pass; standalone config updates and mismatched/stale audit pointers are denied.
5. Existing test coverage for task/offer lifecycle, proposals, reports/counters, notices/support/config permissions, ratings, legacy U2U chat and Nearby-transfer rules remains green in the tested Emulator suite.

## Recent Rules CI history

- [Run #27](https://github.com/Webster-02/Superior-Student/actions/runs/39858637212): current Rules candidate; static 40/40, Emulator 67/67, zero expression-ceiling diagnostics.
- [Run #26](https://github.com/Webster-02/Superior-Student/actions/runs/39858262063) and [run #25](https://github.com/Webster-02/Superior-Student/actions/runs/39858214859): temporary failures in the newly added mismatched-pointer regression test because its input accidentally matched the created log ID. The test was corrected; run #27 passes.
- [Run #24](https://github.com/Webster-02/Superior-Student/actions/runs/39856525223): task acceptance/chat guard and email verification; static 39/39, Emulator 67/67.
- [Run #23](https://github.com/Webster-02/Superior-Student/actions/runs/39855566823): email-verification guard; static 38/38, Emulator 66/66.
- [Run #22](https://github.com/Webster-02/Superior-Student/actions/runs/39603954521): restored baseline; static 37/37, Emulator 65/65.
- Older run [39533202614](https://github.com/Webster-02/Superior-Student/actions/runs/39533202614) used an older rules snapshot and recorded 39 expression-ceiling diagnostics. It is historical; current run #27 has zero diagnostics.

## Remaining production gates

Passing CI does **not** authorize production deployment. Before any production change:

- Export actually published production Rules and indexes, compare with the selected source baseline, and save protected rollback copies and hashes. Git comparison does not prove the live project matches.
- Verify email-verification readiness and required `profiles/{uid}` records against current Firebase Auth/data.
- Dry-run, back up, apply and verify any historic proposal `proposerId` migration with an authorized one-off Admin SDK operation; manually reconcile mismatches rather than guessing.
- Finish audit-log/notice linkage for task/offer moderation and pin, report status, support, and account actions; clarify exact lifecycle-counter transitions.
- Deploy to a separate staging Firebase project and validate actual student/admin/query/index flows.
- Obtain explicit product-owner approval before any production deployment.

The original `MARKET_SPEC_ADDENDUM_RULES_PHASE.md` remains byte-identical to its integrity-checked reference. Its production-gate review remains open.

No Firebase Rules or indexes have been deployed and no merge to `main` has occurred.
