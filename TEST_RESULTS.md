# Test and validation results

## Latest verified Rules CI

**Workflow:** [Firestore Rules Review Tests — run #29](https://github.com/Webster-02/Superior-Student/actions/runs/39860003494)  
**Rules-tested commit:** `181e347c14032a8b8ddfd5a0c314afe17111a016` on `review/market-rules`  
**Rules blob:** `3bd08c780ebdb2eef69033260d6a86f4ceee4c2c`.

| Check | Actual result |
|---|---|
| `npm ci` | Exit 0 |
| Static validation | **41 passed, 0 failed** |
| Firestore Rules Emulator | **67 passed, 0 failed, 0 skipped**, across 14 suites |
| 1,000-expression-ceiling diagnostics | **0** |
| Expression-ceiling guard | Exit 0 |
| Overall Rules workflow | **Success** |
| Firebase deployment / merge to `main` | Not performed |

Artifact: `firestore-rules-test-results`, ID `12461586838` (185,843 bytes). Its downloaded raw summary reports both suites exit 0, diagnostic count 0 and `Overall: PASS`.

## Latest verified Android CI

**Workflow:** [Market Data Step Check — run #101](https://github.com/Webster-02/Superior-Student/actions/runs/39859898773)  
**App commit:** [`a8b8e55d0b3cfec5f19323b31091572282c18d00`](https://github.com/Webster-02/Superior-Student/commit/a8b8e55d0b3cfec5f19323b31091572282c18d00) on `feature/market-ui-reference`.  
**Result:** Success. Unit tests, route checks and debug APK build passed; the APK verification/upload steps also passed. Artifact `superior-student-debug-apk`, ID `12461825353`.

The Rules and app changes reside on different branches and have not been merged to `main`.

## Security properties verified in the current Rules candidate

1. Self-service `marketProfiles/{uid}` preference changes require verified email.
2. Task acceptance requires the proposal, counter decrement and new deterministic Market conversation in the same atomic write.
3. Market configuration edits require a new linked immutable `adminLogs` record in the same atomic write.
4. Task/offer pin and moderation updates require a matching new immutable `adminLogs` record and a new owner notice in the same atomic write. The log action/target and notice listing relation are tied to the affected listing and current admin.
5. The aggregate suspend/ban flow remains allowed only when the hidden task/offer listings link to the new aggregate log and removal notice and the target profile is suspended/banned in the same write.
6. Regression tests deny missing audit pointers, missing notices and mismatched audit target type. Valid task/offer hide/restore, pin/unpin, configuration and suspension transactions pass.
7. The previous task acceptance/chat and verified-email test coverage remains green; existing U2U chat and Nearby-transfer rules are kept separate from Market paths.

## Recent Rules CI history

- [Run #29](https://github.com/Webster-02/Superior-Student/actions/runs/39860003494): current candidate; static 41/41, Emulator 67/67, zero expression-ceiling diagnostics.
- [Run #28](https://github.com/Webster-02/Superior-Student/actions/runs/39859803552): a negative-test fixture initially mixed references from different Firestore instances; fixture corrected and run #29 passed.
- [Run #27](https://github.com/Webster-02/Superior-Student/actions/runs/39858637212): Market config audit linkage; static 40/40, Emulator 67/67.
- [Run #24](https://github.com/Webster-02/Superior-Student/actions/runs/39856525223): task acceptance/chat guard; static 39/39, Emulator 67/67.
- [Run #23](https://github.com/Webster-02/Superior-Student/actions/runs/39855566823): verified-email guard; static 38/38, Emulator 66/66.
- [Run #22](https://github.com/Webster-02/Superior-Student/actions/runs/39603954521): restored baseline; static 37/37, Emulator 65/65.
- Older run [39533202614](https://github.com/Webster-02/Superior-Student/actions/runs/39533202614) is historical and used an older Rules snapshot with expression-ceiling diagnostics; run #29 has zero.

## Remaining production gates

Passing CI does **not** authorize production deployment. Before any production change:

- Export live production Rules and indexes, compare them to the selected source baseline, and save protected rollback copies/hashes. Git comparison does not prove live project identity.
- Verify email-verification readiness and required `profiles/{uid}` documents against current Auth/data.
- Dry-run, back up, apply and verify any historic proposal `proposerId` migration using an authorized one-off Admin SDK operation; reconcile mismatches rather than guessing.
- Add Rules-enforced log/notice linkage for report status, support reply/resolve, and direct account warning/lift-suspension/profile-status actions.
- Clarify exact lifecycle-counter transitions.
- Deploy to a separate staging Firebase project and validate actual student/admin/query/index flows.
- Obtain explicit product-owner approval before production deployment.

The original `MARKET_SPEC_ADDENDUM_RULES_PHASE.md` remains byte-identical to its integrity-checked reference. Its production readiness review remains open.

No Firebase Rules or indexes have been deployed and no merge to `main` has occurred.
