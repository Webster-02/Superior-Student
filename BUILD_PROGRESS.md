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
- **Part 5 — pure ranking scorer and unit tests:** complete on isolated branch `feature/market-step5-ranking`. Implementation commit `facdeb5e0c6561574ade3c68ee27ebf496960cb6` was verified by GitHub Actions run #3 (`39640818969`): JavaScript validation, clean, all unit tests, APK assembly, APK verification and artifact upload passed. Eight focused ranking tests passed. Earlier CI attempts #1 and #2 exposed `Timestamp.toMillis()` compilation errors; these were fixed in the current branch before run #3. The final checkpoint run #5 (`39641417000`) passed on commit `dca6ff497b5e0267f316c0db92fcd449d6f7a2c0`; unit tests, JavaScript validation, APK assembly, APK verification, and artifact upload all passed.
  - Added `MarketRankingScorer.kt` as a side-effect-free scorer for already-loaded items. It performs no Firestore reads or writes and does not apply authorization/visibility policy.
  - Offer scoring follows the spec: 30-day newness decay, cold-seller quality prior of 4.2 over five prior ratings, skill/course match signal, config-supplied weights, pinned-first ordering, then score and time. Pinned time is an optional signal; when unavailable the renewal timestamp is used as a deterministic fallback.
  - Task ranking sorts pinned first, then skill/course match, then creation time, and excludes tasks whose deadline or expiry timestamp has passed.
  - Repeated sellers are separated by selecting the next different seller when one is available; this is best-effort when the page only contains one seller.
  - Added `MarketRankingScorerTest.kt` for weighted scoring, cold-seller prior, newness boundary, pinned order, pin-time priority, score order, repeat-seller handling, expired tasks, and invalid weights.
  - No UI integration, repository changes, rule/index changes, deployment or merge was performed as part of this step.
- **Part 6 — Firestore error mapping:** implemented on isolated branch `feature/market-step6-errors`; final CI checkpoint pending. Added `MarketErrorMapper.kt`, a pure mapper from Firestore failure codes and known network/cancellation/validation exceptions to safe user-facing guidance. It distinguishes sign-in requirements from permission denial, identifies offline/timeout failures, avoids echoing raw exception text, and indicates whether a user-requested retry is appropriate. It does not auto-retry, alter cached data, bypass Firestore rules, or wire into screens/repository behavior yet. Added `MarketErrorMapperTest.kt` for auth/permission, connectivity, not-found/duplicate, invalid/precondition, contention/limits, unsupported/cancelled/unknown, wrapped causes, safe messages and common exceptions.

## Exact next step

Run the Market data GitHub Actions verification on `feature/market-step6-errors`. Fix only error-mapper compilation/test failures. Keep the mapper standalone; do not wire it into repository methods or screens until this step is green and the user approves the next step.
