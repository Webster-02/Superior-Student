# U2U Market Step 1 Build Progress

**Branch:** `feature/market-step1`
**Base:** `review/market-rules` at `aab0795a1d67b0e8d99f52bf3e815abcd0aa85b3`
**Build status:** Parts 1–3 complete. Part 4 Steps 4A, 4B1, 4B2 and 4C1 (cache policy, versioned codec, persistent local store, and read-only config reader) are complete and green in runs #596 (`39631043160`), #598 (`39632795357`), #600 (`39634123211`) and #602 (`39635387870`). Remote refresh/cache fallback wiring is not complete.
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
- **Part 3 — rule-constrained Market query builders:** complete.
  - **Public feeds and own listings (Slice 3A):** `publicTasks` always filters `moderation == ok` and `status == open`; `publicOffers` always filters `moderation == ok` and `status == active`. Optional category and pinned/newest ordering variants match `INDEX_QUERY_MAP.md`. `myTasks` always filters `posterId == uid`; `myOffers` always filters `sellerId == uid`. Page sizes are validated within 1–50 (default 20).
  - **Applied proposals (Slice 3B):** `collectionGroup("proposals")` with mandatory `proposerId == uid`, `updatedAt DESC`, limit 20.
  - **Market Chats (Slice 3C):** `marketConversations` with mandatory `participants ARRAY_CONTAINS uid`, `updatedAt DESC`, limit 20. This does not use or modify the existing U2U chat collection.
  - **Remaining collections:** `taskProposals` stays within `tasks/{taskId}/proposals` and optionally filters status; `myReports` always filters `reporterId == uid` and optionally filters target type; `myNotices` reads only `users/{uid}/notices` and optionally filters read state; `mySupportTickets` always filters both `userId == uid` and a status, matching the indexed `userId/status/updatedAt DESC` shape.
  - The shared executor explicitly supports base collections, nested collection paths, and collection groups. No UI calls these query builders yet; Firestore Rules remain the authorization boundary.
  - `MarketQueryBuildersTest.kt` now contains 19 focused query-plan tests covering required moderation/status constraints, owner scoping, participant-only chats, Applied proposals, nested paths, report/notices/support filters, sort order, limits, and invalid UIDs/IDs.
  - **GitHub Actions run #595:** [build and APK artifact](https://github.com/Webster-02/Superior-Student/actions/runs/39629560052) — JavaScript validation, clean, unit tests, APK build, APK verification, and artifact upload all succeeded. Artifact `superior-student-debug-apk`, 40,766,614 bytes, SHA-256 `ccb73b24fca205db18b78d4a2fa85697f75bbfae11e39792c72146ce84373d50`, expires 2026-10-28.
- **Part 4 — config and persistent 6-hour cache:** in progress, divided into small reviewable steps.
  - **Step 4A — pure cache policy:** complete. Added `MarketConfigCachePolicy.kt` with a strict six-hour freshness boundary; missing/invalid/future saved timestamps are stale. Stale cache is eligible as fallback only when a cached value exists and the caller identifies a connectivity failure, not for authorization or invalid-data errors. Added `MarketConfigCachePolicyTest.kt` for TTL boundary, invalid/future timestamps, fallback gating, and exact TTL.
  - **GitHub Actions run #596:** [run details](https://github.com/Webster-02/Superior-Student/actions/runs/39631043160) — JavaScript validation, clean, unit tests, APK build, APK verification, and artifact upload all succeeded. Artifact `superior-student-debug-apk`, 40,767,078 bytes, SHA-256 `6aca26d906204335ca33927ea1c7998ee03ccc7f16e862b071079afa7874c902`, expires 2026-10-28.
  - **Step 4B1 — versioned cache codec:** complete. Added `MarketConfigCacheCodec.kt` and `MarketConfigCacheCodecTest.kt`. The pure codec versions its envelope, enforces a 64 KiB UTF-8 payload limit, preserves timestamps down to nanoseconds, nullable values, and nested supported scalar/map/list types in allowed extra limit fields. It rejects invalid config shapes, malformed/unknown-version payloads, oversized entries, and unsupported nested types rather than silently dropping values. This sub-step does not read/write Android storage or contact Firestore.
  - Six focused codec tests cover config round-trip and saved-at timestamps, nullable announcement timestamps, nested extra fields, invalid config/timestamps, malformed payloads and versioning, payload bounds, and unsupported types.
  - **GitHub Actions run #598:** [build and APK artifact](https://github.com/Webster-02/Superior-Student/actions/runs/39632795357) — JavaScript validation, clean, unit tests, APK assembly, APK verification, and artifact upload all succeeded. Artifact `superior-student-debug-apk`, 40,774,420 bytes, expires 2026-10-28.
  - **Step 4B2 — persistent local cache store:** complete. Added `MarketConfigCacheStore.kt` backed by a dedicated app-private `SharedPreferences` file and the Step 4B1 codec. It exposes write/read/clear, uses synchronous commit results for write/remove, enforces the codec's payload bound, returns stale entries for the caller's policy-controlled offline fallback, and clears malformed or incompatible cached payloads. Invalid configs and failed writes do not replace the previously saved value. The store has no Firestore dependency.
  - Added `MarketConfigCacheStoreTest.kt` with seven local tests: write/read timestamp round-trip; overwrite; stale entry retained for policy-controlled fallback; clear affects only Market config; malformed entry cleanup; invalid config/rejected write preserve existing cache; and preference runtime failures do not escape.
  - **GitHub Actions run #600:** [build and APK artifact](https://github.com/Webster-02/Superior-Student/actions/runs/39634123211) — JavaScript validation, clean, all unit tests, APK assembly, APK verification, and artifact upload all succeeded. Artifact `superior-student-debug-apk`, 40,777,383 bytes, expires 2026-10-28.
  - **Step 4C1 — read-only Firestore config reader:** complete. Added `MarketConfigReader.kt`. It reads only `config/market` through Firestore's read API, propagates Firestore read failures, and routes the returned document identity and data through a strict decoder. It does not write, alter authorization, automatically fall back to cached values, or modify any UI.
  - Added `MarketConfigReaderTest.kt` with four focused tests: the fixed collection/document identity; successful strict config decoding; rejection of a different document ID; and rejection of missing/extra fields, malformed nested shapes, weights that do not total 100, or rule-incompatible limit values.
  - **GitHub Actions run #602:** [build and APK artifact](https://github.com/Webster-02/Superior-Student/actions/runs/39635387870) — JavaScript validation, clean, unit tests, APK assembly, APK verification, and artifact upload all succeeded. Artifact `superior-student-debug-apk`, 40,777,491 bytes, expires 2026-10-28.
  - **Step 4C2** remains pending: connect the read-only reader to the cache store, enforce the six-hour refresh policy, and allow stale-cache fallback only for identified connectivity failures. Keep authorization, malformed-data, and programming errors visible instead of hiding them behind cached data.

- **Part 5 — pure ranking scorer and unit tests:** not started.
- **Part 6 — Firestore error mapping:** not started.

## Exact next step

Steps 4A, 4B1, 4B2 and 4C1 are complete, and their implementation builds, unit tests, APK verification, and artifact uploads passed. The next small step is Step 4C2: connect `MarketConfigReader`, `MarketConfigCacheStore`, and `MarketConfigCachePolicy` into a config provider with a six-hour refresh policy and connectivity-only cached fallback. Do not start Step 4C2 until the user says `continue`.
