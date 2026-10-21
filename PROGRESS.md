# Market Rules Review Progress

**Branch:** `review/market-rules`  
**State:** WIP only. Candidate rules changes below are untested; CI was not run for this checkpoint. No merge to `main` and no Firebase deployment.

## P1–P6 status

- **P1 — Tasks + offers rules:** WIP candidate prepared; owner-update `affectedKeys()` results are cached, create validators check cheap payload properties before linked reads, and repeated lifecycle counter checks are factored. Untested.
- **P2 — Reports rules:** WIP candidate caches post-batch task/offer and report maps in the report-counter validators. Untested.
- **P3 — Profile + proposals rules:** WIP candidate moves auth/profile-existence checks ahead of status reads, prefilters profile counter updates by changed fields, caches repeated linked document reads, and separates proposal payload validation from linked-state checks. Untested.
- **P4 — CI guard:** WIP workflow gate added to fail if the raw Emulator log contains the exact 1,000-expression-ceiling message; a static test checks that the gate remains present. Untested.
- **P5 — Run CI:** **Not run by request.** No new test result is claimed.
- **P6 — Add `MARKET_SPEC_ADDENDUM_RULES_PHASE.md` unchanged:** **Blocked / not added.** The original attachment was not present in the accessible files/source branch at the last check. Do not recreate or edit it; attach the original for a byte-preserving add.

`DEPLOY_CHECKLIST.md` is retained unchanged. Deployment remains on hold pending expression diagnostics resolution, production rules/index export and comparison, legacy `proposerId` backfill, and staging verification.

## Mapping of the 39 diagnostics in run 39533202614

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

## Repeated-cost patterns identified

1. Task and offer owner-update rules repeatedly evaluate `request.resource.data.diff(resource.data).affectedKeys()`; the WIP candidate binds the affected-key set once and reuses it.
2. Task/offer report-counter validators repeatedly read the same post-batch task/offer and report documents using `getAfter()`; the WIP candidate binds these maps once per validator.
3. Profile counter mutation validators repeatedly read the same target documents, especially with `getAfter()`, and dispatch through long operation OR chains; WIP changes bind repeated target reads and add a cheap counter-field allowlist before the expensive dispatcher.
4. Proposal creation repeatedly reads task/proposal/profile-linked state while validating payload fields; the WIP candidate moves payload checks ahead of linked-state validation and caches task/profile maps.
5. Posting eligibility originally read Market profile status before auth and document-existence checks; the WIP candidate puts those cheap guards first.

These are refactoring hypotheses, **not proven expression-count reductions** until the Emulator runs with a clean raw log. The previous run had 39 diagnostics despite 65/65 assertions passing.
