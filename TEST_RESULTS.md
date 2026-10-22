# Test and validation results

## Latest verified CI run

**Workflow:** [Firestore Rules Review Tests — run #22](https://github.com/Webster-02/Superior-Student/actions/runs/39603954521)  
**Tested commit:** 2a136f1cc8478a27b0ef101a7a3c0ec8bde86ad9 on review/market-rules  
**Current rules blob:** 8324f3bde6ee72d051a5635ba44ae73388439e02 — identical to the rules blob tested by run #22. The later commit at the time of this documentation correction changed PROGRESS.md only.

| Check | Actual result |
|---|---|
| npm ci | Exit 0 |
| Static validation | **37 passed, 0 failed** |
| Firestore Rules Emulator | **65 passed, 0 failed, 0 skipped**, across 14 suites |
| 1,000-expression ceiling diagnostics | **0** |
| Expression-ceiling guard | Exit 0 |
| Overall GitHub Actions job | **Success** |
| Firebase deployment / merge to main | Not performed |

Artifact: [run #22 and its artifacts](https://github.com/Webster-02/Superior-Student/actions/runs/39603954521)  
Artifact name: firestore-rules-test-results  
Artifact ID: 12346912626 (185,719 bytes; expiry reported by GitHub: 2027-01-19).

The downloaded artifact was inspected directly. Its summary reports static exit code 0, Emulator exit code 0, expression-ceiling diagnostic count 0, guard exit code 0, and Overall: PASS. The raw Emulator output has zero matches for the exact 1,000-expression-limit error.

## Security and transaction paths covered

The passing Emulator assertions include:

1. Task creation paired atomically with the poster's open-task counter.
2. Proposal creation paired with task proposal count and the proposer's rolling-window counter.
3. Accept transaction updating task, proposal and profile while creating the deterministic task conversation in the same transaction.
4. Report batch paired with a deterministic report document and counter updates.
5. Admin suspend/hide batch with listing/profile changes, suspension notice and admin log, while the admin has neither profiles/{uid} nor marketProfiles/{uid}.
6. Admin moderation/config/support operations without a student profile, and denial of regular student Market writes without profiles/{uid}.
7. Applied collection-group reads constrained by immutable proposerId, denial of cross-user reads, and poster task-level proposal access.
8. Poster-only completion of an assigned task and ratings by both participants with the paired stats update.
9. Existing accepted-friend chat and Nearby-transfer regression coverage, plus admin exclusion from the separate Market chat feature.

These results prove the tested requests behaved as asserted in the Rules Emulator. They do not guarantee every possible request shape is free of every access-call or evaluator-complexity risk; staging verification remains required.

## Historical CI results — do not confuse with run #22

- [Run 39533202614](https://github.com/Webster-02/Superior-Student/actions/runs/39533202614) belongs to an older rules snapshot. Its raw Emulator log contained **39** 1,000-expression-ceiling diagnostics even though the test assertions passed. It is not the current verified baseline.
- [Run #21](https://github.com/Webster-02/Superior-Student/actions/runs/39601819592) failed the static suite (34 passed, 3 failed) while its 65 Emulator assertions passed.
- The subsequent [run #22](https://github.com/Webster-02/Superior-Student/actions/runs/39603954521) restored the verified baseline and passed the current static and Emulator gates with zero expression-ceiling diagnostics.

## Remaining production gates

Passing CI is not authorization to deploy. Before any production change:

- Export the rules and indexes actually deployed in Firebase and compare them to this selected source baseline. Save protected rollback copies and hashes. The Git branch comparison does not prove what is currently live.
- Check email-verification readiness and confirm valid profiles/{uid} documents for Market students.
- Dry-run, back up, apply and verify the historical proposal proposerId migration using a trusted one-off Admin SDK operation. Stop for manual reconciliation if a proposal document ID cannot safely identify its author.
- Resolve the semantic addendum review gaps recorded in PROGRESS.md.
- Deploy to a separate staging Firebase project and test student, admin, query, index and regression paths.
- Obtain explicit product-owner approval before any production deployment.

The original MARKET_SPEC_ADDENDUM_RULES_PHASE.md **is present on the review branch** and is integrity-checked by static validation. Its semantic reconciliation is still open; it should not be described as missing.

No Firebase rules/index deployment or merge to main has occurred.
