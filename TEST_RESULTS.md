# Test and validation results

## Latest actual CI run

**Workflow:** [Firestore Rules Review Tests — run 39533202614](https://github.com/Webster-02/Superior-Student/actions/runs/39533202614)  
**Tested commit:** `d5490826a6ee55b5d5861382068fb9571e769227` on `review/market-rules`  
**Workflow conclusion:** success.

| Step | Result |
|---|---|
| `npm ci --no-audit --no-fund` | Exit 0; 781 packages installed |
| `npm run test:static` | Exit 0; **31 passed, 0 failed** |
| `npm run test:emulator` | Exit 0; **65 passed, 0 failed, 0 skipped** across 14 suites |
| GitHub Actions job | Success |
| Firebase deployment / merge to `main` | Not performed |

Artifact: [firestore-rules-test-results.zip](https://github.com/Webster-02/Superior-Student/actions/runs/39533202614/artifacts/12315439964)  
Artifact SHA-256: `f459f2730e9bb09e3ae311859c33ce90b17a5477bc01135ef9fcf0429a635060`.

## Selected actual output from the latest run

```text
added 781 packages in 8s
INSTALL_EXIT_CODE=0
```

Static suite: 31 PASS checks, 0 failures.

```text
# tests 65
# suites 14
# pass 65
# fail 0
# skipped 0
```

Workflow summary:

```text
test:static exit code: 0
test:emulator exit code: 0
Overall: PASS
Artifact firestore-rules-test-results.zip successfully finalized. Artifact ID 12315439964
```

The linked artifact retains the raw npm install, static, emulator, and summary logs.

## Security and transaction paths covered

All relevant Emulator assertions passed, including:
1. Task creation paired atomically with the poster's `openTasks` counter.
2. Proposal creation paired with the task's proposal count and the proposer's rolling-window counter.
3. Accept transaction updating task/proposal/profile and creating the deterministic task conversation in the same transaction.
4. Report batch paired with a deterministic report document and counter updates.
5. Admin suspend/hide batch with listing/profile changes, suspension notice and immutable admin log; the admin user intentionally lacks both `profiles/{uid}` and `marketProfiles/{uid}`.
6. Admin moderation/config/support operations without a student profile, and denial of regular student Market writes without `profiles/{uid}`.
7. Applied collection-group reads constrained by immutable `proposerId`; no cross-user read; poster's task-level proposal access retained.
8. Poster can complete an assigned task; the assigned helper cannot. Both participants can rate a completed task with the paired stats update.
9. Regular accepted-friend chat and Nearby-transfer regression tests, plus admin exclusion from the separate Market chat feature.

These passing assertions show the tested operations were accepted/denied as expected by the Firestore Rules Emulator. The tests do not print an exact numeric count of every internal `get/exists` call; results should not be generalized beyond the tested shapes.

## Important diagnostic: evaluator expression ceiling

The actual latest Emulator log contains **39 lines** containing the message that Rules evaluation reached the 1,000-expression ceiling. These diagnostics did not make any test assertion fail, so the job concluded green, but they are not ignorable and this is not a warning-free log. They are distinct from Firestore's document access-call limits (10 for an individual operation, up to 20 across a batch/transaction). Review and simplify the affected branches before production approval; successful tests alone do not prove every possible request shape is free of evaluator-complexity risk.

## Earlier CI history

- [Run 39531687165](https://github.com/Webster-02/Superior-Student/actions/runs/39531687165): first committed attempt, 56/65 Emulator assertions passed and 9 failed. It exposed two denial-test fixtures that did not actually modify protected fields, an invalid public-key fixture for the legacy Nearby test, and expression-evaluation complexity.
- [Run 39532613999](https://github.com/Webster-02/Superior-Student/actions/runs/39532613999): static 31/31 and Emulator 65/65 passed.
- [Run 39532954514](https://github.com/Webster-02/Superior-Student/actions/runs/39532954514): ran using the committed lockfile with `npm ci`; both suites passed.
- [Run 39533202614](https://github.com/Webster-02/Superior-Student/actions/runs/39533202614): final rules commit with baseline trailing newline preserved; `npm ci`, static and Emulator suites passed.

## Still required before production

- Compare live Firebase Console Rules and indexes against the branch baseline and save live exports as rollback artifacts. The committed snapshot proves only what the selected source branch contained.
- Verify existing users' email-verification status in Firebase Authentication.
- Backfill historical proposal documents with immutable `proposerId` using a trusted one-off Admin SDK migration (not client rules and not a deployed Cloud Function).
- Attach the original `MARKET_SPEC_ADDENDUM_RULES_PHASE.md`. It is missing from the accessible attachments and source branch, so it has not been fabricated.
- Exercise the rules and indexes on a separate staging Firebase project and explicitly review the expression-ceiling diagnostics.
- No Firebase deployment or merge to `main` has happened.
