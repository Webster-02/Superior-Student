# Market Rules Review Progress

**Branch:** `review/market-rules`  
**State:** Verified Rules baseline with targeted Market security hardening under review. Current candidate includes verified-email profile edits, task acceptance/chat atomicity, and Rules-enforced linked audit logs/notices for Market config plus task/offer moderation and pins. Report status, support/admin profile actions and production gates remain open. No merge to `main` and no Firebase deployment.

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


## Follow-up security hardening — verified CI run #24 (2026-10-22)

**Current tested commit:** [`46ce86ce325135814963fb5e077b6257694731cb`](https://github.com/Webster-02/Superior-Student/commit/46ce86ce325135814963fb5e077b6257694731cb)  
**Workflow:** [Firestore Rules Review Tests — run #24](https://github.com/Webster-02/Superior-Student/actions/runs/39856525223) — **success**  
**Artifact:** `firestore-rules-test-results`, ID `12459119458`.

Raw downloaded artifact was inspected directly:
- Static validation: **39 passed, 0 failed**.
- Firestore Rules Emulator: **67 passed, 0 failed, 0 skipped**, across 14 suites.
- 1,000-expression-ceiling diagnostic count: **0**; CI guard exit code 0; summary `Overall: PASS`.
- The positive acceptance test (task, accepted proposal, poster counter and new Market chat in one transaction) passes.
- New negative acceptance test proves the transaction is denied if it changes the task to assigned and accepts the proposal but omits the new Market conversation.
- New profile security fix: self-service `marketProfiles/{uid}` updates now require `marketVerified()`; a new test denies an unverified user's profile preference edit and allows a verified user's edit.

### Current targeted rule changes

1. Commit `0ce0b2e4aaf2767f4742ced6c8a75dd947bfc217` changed self-service Market profile updates from signed-in-only to verified-email authorization and added a verified/unverified regression test.
2. Commit `46ce86ce325135814963fb5e077b6257694731cb` binds task acceptance to a brand-new deterministic conversation document in the same atomic write. The task acceptance validator requires `existsAfter(conversationPath)` and `!exists(conversationPath)`; the Market conversation's own create rule validates its schema and participants.

Run #24 tested the rules blob `b039f9f896c27314eb1f57594e0c1fe911be92a6`. The workflow and both test files are the current committed versions. The artifact's raw Emulator output contains zero expression-ceiling diagnostics.

### Still-open specification gaps — do not deploy yet

- **Admin audit linkage:** Market configuration and task/offer pin/moderation now require linked new immutable `adminLogs` records in the same write and a linked user notice for listing changes. Report status, support actions, and direct account warning/suspension/profile status changes still need Rules-enforced log/notice linkage.
- **Lifecycle-counter wording:** clarify each operation's exact counter transition; do not double-decrement task completion after acceptance or offer removal after pause.
- **Original addendum §9:** its expression-ceiling note describes older output (including the older 39-diagnostic run). Keep the addendum itself byte-identical because the static validator verifies its supplied source hash; this progress note records the newer verified result.
- **Production baseline:** live published rules/indexes have not been exported or compared. Email-verification readiness and Market profile completeness are not yet checked against production Auth/data.
- **Legacy proposals:** historic `proposerId` backfill has not been run. It requires dry-run, backup, mismatch review, bounded idempotent writes and post-write verification.
- **Staging:** no staging Firebase deployment or end-to-end staging flow checks have been performed.

No Firebase Rules or indexes have been published/deployed. No merge to `main` occurred. Existing university sign-in, Student Connect chat, friend requests, notifications and Nearby-transfer rules were not edited by these two targeted Market changes.


## Phase 2 — Step 9: Market configuration audit linkage (verified 2026-10-22)

**Rules candidate commit:** [`1f5536f4387cb7254e4a0ccae04c896b1a68b511`](https://github.com/Webster-02/Superior-Student/commit/1f5536f4387cb7254e4a0ccae04c896b1a68b511)  
**Rules workflow:** [Run #27](https://github.com/Webster-02/Superior-Student/actions/runs/39858637212) — **success**  
**Rules artifact:** `firestore-rules-test-results`, ID `12459668968`.

Actual downloaded CI output:
- Static checks: **40 passed, 0 failed**.
- Firestore Rules Emulator: **67 passed, 0 failed, 0 skipped**, across 14 suites.
- Expression-ceiling diagnostics: **0**, guard exit code 0; summary `Overall: PASS`.
- Rules blob tested: `15ae62777f1d9486c3bd15f364b03a82b934b34e`.

### What the safeguard enforces

- `config/market` now has a server-owned `lastAuditLogId` pointer. Admin create/update requires a matching new `adminLogs/{logId}` document in the same atomic write.
- The linked log must be newly created, immutable after creation, owned by the current admin identity, with action `update_market_config`, target type `config`, target ID `market`, and `createdAt == request.time`.
- Tests deny a configuration update with no linked log, a stale pointer, and a pointer to a different/nonexistent log. Valid edits with the linked log still pass. Invalid weights and fixed limits continue to be denied.
- The client repository writes the linked ID and the immutable audit record in the same transaction. The strict config decoder accepts this optional metadata field when reading while keeping the user-facing `MarketConfig` model and converter's ordinary serialized fields unchanged.

**App transaction commit:** [`4a74df5e6d7d383f4f38fda716b8eaafed4ebdb0`](https://github.com/Webster-02/Superior-Student/commit/4a74df5e6d7d383f4f38fda716b8eaafed4ebdb0) on `feature/market-ui-reference`.  
**Android verification:** [Market Data Step Check — run #100](https://github.com/Webster-02/Superior-Student/actions/runs/39858373895) — **success**; unit tests and debug APK build passed. Artifact `superior-student-debug-apk`, ID `12459833836`.

The two commits are on separate review/app branches by design. The app branch writes the metadata expected by the review Rules; neither branch has been merged to `main`.

### Remaining admin gaps

- Task and offer pin/moderation still permit some direct edits without requiring the matching audit log and user notice.
- Report status updates are still permitted without a required same-batch audit log and reporter notice.
- Support reply/resolve and user warning/suspension operations need complete log/notice linkage enforced by Rules, not just by the repository transaction.
- These other operations are the next review steps. The config audit pointer does not by itself secure them.

No Firebase Rules or indexes were published or deployed. No live data was changed.


## Phase 2 — Step 10: Task and offer moderation/pinning audit linkage (verified 2026-10-22)

**Rules test commit:** [`181e347c14032a8b8ddfd5a0c314afe17111a016`](https://github.com/Webster-02/Superior-Student/commit/181e347c14032a8b8ddfd5a0c314afe17111a016)  
**Rules workflow:** [Firestore Rules Review Tests — run #29](https://github.com/Webster-02/Superior-Student/actions/runs/39860003494) — **success**  
**Rules artifact:** `firestore-rules-test-results`, ID `12461586838`.

Actual downloaded artifact:
- Static validation: **41 passed, 0 failed**.
- Firestore Rules Emulator: **67 passed, 0 failed, 0 skipped**, across 14 suites.
- Expression-ceiling diagnostics: **0**; CI guard exit code 0; summary `Overall: PASS`.
- Rules blob tested: `3bd08c780ebdb2eef69033260d6a86f4ceee4c2c`.

### Enforced behavior

- Task/offer admin pin and moderation updates now carry `lastAuditLogId` and `lastAdminNoticeId` pointers on the listing update.
- The Rules require the referenced audit log and owner notice to be created in the same atomic write, not to exist beforehand, and to match the authenticated admin, action (`moderate_task`/`moderate_offer`), listing type/ID and request timestamp.
- The paired notice must be new and relate to the same listing. The immutable `adminLogs` schema remains unchanged.
- Tests deny direct pin updates without a linked audit log/notice and deny mismatched target types. Positive tests cover task and offer hide/restore and pin/unpin flows, including counter mutations.
- Bulk user suspend/ban remains supported: affected listings point to the aggregate `suspend_user`/`ban_user` log and the new removal notice; Rules verify the matching post-write suspended/banned profile and `lastMutation`. Its existing aggregate access-call test remains green.

**App repository commit:** [`a8b8e55d0b3cfec5f19323b31091572282c18d00`](https://github.com/Webster-02/Superior-Student/commit/a8b8e55d0b3cfec5f19323b31091572282c18d00) on `feature/market-ui-reference`.  
**Android workflow:** [Market Data Step Check — run #101](https://github.com/Webster-02/Superior-Student/actions/runs/39859898773) — **success**. Unit tests and debug APK build passed. Artifact `superior-student-debug-apk`, ID `12461825353`.

The app repository adds the log/notice IDs to moderation/pin writes and to every listing hidden by a bulk suspension/ban transaction. Task/offer decoders accept and validate these optional metadata fields; unit tests cover valid and malformed pointer metadata. The UI/business listing models are unchanged.

The app and Rules changes are on separate branches. They have not been merged to `main`.

### Remaining admin gaps

- Report-status audit/notice linkage is completed in Phase 2 Step 11 below.
- Support reply/resolve and user warning/lift-suspension/profile-status changes still need the corresponding audit logs and notices enforced by Rules, not merely written by the repository.
- Production live Rules/indexes are still not exported/compared; historical proposal `proposerId` backfill and separate staging checks remain.
- Lifecycle-counter spec wording needs clarification.

No Firebase Rules or indexes were published or deployed. No live data was changed.


## Phase 2 — Step 11: Report-status audit and reporter-notice linkage (verified 2026-10-22)

**Rules commit:** [`b19472ad8d9d7ce0811285e66d9dfc0fadbc7fba`](https://github.com/Webster-02/Superior-Student/commit/b19472ad8d9d7ce0811285e66d9dfc0fadbc7fba) on `review/market-rules`.  
**Rules CI:** [Firestore Rules Review Tests — run #30](https://github.com/Webster-02/Superior-Student/actions/runs/39862784731) — **success**. Artifact `12462911143`.  
**App commit:** [`3c6da1a713ec836d7b5229324ac3bcb65dc415af`](https://github.com/Webster-02/Superior-Student/commit/3c6da1a713ec836d7b5229324ac3bcb65dc415af) on `feature/market-ui-reference`.  
**Android CI:** [Market Data Step Check — run #102](https://github.com/Webster-02/Superior-Student/actions/runs/39862887618) — **success**. Debug APK artifact `12463315396`.

### Enforced behavior and verification

- Report status, `lastAuditLogId` and `lastAdminNoticeId` must all change in the same update, with no unrelated fields changed.
- Rules require both referenced documents to be new in the same atomic write. The audit log must match the current admin, `set_report_status` action, report type/ID and request timestamp. The new reporter notice must have type `activity`, relate to that report, and carry the same request timestamp.
- The app repository creates one notice ID, writes that ID to the report, and creates the matching reporter notice and immutable audit log in the same transaction. The report converter accepts only bounded nonblank pointer strings if those optional metadata fields exist.
- Negative tests cover reporter direct-write denial, admin direct status update without links, missing notice, mismatched audit target type and a notice linked to a different report. A valid linked report-status transaction succeeds.
- Rules run #30: **42/42 static checks passed; 67/67 Emulator tests passed; 0 skipped; 0 expression-ceiling diagnostics**.
- Android run #102: route validation, unit tests, debug APK build, APK verification and artifact upload passed.

The Rules and app changes remain on separate review branches. They have not been merged to `main`.

### Remaining admin gaps

Ordinary non-appeal support reply/resolve linkage is completed in Phase 2 Step 12 below. Appeal-ticket decision linkage and direct account warning/lift-suspension/profile-status mutations still need Rules-enforced audit-log and notice linkage. Lifecycle-counter semantics need clarification. Live production Rules/indexes have not been exported and compared; the historical proposal `proposerId` migration and separate staging verification remain outstanding.

No Firebase Rules or indexes were published or deployed. No live data was changed.


## Phase 2 — Step 12: Support reply and resolution audit linkage (verified 2026-10-22)

**Rules commit:** [`d0428fb7b6d6e2414a2d199d954564527193df78`](https://github.com/Webster-02/Superior-Student/commit/d0428fb7b6d6e2414a2d199d954564527193df78) on `review/market-rules`.  
**Rules CI:** [Firestore Rules Review Tests — run #32](https://github.com/Webster-02/Superior-Student/actions/runs/39865131476) — **success**. Artifact `12462414890`.  
**App commit:** [`d1d95c4d3c4e9ff3fc5d8596cb0e74eb87d0392c`](https://github.com/Webster-02/Superior-Student/commit/d1d95c4d3c4e9ff3fc5d8596cb0e74eb87d0392c) on `feature/market-ui-reference`.  
**Android CI:** [Market Data Step Check — run #103](https://github.com/Webster-02/Superior-Student/actions/runs/39864585562) — **success**. APK artifact `12464190239`.

### Enforced behavior and verification

- For non-appeal support tickets, admin replies/resolutions now update the ticket with `lastAuditLogId` and `lastAdminNoticeId`; Rules require the referenced log and notice to be newly created in the same atomic write.
- The audit log must match the current admin, action (`reply_support_ticket` or `resolve_support_ticket`), support ticket ID and request timestamp. The new owner notice must have type `support_reply`, relate to that ticket, and share the request timestamp.
- The repository now creates and reuses one notice reference in the transaction for both reply and resolve operations. Resolution continues to update the owner's open-support-ticket counter in the same transaction.
- The support-ticket converter accepts and validates optional bounded, nonblank audit/notice IDs.
- Tests deny direct admin status/reply writes, missing notices and mismatched audit targets; valid atomic reply and resolution writes pass. Owner-authored replies retain the existing strict constraints.
- The existing appeal-specific admin update branch was deliberately left separate in this step; appeal-decision linkage needs its own review.
- Rules run #32: **43/43 static checks passed; 68/68 Emulator tests passed; 0 skipped; 0 expression-ceiling diagnostics**.
- Android run #103: route validation, unit tests, debug APK build, APK verification and artifact upload passed.

The Rules and app changes remain on separate review branches. They have not been merged to `main`.

### Remaining admin gaps

Appeal-ticket decision linkage and direct account warning/lift-suspension/profile-status mutations still need Rules-enforced audit-log and notice linkage. Lifecycle-counter semantics require clarification. Live production Rules/indexes have not been exported and compared; the historical proposal `proposerId` migration and separate staging verification remain outstanding.

No Firebase Rules or indexes were published or deployed. No live data was changed.

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

 
## Phase 2, Step 14 — Account moderation audit linkage (verified 2026-10-22)

### Rules implementation and verification
- Rules implementation commit: `d19480430c8aab5aba4585b89b926968dd2d48aa`; follow-up short-circuit/complexity fix: `a40a25847a23453f0b24c0510b282ce2e568ef25`.
- Final Rules workflow run #36: https://github.com/Webster-02/Superior-Student/actions/runs/39880626698 — **success**.
- Static validation: **45 passed, 0 failed**.
- Firestore Rules Emulator: **71 passed, 0 failed, 0 skipped**.
- Expression-ceiling diagnostics: **0**; guard exit code 0.
- Profile warning, suspend/ban, and lift-suspension updates now require the profile's `lastAuditLogId` and `lastAdminNoticeId` to point to new records created atomically with the profile change. Rules verify the authenticated admin, user target, action, timestamp, notice type/title and allowed status/counter transition.
- Warning increments are constrained to exactly +1. Suspension/ban still clears the relevant listing counters and retains the existing same-transaction listing hide/log/notice contract. Lift is limited to a suspended account becoming active; ban review remains separate.
- Negative and positive Emulator tests cover warnings with missing pointers and mismatched audit targets, lift without pointers and valid lift, and suspension without pointers plus the valid bulk-suspension transaction.
- A first candidate run tripped the 1,000-expression guard because profile-update branches were evaluated without short-circuit routing. The follow-up restored the existing short-circuit dispatcher. Final run #36 has zero ceiling diagnostics.

### Android implementation and verification
- Android commit: `de591fdc6d987cf614a7a0011d2fc1490b295160` on `feature/market-ui-reference`.
- Repository transactions now write matching profile audit pointers and reuse the exact notice IDs. The profile converter safely accepts optional bounded audit pointers without changing the student-facing profile model.
- Android workflow run #105: https://github.com/Webster-02/Superior-Student/actions/runs/39880533430 — **success**. Embedded JavaScript validation, reference-route checks, clean build, unit tests, debug APK build, APK verification and artifact upload all passed.
- APK artifact: `superior-student-debug-apk`, ID `12471070183`.

### Current remaining gates before testing/deployment
- Clarify lifecycle-counter transitions in the spec and validate them against current implementation/tests.
- Export and compare the currently published production Rules/indexes against the candidate baseline, and separately validate Auth/profile prerequisites.
- Review historical proposal `proposerId` records before any strict Rules deployment.
- Deploy to a separate staging Firebase project and perform the full student/admin smoke checklist. No staging project ID or Firebase CLI session is available through the current repository connection.
- Explicit owner approval is still required before any production deployment.

No Firebase Rules or indexes were deployed, no live Firebase data changed, and neither branch was merged into `main`.


## Phase 2, Step 15 — Lifecycle-counter contract and no-double-decrement regressions (verified 2026-10-22)

**Rules/spec/test commit:** `b37032cf3198723fe58d25d4bc84302677161a0f`.  
**Rules CI run #37:** https://github.com/Webster-02/Superior-Student/actions/runs/39881170691 — **success**. Artifact `12471180011`.

- Static validation: **46 passed, 0 failed**.
- Firestore Rules Emulator: **73 passed, 0 failed, 0 skipped**.
- Expression-ceiling diagnostics: **0**; guard exit code 0.
- Added Section 15, “Counter transition contract”, to `MARKET_FINAL_SPEC.md`. It defines the meaning and expected delta for open tasks, active offers, per-task proposal counts, proposal/report rolling-window counts, listing report counts, open support tickets and warning totals.
- Added an Emulator regression proving that acceptance decrements `openTasks` once, and the later assigned-to-completed transition does not decrement it again.
- Added an Emulator regression proving removing a paused offer leaves `activeOffers` unchanged, and a paired negative-counter write is rejected.
- Existing task/offer expiry, admin hide/restore, aggregate suspension, report and support counter tests remain green.

No Android change was needed for this step. No Firebase Rules/indexes were deployed, no live data was changed, and no merge to `main` occurred.

### To start real-device Market functional testing

The Android debug APK from run #105 is ready: https://github.com/Webster-02/Superior-Student/actions/runs/39880533430 (artifact `superior-student-debug-apk`, ID `12471070183`). It is built with the checked-in Firebase app config, project ID `pachattarpercent`. Do not treat that as a staging project. Candidate Rules still need an isolated Firebase staging project with staging Android config and staged Rules/indexes before the app can perform an end-to-end backend test safely.


## Staging procedure validation (workflow run #38)

- Updated `DEPLOY_CHECKLIST.md` with a copy-ready, staging-only deployment/build sequence and an ordered phone smoke-test plan.
- [Run #38](https://github.com/Webster-02/Superior-Student/actions/runs/39881528899), commit `d00cc23a02b192b2f414de88ce27dae453b5ff03`, passed again: **46 static checks, 73 Emulator tests, zero expression-ceiling diagnostics**. Artifact ID `12470767412`.
- This does not substitute for deployment to a separately created staging project. The current checked-in Android Firebase config targets `pachattarpercent`; do not use the candidate Rules on that project solely to speed up testing.
