# U2U Market Step 1 Build Progress

**Branch:** `feature/market-step1`
**Base:** `review/market-rules` at `aab0795a1d67b0e8d99f52bf3e815abcd0aa85b3`
**Build status:** Parts 1–3 and Part 4 Steps 4A, 4B1, 4B2, 4C1 and 4C2 are complete and green in runs #596, #598, #600, #602 and #607.
**Safety:** Do not modify `firestore.rules`, the protected top-level `tests/`, or `firestore.indexes.json`; do not change existing U2U chat/friend/Nearby logic or existing view IDs. No merge or Firebase deployment.

## Baseline gate

- Verified rules baseline commit: `6249b60165cbe96d2aa2102e22d22ac99469c1c1`.
- Latest pre-code confirmation run #22 on restore commit `2a136f1cc8478a27b0ef101a7a3c0ec8bde86ad9`: static 37 passed / 0 failed; Emulator 65 passed / 0 failed; expression-ceiling diagnostics 0; overall PASS.
- The protected Firestore-rules workflow and indexes remain unchanged. No live Firebase rules deployment has been performed.
- Required spec documents and the repository code were reviewed before implementing the pure ranking step.

## Parts and checkpoints

- **Part 1 — typed models and converters:** complete. Implementation and tests passed in GitHub Actions run #580.
- **Part 2 — MarketRepository:** complete. Repository transaction/policy tests and APK build passed in run #587.
- **Part 3 — rule-constrained Market query builders:** complete. Query-plan tests and APK build passed in run #595.
- **Part 4 — config and persistent six-hour cache:** complete.
  - **4A:** pure six-hour cache policy; run #596 passed.
  - **4B1:** versioned codec, 64 KiB payload cap, malformed/version validation; run #598 passed.
  - **4B2:** app-private persistent cache store; run #600 passed.
  - **4C1:** read-only reader of `config/market`; run #602 passed.
  - **4C2:** config provider connecting the reader, cache and policy; fresh cache bypasses remote read, stale cache refreshes remotely, and stale fallback is limited to recognised connectivity/timeouts. Authorization and invalid-data errors do not fall back to stale cache. Provider policy tests passed in run #607.
  - Final Part 4 documentation checkpoint run #608 passed. Artifact `superior-student-debug-apk`, 40,779,421 bytes, SHA-256 `2fd9f725ca2ea4d1467d4d429de4d98148c814c3376d318d749a789298903616`, expires 2026-10-28.
- **Part 5 — pure ranking scorer and unit tests:** complete on isolated branch `feature/market-step5-ranking`. The final checkpoint run #5 (`39641417000`) passed on commit `dca6ff497b5e0267f316c0db92fcd449d6f7a2c0`: unit tests, JavaScript validation, APK assembly, APK verification, and artifact upload all succeeded.
  - Added `MarketRankingScorer.kt` as a side-effect-free scorer for already-loaded items. It performs no Firestore reads or writes and does not apply authorization/visibility policy.
  - Offer scoring follows the spec: 30-day newness decay, cold-seller quality prior of 4.2 over five prior ratings, skill/course match signal, config-supplied weights, pinned-first ordering, then score and time. Pinned time is an optional signal; when unavailable the renewal timestamp is used as a deterministic fallback.
  - Task ranking sorts pinned first, then skill/course match, then creation time, and excludes tasks whose deadline or expiry timestamp has passed.
  - Repeated sellers are separated by selecting the next different seller when one is available; this is best-effort when the page only contains one seller.
  - Added `MarketRankingScorerTest.kt` for weighted scoring, cold-seller prior, newness boundary, pinned order, pin-time priority, score order, repeat-seller handling, expired tasks, and invalid weights.
  - No UI integration, repository changes, rule/index changes, deployment or merge was performed as part of this step.

- **Part 6 — Firestore error mapping:** complete as a standalone utility on `feature/market-step6-errors`. Final GitHub Actions checkpoint run #7 (`39642945303`) passed on commit `ee04a2102c836f98460bc4d2032ebf84f694d154`: JavaScript validation, clean, unit tests, APK assembly, APK verification and artifact upload all succeeded.
  - Added `MarketErrorMapper.kt`, a pure mapper from Firestore failure codes and known network/cancellation/validation exceptions to safe user-facing guidance. It distinguishes sign-in requirements from permission denial, identifies offline/timeout failures, avoids echoing raw exception text, and indicates whether a user-requested retry is appropriate.
  - Added `MarketErrorMapperTest.kt` with nine focused test methods covering auth/permission, connectivity, missing/duplicate items, invalid/precondition errors, contention/limits, unsupported/cancelled/unknown codes, wrapped causes, common exceptions and safe messages.
  - The mapper does not auto-retry, alter cached data, bypass Firestore rules, or wire into screens/repository behavior. No changes were made to `firestore.rules`, `firestore.indexes.json`, the protected top-level `tests/`, existing U2U chat/friend/Nearby logic, or existing view IDs.
