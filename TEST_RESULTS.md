# Test and validation results

## Latest verified CI run

**Workflow:** [Firestore Rules Review Tests — run #24](https://github.com/Webster-02/Superior-Student/actions/runs/39856525223)  
**Tested commit:** `46ce86ce325135814963fb5e077b6257694731cb` on `review/market-rules`  
**Current rules blob:** `b039f9f896c27314eb1f57594e0c1fe911be92a6`.

| Check | Actual result |
|---|---|
| `npm ci` | Exit 0 |
| Static validation | **39 passed, 0 failed** |
| Firestore Rules Emulator | **67 passed, 0 failed, 0 skipped**, across 14 suites |
| 1,000-expression-ceiling diagnostics | **0** |
| Expression-ceiling guard | Exit 0 |
| Overall GitHub Actions job | **Success** |
| Firebase deployment / merge to `main` | Not performed |

Artifact: [run #24](https://github.com/Webster-02/Superior-Student/actions/runs/39856525223)  
Artifact name: `firestore-rules-test-results`; ID `12459119458` (185,653 bytes).

The downloaded raw artifact was inspected. Its summary reports static exit code 0, Emulator exit code 0, expression-ceiling diagnostic count 0, guard exit code 0, and `Overall: PASS`.

## Security changes verified by run #24

1. Self-service `marketProfiles/{uid}` preference changes require a verified email. The regression test denies an unverified student's preference update and accepts the same update for a verified student.
2. Task acceptance must atomically change the task to assigned, mark the selected proposal accepted, decrement the poster's open-task counter, and create the new deterministic Market conversation. The rules require the conversation to exist after the transaction and not exist before it.
3. Positive acceptance transaction including the new conversation passes.
4. Negative acceptance transaction omitting the new conversation is denied.
5. Existing test coverage for task/offer lifecycle and create operations, proposals, reports/counters, admin operations, ratings, legacy U2U chat, and Nearby-transfer rules remains green.

The emulator results cover the tested request shapes and don't guarantee every possible future request shape. Staging verification remains necessary.

## Recent CI history

- [Run #24](https://github.com/Webster-02/Superior-Student/actions/runs/39856525223): current rules candidate; static 39/39, Emulator 67/67, zero expression-ceiling diagnostics.
- [Run #23](https://github.com/Webster-02/Superior-Student/actions/runs/39855566823): verified-email guard; static 38/38, Emulator 66/66, zero expression-ceiling diagnostics.
- [Run #22](https://github.com/Webster-02/Superior-Student/actions/runs/39603954521): restored baseline; static 37/37, Emulator 65/65, zero expression-ceiling diagnostics.
- Older run [39533202614](https://github.com/Webster-02/Superior-Student/actions/runs/39533202614) used an older rules snapshot and recorded 39 expression-ceiling diagnostics. It is historical and is not the current test result.

## Remaining production gates

Passing CI does **not** authorize production deployment. Before any production change:

- Export actually published production Rules and indexes, compare with this selected source baseline, and save protected rollback copies and hashes. Git comparison does not prove the live project matches.
- Verify email-verification readiness and required `profiles/{uid}` records against current Firebase Auth/data.
- Dry-run, back up, apply, and verify any historical proposal `proposerId` migration using an authorized one-off Admin SDK operation; manually reconcile mismatches rather than guessing.
- Resolve remaining specification gaps, including admin audit-log/notice atomicity and precise lifecycle-counter requirements.
- Deploy to a separate staging Firebase project and validate the actual student/admin/query/index flows.
- Obtain explicit product-owner approval before any production deployment.

The original `MARKET_SPEC_ADDENDUM_RULES_PHASE.md` is present on the review branch and integrity-checked by static validation. Its semantic gap review remains open; its historical expression-ceiling note should not be confused with run #24.

No Firebase Rules or indexes have been deployed and no merge to `main` has occurred.
