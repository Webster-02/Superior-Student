# Market Rules Review Progress

**Branch:** `review/market-rules`  
**State:** Verified Step Zero rules baseline restored and green in CI run #22. Market Step 1 build has not started. P1–P4 candidate edits to protected files were reverted. No merge to `main` and no Firebase deployment.

## Step Zero verification record

- **Validated commit:** `6249b60165cbe96d2aa2102e22d22ac99469c1c1`
- **GitHub Actions run:** [#20 — Align static checks with corrected expression guard](https://github.com/Webster-02/Superior-Student/actions/runs/39583502817) — **success**
- **Static suite:** 37 passed, 0 failed.
- **Firestore Emulator suite:** 65 passed, 0 failed.
- **Expression-ceiling diagnostics in raw Emulator output:** 0.
- **Guard result:** diagnostic count 0; guard exit code 0; final summary `Overall: PASS`.
- **Rules baseline:** `firestore.rules` was restored byte-for-byte to the last known passing Rules blob (`8324f3bde6ee72d051a5635ba44ae73388439e02`, from commit `6893af1a273f17134e72f49e9ecac9f24e0ae999`). The untested rule refactor present in WIP commit `b7a7b39` was not retained: run #18 reproduced 39 expression-ceiling diagnostics and nine static-check failures. Do not reapply that experiment without a separately reviewed change and a fresh CI run.
- **P1 — Tasks + offers (pre-restore candidate, now reverted):** reduced repeated field access in create validators, cached status/owner/assignment/timestamp fields in owner-update validators, retained the single `affectedKeys()` diff per update dispatcher, cached pre-update report counters/moderation, and combined offer reactivation limit/counter checks around one cached profile before/after snapshot. Cheap create payload checks now run before timestamp checks. Existing allowlists, ownership checks, status transitions, and counter predicates remain in place. **Not tested in CI by instruction.**
- **P2 — Reports (pre-restore candidate, now reverted):** cached report-create profile mutation and report post-state snapshots; consolidated report existence validation with the task/offer report-counter validators while retaining explicit `existsAfter`, target identity, reporter identity, report-count, moderation-threshold, and profile-counter checks. The admin report update checks the status allowlist before the field diff. **Not tested in CI by instruction.**
- **P3 — Profile + proposals (pre-restore candidate, now reverted):** validates profile-create payload before existence reads, skips the expensive profile counter dispatcher unless numeric/window counter fields actually change (while preserving the separate exact admin suspension reset path), caches proposal preflight/linked-state values, and caches proposal update routing predicates. Existing counter, ownership, lifecycle, allowlist, and mutation checks remain in place. **Not tested in CI by instruction.**
- **P4 — CI diagnostic guard (pre-restore candidate, now reverted):** the workflow scans captured Emulator output for the stable core phrase `maximum of 1000 expressions to evaluate has been reached` case-insensitively, records the diagnostic count and guard status, and fails the job for any match. Static validation asserts the log source, matcher, positive-count failure branch, and final nonzero exit path. **This P4 change has not been run in CI.**
- **P5 — Run CI:** Step Zero baseline run #20 completed successfully. **No CI run for the current P1/P2/P3/P4 candidates**, as requested.
- **P6 — Original addendum (integrity verified):** the attached `MARKET_SPEC_ADDENDUM_RULES_PHASE (1).md` is 7,793 bytes / 68 lines; source SHA-256 is `ff60be0137604b27f93fe8f12ee3e5f0dcbcf7a38e250b3351bdea2a1da4c899`, matching the existing branch path `MARKET_SPEC_ADDENDUM_RULES_PHASE.md` (Git blob SHA `7cec066773fb5f40cdcf4e921e09ab9c92119800`). The file was already present on the branch byte-for-byte, so it was preserved unchanged rather than duplicated or rewritten. Static validation already pins this source SHA-256. The contradiction/gap review is recorded below; no rules or tests were changed to resolve it.
- **Deployment safety:** `DEPLOY_CHECKLIST.md` remains unchanged (blob SHA `ba9dd664d3348ef3d25a1c7e1717ee9519110b93`). No merge to `main`, production export overwrite, Firebase deployment, or staging deployment occurred. Production remains blocked pending the checklist's live rules/index baseline comparison, legacy `proposerId` backfill, staging verification, and explicit approval.

## P6 addendum contradiction / gap review (no fixes made)

Compared the attached addendum (unchanged) with the current `firestore.rules` and `tests/firestore.rules.test.mjs`. These are review findings only; P1–P4 remain untested.

1. **§4, lifecycle counter decrements (addendum L22):** The blanket requirement that cancel, expire, pause, remove, and complete must all carry a counter decrement is too broad for the implemented lifecycle. Task completion is a status-only update (`firestore.rules` L942–947; `tests/firestore.rules.test.mjs` L759–764); `openTasks` is decremented when the proposal is accepted (rules L935 and test L713–718). Removing an already-paused offer keeps `activeOffers` unchanged (rules L1373–1379), because pause already decremented it. The owner removal branch also permits removal only from `active` or `paused`, not `expired` (rules L1373–1375).
2. **§6, verified email (addendum L45):** “All Market writes require `email_verified == true`” conflicts with Market profile updates: the match uses `marketSignedIn()` rather than `marketVerified()` (`firestore.rules` L803), and the self-service `skillTags` / `blockedUids` / `settings` path does not check email verification (rules L696–702). The rules therefore do not enforce that blanket statement for this update path.
3. **§8, admin audit/notice linkage (addendum L63):** The rules validate `adminLogs` create independently (`firestore.rules` L1782–1794) and do not require every admin action to create a related log and notice in the same batch. Tests explicitly accept a standalone admin pin update without companion log/notice (`tests/firestore.rules.test.mjs` L743–747) and a standalone admin report-status update (tests L749–754). The suspension test proves one batched example, not the universal requirement.
4. **§7, task chat at acceptance (addendum L58):** The accept flow can be committed with a same-transaction task chat, and the positive test includes it (`tests/firestore.rules.test.mjs` L697–723). However, task assignment/counter validation does not require a corresponding `marketConversations` document, and there is no negative test showing acceptance fails when that conversation is omitted. The “same batch” requirement is therefore not enforced by these rules/tests.
5. **§1, profile counter wording (addendum L6 and L9):** “Can never edit … counters afterwards” is ambiguous against the implemented atomic counter mutations. Rules and tests allow changes to `openTasks`, `activeOffers`, proposal/report windows, etc. when bound to a valid `lastMutation` and matching operation. If the intent is “no direct or unpaired counter edits,” that narrower meaning is consistent; the literal blanket wording is not.
6. **§9, expression-ceiling note (addendum L67):** The addendum says the Emulator log contains 1,000-expression-limit diagnostics. The recorded Step Zero run #20 in this progress file reports zero diagnostics; an earlier run #18 recorded 39. This is a version/history mismatch, not proof that the current P1–P4 candidate is clear—the current candidate has not been rerun in CI.

No other files, rules, test cases, indexes, or workflow files were modified for P6. No CI run or Firebase deployment was performed.

## Historical P1/P2/P3/P4 pre-restore scope and verification status

P1 changed task/offer create and update helpers. P2 changed report-create/profile-counter validation, task/offer report-counter validation used by report updates, and the admin report-status update rule. P3 changed Market profile create/update validation and proposal create/update helpers. P4 hardened the workflow's Emulator-log expression-ceiling guard and the static assertions that protect it. No Firestore Rules, index, Emulator test case, deployment checklist, or Firebase project was changed by P4. The P1/P2/P3/P4 candidate later received CI run #21, which exposed three stale static assertions; it was subsequently reverted from the protected files. Run #22 below verifies the restored baseline.

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


## Current-head CI trigger verification (2026-10-21)

- Candidate tested: `9fbb8a423be36badce22e1f799d87658dda640c7`. A comment-only marker was added to `.github/workflows/firestore-rules-test.yml` to trigger the path-filtered workflow; trigger commit: `55260cbe0c1be04a05f8439aea46289ad2cab6b1`.
- Run #21: https://github.com/Webster-02/Superior-Student/actions/runs/39601819592 — **failure**.
- Static suite: **34 passed, 3 failed**. Failed assertions: (1) “assigned task completion is explicitly poster-only”; (2) “Market mutation validators route to one operation-specific branch”; (3) “task and offer report counters require a report document before linked reads.” These are existing static assertions and were not changed for the trigger.
- Firestore Emulator suite: **65 passed, 0 failed**.
- Raw `artifacts/test-emulator-output.txt`: `grep -Fic -- "maximum of 1000 expressions to evaluate has been reached"` = **0**; `grep -ci "expression"` = **0**; literal `PERMISSION_DENIED` diagnostics = **77**; reviewed against the passing Emulator test output, **0 unexpected denials identified**.
- No rules, indexes, Emulator tests, UI, or build code were changed for this check. No merge or deployment occurred. The CI run is not green; do not start Market build work until the static failures are resolved or verified-baseline fallback is explicitly approved.


## Guard and Step Zero raw-artifact verification (2026-10-21)

- CI matcher in `.github/workflows/firestore-rules-test.yml`: `expression_message="maximum of 1000 expressions to evaluate has been reached"`; counted by `grep -Fic -- "${expression_message}" artifacts/test-emulator-output.txt` (fixed-string, case-insensitive, count matching lines).
- Downloaded run #20 artifact `firestore-rules-test-results` and ran the same matcher directly against `artifacts/test-emulator-output.txt`: **0**. Plain `grep -ci "expression" artifacts/test-emulator-output.txt`: **0**. The two independent raw-file counts agree; run #20’s zero is not an artifact of a too-narrow matcher.
- Run #20 tested `6249b60165cbe96d2aa2102e22d22ac99469c1c1`: static **37/0**, Emulator **65/0**, summary `Overall: PASS`.
- Changes among the original 10 Market rule operations from Step Zero to the P1–P3 candidate: `marketProfiles` create/update (P3); `tasks` create/update (P1, with report-counter validation refined in P2); `proposals` create/update (P3); `offers` create/update (P1, with report-counter validation refined in P2); `reports` create/update (P2). All 10 operation paths were touched by those candidate commits.
- Current-candidate static failures map to stale, shape-specific source assertions, not yet-fixed rules: `tests/static-validate.mjs:26` expects `after.status` / `before.posterId`, while `firestore.rules:942-947` expresses the poster-only completion with cached locals `afterStatus` / `beforePosterId`; `tests/static-validate.mjs:62-63` expects raw `before.status` selectors, while task and offer owner updates use cached `beforeStatus` at `firestore.rules:907-948` and `1348-1390`; `tests/static-validate.mjs:65-68` expects removed helper names, while the document-existence checks are combined into `marketTaskReportCounterValid` at `firestore.rules:885-904` and `marketOfferReportCounterValid` at `1282-1300`, using `existsAfter` at lines 890 and 1287.
- At the time this historical run #21 note was recorded, no rule/test fix or baseline restore had been performed. The approved restore and green run are recorded in the latest section below.


## Approved verified-baseline restore and CI confirmation (2026-10-21)

- User approved restoring the verified baseline. Commit: [`2a136f1cc8478a27b0ef101a7a3c0ec8bde86ad9`](https://github.com/Webster-02/Superior-Student/commit/2a136f1cc8478a27b0ef101a7a3c0ec8bde86ad9), message `restore verified rules baseline (#20)`, parent `babcebaba6e23b2d0a3b571fee97547c2055b1f3`.
- The commit restores the exact Step Zero Git blobs: `firestore.rules` = `8324f3bde6ee72d051a5635ba44ae73388439e02`; `.github/workflows/firestore-rules-test.yml` = `d40424a21563df2da824cc0ba51a27d0661ae867`; `tests/static-validate.mjs` = `58fc4d4ae4a510b943147d2ab6443b42c8089ed0`. Comparison against commit `6249b60165cbe96d2aa2102e22d22ac99469c1c1` shows `PROGRESS.md` as the only remaining changed path; all `tests/` contents match the baseline.
- CI run #22: https://github.com/Webster-02/Superior-Student/actions/runs/39603954521, tested commit `2a136f1cc8478a27b0ef101a7a3c0ec8bde86ad9`, **success**.
- Actual artifact output: static **37 passed, 0 failed**; Emulator **65 passed, 0 failed**; expression-ceiling guard count **0**, guard exit code **0**, final summary `Overall: PASS`.
- Direct greps against the run #22 raw Emulator artifact: `grep -Fic -- "maximum of 1000 expressions to evaluate has been reached"` = **0**; `grep -ci "expression"` = **0**. Literal `PERMISSION_DENIED` lines = **77**, matching the expected denials logged during the passing authorization-denial tests; unexpected denials identified = **0**.
- No build work has started. Do not begin Market Step 1 until explicitly instructed after this green baseline verification. No merge or deployment occurred.