## Exact next step

- **Part 6B — one config-display error path:** complete on `feature/market-step6-errors`. Final implementation checkpoint run #11 (`39644704829`) passed on commit `03c7ca1e9d6ae536ea6bf54deb1376c36d91c8f5`: unit tests, JavaScript validation, APK assembly, APK verification, and artifact upload all succeeded. The dedicated display-state tests passed.
  - Added `MarketConfigProvider.loadForDisplay(...)`: on remote failure it retains the config already rendered by the caller and returns a `MarketMappedError`. The caller should offer Retry only when `retryable == true`; permission/auth and invalid-config errors do not offer Retry.
  - Successful valid remote config replaces the displayed value and clears the mapped error. A missing/invalid successful config does not use stale fallback; it preserves the displayed value with a non-retryable `INVALID_CONFIG` message.
  - The underlying six-hour cache policy remains unchanged: stale cache fallback is still limited to recognized connectivity failures. Permission and invalid-data failures do not use stale cache, and failed loads do not clear or overwrite persisted cache.
  - Added `MarketConfigProviderDisplayTest.kt` for retained display/cache after permission failure, offline retry guidance, successful refresh, missing/invalid config, and first-load offline behavior.
  - No Market screen/repository integration beyond this single provider display path, no rule/index changes, no Firebase deployment or merge.
  
## Part 6C — create-task mapped completion path

- **Status:** complete and verified on commit `5353e5134fe16e72ac7a987a84dab4544a9651cc`. GitHub Actions run #14 (`39647165787`) passed unit tests, JavaScript validation, APK build, APK verification, and artifact upload. Five focused adapter tests passed.
- Added the opt-in `MarketRepository.createTaskWithMappedError(...)` API. The original `createTask(...): Task<String>` remains unchanged for existing callers.
- The new path returns `MarketOperationResult<String>`: success contains the task ID; failure contains a safe `MarketMappedError`. Offline errors are retryable; permission and input errors are not. Synchronous state/precondition failures use non-retryable guidance without exposing exception text.
- This adapter does not retry, mutate the cache, skip Firestore rules, or change the atomic create-task transaction. No existing UI call site is redirected in this step.
- Updated the focused Market workflow path filters so changes to the repository adapter and its tests trigger CI.
- No changes to `firestore.rules`, `firestore.indexes.json`, the protected top-level `tests/`, existing U2U chat/friend/Nearby behavior, or existing view IDs. No Firebase deployment or merge.

## Exact next step

## Part 6D — create-offer mapped completion path

- **Status:** complete and verified on commit `254802a68631b83f8250db8fddeaa1ab55934cc6`. GitHub Actions run #16 (`39648819689`) passed all unit tests, JavaScript validation, APK build, APK verification, and artifact upload.
- Added opt-in `MarketRepository.createOfferWithMappedError(...)`, mirroring the verified mapped-result path for task creation.
- The original `createOffer(...): Task<String>` remains unchanged for existing callers. The new path reuses the already tested `MarketOperationTaskAdapter`; no offer write logic or transaction contents were changed.
- No existing UI call site is redirected in this step. No rules/index, protected test, existing U2U feature, or view ID changes; no Firebase deployment or merge.

## Exact next step

Part 6D is complete and verified. Stop for user review before mapping any additional Market operation; do not broaden integration until the user explicitly approves.


## Market UI reference rollout — Step UI-01

- **Scope:** first visual increment for the user-provided `pachattar_market_final(1).zip` reference set.
- Added a Market entry tile between Nearby share and Your space on the existing U2U Home screen, matching the reference's peach surface, 24dp corner radius, 44dp icon tile, briefcase icon, Poppins title/subtitle, and chevron.
- Added Paper/Night resource values for the tile colours so the component follows the app's existing appearance selection.
- This is a **visual-only increment**: the tile is not yet connected to the Market Requests destination, and the rest of the Home screen has not yet been certified pixel-matched. The next screen step should implement the reference Requests screen and wire this entry to it.
- Reference inventory from the uploaded ZIP: 32 student screens in both light and dark themes, plus 9 light-theme desktop admin pages. The HTML files are static layout references; behavior and Firebase data paths must be wired separately.
- No existing U2U auth, friend, chat, Nearby-transfer logic, view IDs, Firestore rules/indexes, or Firebase deployment changed.
- **Verification:** UI-01 passed in GitHub Actions run #18 (`39654835518`), including unit tests, debug APK build, APK verification and artifact upload.
- The Market workflow includes `feature/market-ui-reference` and monitors the Home layout/resource files.


