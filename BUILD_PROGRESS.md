# U2U Market Step 1 Build Progress

**Branch:** `feature/market-step1`
**Base:** `review/market-rules` at `aab0795a1d67b0e8d99f52bf3e815abcd0aa85b3`
**Build status:** Parts 1, 2, Part 3A, and Part 3B (Applied proposals collection-group query) complete. Latest green APK and unit-test run #591 (`39623231607`) on `51d7b5eab9aff6d52531a320905de4914983e5c2`. No Market UI or later query-builder/cache/scoring integration is wired yet.
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
- **Part 2 — MarketRepository:** complete.
  - Main implementation commit: `666030fac6d21e97baab70fb4396bae0040a274a`; subsequent reviewed fixes are `3b331433e36ef24351bc2d9b3b9f1a66f310e1ba`, `bdc2ecec5ddf63ec57feec25dc738fc392804955`, `cf6e375f4b195bd8c12a0359e32c818817cf05ac`, `8ac056a4300223345b2c790b7e12413ddfe3f9de`, and final code commit `7c376fed7f68ac37bf06e7fb713a5f0cd3967b69`.
  - Added `MarketRepository.kt` as the sole Android Market Firestore write boundary, plus `MarketRepositoryPolicyTest.kt`. Methods cover Market profile setup/preferences/blocking, task and offer create/edit/lifecycle, proposals and poster decisions, atomic accept + deterministic task conversation, offer conversations, reports and rolling counters, ratings and `userStats`, text-only Market messages, notice read state, support tickets/replies/appeals, admin report/listing/user/settings/support actions, audit logs and notices.
  - Cross-document mutations are grouped into Firestore transactions with operation-specific `lastMutation` shapes and linked counter changes. Task/offer creation and renewal use the 30-second client timestamp safety margin and exact 14/30-day expiry. Posting limits and deterministic report/appeal/rating IDs are checked locally while Firestore rules remain authoritative.
  - Appeal listing restoration and support-ticket resolution are two separate atomic transactions when a listing counter must also change, because Firestore rules accept only one operation-specific `lastMutation` shape for a profile update. Each phase includes its own audit record and affected-user notice.
  - Validation history: #582 failed because the client transaction API cannot read queries; fixed and tested query candidates by document reference. #584 and #585 found nullable appeal fields; #586 identified a nested `continueWithTask` return type. Those were fixed in the final code commit. Run #587 on that final code passed all steps.
  - **GitHub Actions run #587:** [build and APK artifact](https://github.com/Webster-02/Superior-Student/actions/runs/39619771793) — embedded JavaScript validation PASS; clean PASS; `gradle testDebugUnitTest` PASS; `gradle assembleDebug` PASS; APK verification PASS; artifact upload PASS.
  - Artifact: `superior-student-debug-apk`, 40,759,949 bytes, SHA-256 `d81b5e8ef60a0999742a263cebc31e2e9c4877a879d51a0670906422bb0b5db7`, expires 2026-10-28. No UI, protected rules/tests/indexes, Firebase deployment or merge changes were made.
- **Part 3 — query builders:** in progress; split into small reviewable slices.
  - **Slice 3A — student feed and own-list query builders:** complete. Added `MarketQueryBuilders.kt` with public task and offer feeds (mandatory public moderation/status filters), and owner-scoped My Tasks/My Offers queries; optional category/status filtering and index-map-aligned sorting; page size is bounded to 1–50 (default 20). Added `MarketQueryBuildersTest.kt` for query-plan contract, mandatory filters, ordering, owner scoping, and page-size/UID validation. No UI calls these builders yet.
  - **GitHub Actions run `39621871146`:** [run details](https://github.com/Webster-02/Superior-Student/actions/runs/39621871146) — JavaScript validation, clean, unit tests, APK build, APK verification, and artifact upload all succeeded. Artifact `superior-student-debug-apk`, 40,763,087 bytes, SHA-256 `6733096c078f975769f77c7e976339b510525587d6a2f08cae32564af272bc45`, expires 2026-10-28.
  - **Slice 3B — Applied proposals query:** complete. Added `appliedProposals(firestore, uid)` using `collectionGroup("proposals")`, mandatory `proposerId == uid`, `updatedAt DESC`, and a fixed 20-result limit. Updated the shared query executor to use collection-group queries only when the plan explicitly marks `collectionGroup = true`. Added tests for exact filter, ordering, limit, and blank-UID rejection.
  - **GitHub Actions run #591:** [run details](https://github.com/Webster-02/Superior-Student/actions/runs/39623231607) — JavaScript validation, clean, unit tests, APK build, APK verification, and artifact upload all succeeded. Artifact `superior-student-debug-apk`, 40,763,189 bytes, SHA-256 `ffe7924435591b273489bcc66bd6859272843eb24499df8b017924b310ed0e73`, expires 2026-10-28.
- **Part 4 — config and persistent 6-hour cache:** not started.
- **Part 5 — pure ranking scorer and unit tests:** not started.
- **Part 6 — Firestore error mapping:** not started.

## Exact next step

Slice 3B is complete and its build, tests and APK verification passed. The next small slice is 3C: the Market Chats list query (`participants array-contains current UID`, `updatedAt DESC`) with focused tests. Do not start slice 3C until the user says `continue`.
