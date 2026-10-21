# U2U Market Step 1 Build Progress

**Branch:** `feature/market-step1`
**Base:** `review/market-rules` at `aab0795a1d67b0e8d99f52bf3e815abcd0aa85b3`
**Build status:** Part 1 complete. Latest green APK and unit-test run: #580 (`39609435088`) on `d586f8af6b195e7759bff6a53d9f4613289d9ba1`. No Market UI or repository write methods are wired yet.
**Safety:** Do not modify `firestore.rules`, the protected top-level `tests/`, or `firestore.indexes.json`; do not change existing U2U chat/friend/Nearby logic or existing view IDs. No merge or Firebase deployment.

## Baseline gate

- Verified rules baseline commit: `6249b60165cbe96d2aa2102e22d22ac99469c1c1`.
- Latest pre-code confirmation run #22 on restore commit `2a136f1cc8478a27b0ef101a7a3c0ec8bde86ad9`: static 37 passed / 0 failed; Emulator 65 passed / 0 failed; expression-ceiling diagnostics 0; overall PASS.
- The protected rules, rule tests, rule-test workflow, and indexes remain unchanged from the baseline in this build branch. `PROGRESS.md` contains review history only.

## Required sources read in order

1. `MARKET_SPEC_ADDENDUM_RULES_PHASE.md`
2. `firestore.rules`
3. `firestore.indexes.json`
4. `INDEX_QUERY_MAP.md`
5. `MARKET_FINAL_SPEC.md`

Addendum governs conflicts with `MARKET_FINAL_SPEC.md`; Firestore rules determine allowed data shapes and authorization. Query builders must preserve the required rule constraints and use the index map.

## Parts and checkpoints

- **Part 0 — gate and docs-only wording:** complete. Protected-file baseline gate passed. Addendum wording commits: `fa0a96e84bca50a48abdb7b8772fa12fa3ebf957` (`docs: addendum wording fixes`) and `53aa5e7b0ba7ba1d14801e44b87da5e17e24165e` (removed a duplicate deployment note). Clarified task/offer counter lifecycle, verified-email versus self-service profile edits (including `blockedUids`), admin audit/notice responsibility, required task conversation batching, and green CI baseline (runs #20 and #22). No rules, protected tests, indexes, UI, or deployment changes.
- **Part 1 — typed models and converters:** complete.
  - Implementation commit: `3052b0b238f49e3e8ffc1609afd38925121ab5d0` (`feat(market): add typed data models and strict converters`).
  - Follow-up compiler fix: `d586f8af6b195e7759bff6a53d9f4613289d9ba1` (`fix(market): constrain converter enum helper`). Initial run #579 identified the generic type-bound issue at `MarketConverters.kt:637`; fixed and verified in run #580.
  - Build trigger commit: `ee2c4b7323535bb09534aab7675babdff556f5eb` adds `feature/market-step1` to the existing APK-build workflow's push branches. The protected Firestore-rules workflow was not changed.
  - Files: `app/src/main/java/com/superiorstudent/app/feature/market/data/MarketModels.kt`; `app/src/main/java/com/superiorstudent/app/feature/market/data/MarketConverters.kt`; `app/src/test/java/com/superiorstudent/app/feature/market/data/MarketConvertersTest.kt`.
  - Coverage: typed wire enums and models for Market profile, tasks, proposals, offers, Market conversations/messages, reports, ratings/userStats, notices, support tickets/replies, admin logs and config. Converters validate exact top-level allowed fields, deterministic IDs, conditional conversation IDs, optional field types, and preserve support-reply and limits-map extras where rules permit them. No UI or repository writes were added.
  - Source/schema assertions: 19/19 passed. Six Kotlin unit tests added covering profile key shape, task optional fields, proposal ID, conversation ID, report ID/values, and config weights/extra limits.
  - **GitHub Actions run #580:** [run details](https://github.com/Webster-02/Superior-Student/actions/runs/39609435088) — JavaScript validation PASS; clean PASS; `gradle testDebugUnitTest` PASS; `gradle assembleDebug` PASS; APK verification PASS; artifact upload PASS. Artifact: `superior-student-debug-apk` (40,696,162 bytes; available until 2026-10-28).
- **Part 2 — MarketRepository:** implementation committed; validation pending.
  - Added `MarketRepository.kt` as the single Android Market Firestore write boundary. The implementation groups linked counter/listing changes transactionally, includes deterministic IDs and timestamp policies, and adds Market-specific task/offer/proposal/report/rating/chat/support/admin operations.
  - Added `MarketRepositoryPolicyTest.kt` for client timestamp safety margin, 14/30-day expiry calculations, deterministic conversation/report/rating/appeal IDs, and initials.
  - Initial validation run #582 (`39613624938`) failed in `MarketRepository.kt` because the Android Firestore transaction API does not support query reads. Corrected suspension to query candidate listings first, then re-read their document references inside the transaction. Run #583 (`39614293580`) passed unit tests, APK build, verification, and artifact upload for that fix.
  - Follow-up before final validation: added offer-based Market conversation creation; fixed support reply timestamps (server transforms inside reply arrays are avoided); fixed renewal-window boundary calculations; added an appeal decision method that separates listing restoration and support-ticket counter mutations into two rule-compatible atomic transactions. This split is necessary because a profile update accepts one operation-specific `lastMutation` shape at a time.
  - Additional rule review found missing-document reads that are not permitted to ordinary users for deterministic reports/chats/appeal tickets, and a private-profile read in user reports. Removed those unnecessary reads; deterministic report/ticket writes remain protected by the existing create/update rules and the appeal notice's single-use `appealed` transition. User-report writes now rely on rules to verify that the target Market profile exists, without reading another user's private profile. The current commit is pending its Actions run.
- **Part 3 — query builders:** not started.
- **Part 4 — config and persistent 6-hour cache:** not started.
- **Part 5 — pure ranking scorer and unit tests:** not started.
- **Part 6 — Firestore error mapping:** not started.

## Exact next step

Finish Part 2 validation: confirm the APK build and unit tests pass, review any compiler/test failures, then record the successful Actions run and APK artifact. Part 3 query builders must wait until the user says `continue`.
