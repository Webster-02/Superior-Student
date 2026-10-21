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

- **Status:** implementation added; CI verification pending.
- Added opt-in `MarketRepository.createOfferWithMappedError(...)`, mirroring the verified mapped-result path for task creation.
- The original `createOffer(...): Task<String>` remains unchanged for existing callers. The new path reuses the already tested `MarketOperationTaskAdapter`; no offer write logic or transaction contents were changed.
- No existing UI call site is redirected in this step. No rules/index, protected test, existing U2U feature, or view ID changes; no Firebase deployment or merge.

## Exact next step

Run the Market data and APK workflow on this commit. Verify all unit tests, APK build/verification, and artifact upload. After verification, stop for review before mapping another operation.