## UI-02 — Requests reference screen

- **Status:** complete and verified on commit `d99e824b1b06806a6a39d0264f32cc714b084bda`. Run #20 (`39672533073`) passed unit tests, JavaScript validation, clean, debug APK build, APK verification and upload. The initial run #19 identified two unsupported `ScrollView.LayoutParams` references; those were corrected to `FrameLayout.LayoutParams` and the full run then passed.
- Added a dedicated native Market Requests Activity and connected the Home Market card to open it. Existing Student Connect routes and view IDs are otherwise unchanged.
- Reproduced the reference structure and tokens: Paper/Night colours, Poppins, header/back/search/menu, segmented Requests/Offers tabs, category chips, sort control, request cards, deadline/proposal row, author row, “Matches you” badge, and floating “Post a task” button.
- The three listings intentionally mirror the ZIP's **visual reference fixtures**, not live Firestore data. Search, category filtering and local sorting work over those fixtures. Cache/feed/pagination, detail, Offers, menu destinations and task posting remain later steps.
- No Firestore rules/index changes, deployment or merge. UI-01 build #18 passed and produced the debug APK.
- Workflow path filters now include the Market Activity and its unit tests.

## Exact next step

Stop for user review. After explicit approval, implement student reference screen 03 Offers as a separate step. Keep live Firestore data/cache/rules integration separate and present any required rule changes for review before writing them.


## UI-03 — Offers reference screen

- **Status:** complete and verified on commit `deee30156e387519a95fba6d2c2d33b0252d8c73`. GitHub Actions run #23 (`39674447312`) passed unit tests, JavaScript validation, clean, APK build, APK verification, and artifact upload.
- Added a dedicated native Offers screen using the same Market header and Paper/Night tokens; the existing Requests/Offers segment now opens this screen.
- Matches the reference's All / Notes / Tutoring / Design / Typing chips, seller avatar/name, star rating + count, verified-student label, pastel category chip, title/description, price or Swap, dark Message button and lime Post an offer CTA.
- Uses the reference ZIP's three listing samples as visual fixtures only. Live Firestore offers, authoritative profile verification/ratings, actual chat and offer-posting destinations remain later steps.
- Added unit tests for category filtering, search, rating sort and price sort. No Firestore rules/index changes or Firebase deployment.

## Exact next step

UI-03 is complete. Stop for user review before implementing reference screen 04 Menu or connecting live Firestore offers. Do not expand into the next screen until the user explicitly approves.


## UI-04 — Market menu bottom sheet

- **Status:** complete and verified on commit `674a51ba55d4f815990c65c768f32255700e21a7`. GitHub Actions run #25 (`39676655510`) passed unit tests, JavaScript validation, clean, APK build, APK verification and artifact upload.
- Inspected the ZIP's light/dark `04_menu` references. Replaced the generic PopupMenu on both Requests and Offers with one shared, reference-style dimmed bottom sheet.
- Reproduced the grab handle, "Market menu" heading, bordered rounded list card, five icon tiles and subtitles: My tasks, My offers, Notices (badge 2), Help and safety, Market settings. Uses existing Market Paper/Night resources and Poppins.
- Menu rows currently dismiss and provide honest in-progress feedback; the destination screens are separate steps, so no placeholder navigation is presented as complete.
- Added unit tests for reference row order/labels, Notices badge/subtitle, and non-empty helper text. No Firestore rules/index changes, deployments or merges.
- UI-03 code checkpoint passed in run #23 (`39674447312`) with APK artifact.

## Exact next step

UI-04 is complete. ZIP numbering confirms screen 05 is Task detail (helper), while My Tasks is screen 15. Screen 05 has been approved for implementation in this step.


## UI-05 — Task detail (helper view)

- **Status:** implementation committed; CI pending.
- Corrected the numbered reference order: `05_detail_helper` is the task detail screen for a prospective helper; `15_mytasks` is the separate My Tasks screen.
- Added a native detail screen with back/bookmark/report header, category/course chips, large title, poster avatar/name/rating/verified label, three pastel Budget/Deadline/Proposals cards, detail text, skills chips, locked-contact notice and a fixed lime Send proposal CTA.
- Tapping a Requests card now opens detail and passes its sample listing fields. The exact Design reference uses the ZIP's extended description and 24 Oct / 3 days / 4 of 15 values.
- Bookmark is preview-only and not persisted. Report and proposal submission are not connected to live services yet; taps state this clearly. No Firestore rules/indexes, chat, or Nearby logic changed.
- Added three focused policy tests for reference metrics/tags, generic request mapping and lock message/proposal bounds.

## Exact next step

Run GitHub Actions for UI-05. Fix only compile/test issues in the new detail screen. Verify the APK artifact and stop for review before screen 06 (poster detail) or real data wiring.
