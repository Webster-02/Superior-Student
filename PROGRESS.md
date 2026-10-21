# Market Rules Review Progress

**Branch:** `review/market-rules`  
**State:** Step Zero baseline verified; **P1 and P2 candidates implemented and untested** — review-only. No merge to `main` and no Firebase deployment. CI is intentionally not run for the current P2 change.

## Step Zero verification record

- **Validated commit:** `6249b60165cbe96d2aa2102e22d22ac99469c1c1`
- **GitHub Actions run:** [#20 — Align static checks with corrected expression guard](https://github.com/Webster-02/Superior-Student/actions/runs/39583502817) — **success**
- **Static suite:** 37 passed, 0 failed.
- **Firestore Emulator suite:** 65 passed, 0 failed.
- **Expression-ceiling diagnostics in raw Emulator output:** 0.
- **Guard result:** diagnostic count 0; guard exit code 0; final summary `Overall: PASS`.
- **Rules baseline:** `firestore.rules` was restored byte-for-byte to the last known passing Rules blob (`8324f3bde6ee72d051a5635ba44ae73388439e02`, from commit `6893af1a273f17134e72f49e9ecac9f24e0ae999`). The untested rule refactor present in WIP commit `b7a7b39` was not retained: run #18 reproduced 39 expression-ceiling diagnostics and nine static-check failures. Do not reapply that experiment without a separately reviewed change and a fresh CI run.
- **P1 — Tasks + offers (current candidate):** reduced repeated field access in create validators, cached status/owner/assignment/timestamp fields in owner-update validators, retained the single `affectedKeys()` diff per update dispatcher, cached pre-update report counters/moderation, and combined offer reactivation limit/counter checks around one cached profile before/after snapshot. Cheap create payload checks now run before timestamp checks. Existing allowlists, ownership checks, status transitions, and counter predicates remain in place. **Not tested in CI by instruction.**
- **P2 — Reports (current candidate):** cached report-create profile mutation and report post-state snapshots; consolidated report existence validation with the task/offer report-counter validators while retaining explicit `existsAfter`, target identity, reporter identity, report-count, moderation-threshold, and profile-counter checks. The admin report update checks the status allowlist before the field diff. **Not tested in CI by instruction.**
- **P3 — Profile + proposals:** current restored Rules baseline passed the complete Emulator suite with zero expression-ceiling diagnostics. The untested follow-up refactor was backed out.
- **P4 — CI diagnostic guard:** corrected. The guard counts the exact diagnostic lines, records the count and exit status in the final summary, and makes any nonzero diagnostic count fail the job. Static validation covers the guard.
- **P5 — Run CI:** Step Zero baseline run #20 completed successfully. **No CI run for the P1/P2 candidates**, as requested.
- **P6 — Original addendum:** added as `MARKET_SPEC_ADDENDUM_RULES_PHASE.md` from the supplied Library file without editing its bytes. Source SHA-256: `ff60be0137604b27f93fe8f12ee3e5f0dcbcf7a38e250b3351bdea2a1da4c899`; Git blob SHA: `7cec066773fb5f40cdcf4e921e09ab9c92119800`. The static suite verifies the SHA-256 at test time.
- **Deployment safety:** `DEPLOY_CHECKLIST.md` remains unchanged (blob SHA `ba9dd664d3348ef3d25a1c7e1717ee9519110b93`). No merge to `main`, production export overwrite, Firebase deployment, or staging deployment occurred. Production remains blocked pending the checklist's live rules/index baseline comparison, legacy `proposerId` backfill, staging verification, and explicit approval.

## Current P1/P2 scope and verification status

P1 changed task/offer create and update helpers. P2 changed report-create/profile-counter validation, task/offer report-counter validation used by report updates, and the admin report-status update rule. No test files, CI workflow, unrelated profile/proposal rules, deployment checklist, indexes, or Firebase project were changed. The current P1/P2 candidate has not been run through CI or Emulator; the previous Step Zero CI result applies to the earlier Rules snapshot only.

The mapping and cost patterns below are retained as historical diagnostic documentation for the earlier run; they are not a claim that the discarded experimental refactor is present in the current Rules file.

## Historical mapping of 39 diagnostics in run 39533202614 (pre-refactor snapshot)

The Emulator log's numbered line below is the line in the downloaded raw job log (not a `firestore.rules` line). Rule locations below are from the rules file used by that run. Repeated occurrences are listed individually so none is hidden.

| # | Raw log line | Rule operation(s) referenced by the diagnostic |
|---:|---:|---|
| 1 | 269 | `marketProfiles/{uid}` create at rules L678; update at L683 |
| 2 | 276 | `marketProfiles/{uid}` update at L683 |
| 3 | 278 | `marketProfiles/{uid}` update at L683 |
| 4 | 303 | `tasks/{taskId}` create at L747; update at L748 |
| 5 | 311 | `tasks/{taskId}` create at L747; update at L748 |
| 6 | 340 | `tasks/{taskId}` update at L748 |
| 7 | 342 | `tasks/{taskId}` update at L748 |
| 8 | 344 | `tasks/{taskId}` update at L748 (diagnostic also included a `getAfter` service-call error) |
| 9 | 368 | `tasks/{taskId}` update at L748 |
| 10 | 382 | `tasks/{taskId}` update at L748 |
| 11 | 384 | `tasks/{taskId}` update at L748 |
| 12 | 386 | `tasks/{taskId}` update at L748 |
| 13 | 388 | `tasks/{taskId}` update at L748 |
| 14 | 390 | `tasks/{taskId}` update at L748 |
| 15 | 392 | `tasks/{taskId}` update at L748 |
| 16 | 394 | `tasks/{taskId}` update at L748 |
| 17 | 396 | `tasks/{taskId}` update at L748 |
| 18 | 414 | `offers/{offerId}` create at L972; update at L973 |
| 19 | 429 | `offers/{offerId}` update at L973 |
| 20 | 431 | `offers/{offerId}` update at L973 |
| 21 | 433 | `offers/{offerId}` update at L973 (diagnostic also included a `getAfter` service-call error) |
| 22 | 459 | `offers/{offerId}` update at L973 |
| 23 | 461 | `offers/{offerId}` update at L973 |
| 24 | 463 | `offers/{offerId}` update at L973 |
| 25 | 465 | `offers/{offerId}` update at L973 |
| 26 | 467 | `offers/{offerId}` update at L973 |
| 27 | 469 | `offers/{offerId}` update at L973 |
| 28 | 490 | Proposal create/update are mentioned at L872/L895; the ceiling diagnostic also references task update L748 and profile update L683 |
| 29 | 516 | Proposal create/update at L872/L895; task update L748 and profile update L683 also referenced |
| 30 | 546 | `tasks/{taskId}` update at L748 (report-count batch path) |
| 31 | 674 | Proposal create/update at L872/L895; task update L748 and profile update L683 also referenced |
| 32 | 698 | `offers/{offerId}` update at L973 |
| 33 | 813 | Report create/update at L1084/L1108 are mentioned; ceiling diagnostic also references task update L748 and profile update L683 |
| 34 | 820 | `tasks/{taskId}` update at L748 |
| 35 | 840 | `tasks/{taskId}` update at L748 |
| 36 | 848 | `marketProfiles/{uid}` create at L678; update at L683 |
| 37 | 850 | `tasks/{taskId}` create at L747; update at L748; `marketProfiles/{uid}` update at L683 |
| 38 | 852 | `offers/{offerId}` create at L972; update at L973; `marketProfiles/{uid}` update at L683 |
| 39 | 854 | Proposal create/update at L872/L895; task update L748 and `marketProfiles/{uid}` update L683 |

Operation-to-path key:
- `marketProfiles/{uid}`: create L678, update L683.
- `tasks/{taskId}`: create L747, update L748.
- `tasks/{taskId}/proposals/{userId}`: create L872, update L895.
- `offers/{offerId}`: create L972, update L973.
- `reports/{reportId}`: create L1084, update L1108.

## Historical repeated-cost hypotheses (not retained as completed fixes)

These patterns were recorded from the earlier Rules snapshot that produced the 39 diagnostics above. A follow-up optimization experiment was later rejected and reverted after CI reproduced the expression ceiling. Treat these as investigation leads only, not changes in the current branch.

1. Task and offer owner-update rules repeatedly evaluated `request.resource.data.diff(resource.data).affectedKeys()`. A proposed refactor cached the affected-key set once; that untested follow-up is not retained.
2. Task/offer report-counter validators repeatedly read the same post-batch task/offer and report documents using `getAfter()`. Caching those maps remains a possible future optimization, not a proven current reduction.
3. Profile counter mutation validators repeatedly read the same target documents, especially with `getAfter()`, and dispatch through long operation OR chains. A proposed target-read cache and changed-counter-field prefilter were not retained after the failed experiment.
4. Proposal creation repeatedly reads task/proposal/profile-linked state while validating payload fields. Moving payload checks ahead of linked-state validation and caching task/profile maps was a hypothesis, not a verified fix.
5. Posting eligibility originally read Market profile status before auth and document-existence checks. Guard ordering may be reviewed in a separate change, with the Emulator suite and raw diagnostic guard run after each candidate.
