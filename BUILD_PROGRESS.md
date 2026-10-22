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

- **Status:** complete and verified on commit `7e8680c2925c1cd9c78369553a28495a0397c66e`. GitHub Actions run #27 (`39678143928`) passed unit tests, JavaScript validation, clean, APK build, APK verification and artifact upload.
- Corrected the numbered reference order: `05_detail_helper` is the task detail screen for a prospective helper; `15_mytasks` is the separate My Tasks screen.
- Added a native detail screen with back/bookmark/report header, category/course chips, large title, poster avatar/name/rating/verified label, three pastel Budget/Deadline/Proposals cards, detail text, skills chips, locked-contact notice and a fixed lime Send proposal CTA.
- Tapping a Requests card now opens detail and passes its sample listing fields. The exact Design reference uses the ZIP's extended description and 24 Oct / 3 days / 4 of 15 values.
- Bookmark is preview-only and not persisted. Report and proposal submission are not connected to live services yet; taps state this clearly. No Firestore rules/indexes, chat, or Nearby logic changed.
- Added three focused policy tests for reference metrics/tags, generic request mapping and lock message/proposal bounds.

## Exact next step

UI-05 is complete and verified. Stop for user review before implementing screen 06 (poster detail). My Tasks remains reference screen 15. Do not start another screen or connect live writes until explicitly approved.


## UI-06 — Task detail (poster/owner view)

- **Status:** complete and verified on code commit `db690b571750d324826fa911af2475e986739a8a`. GitHub Actions run #29 (`39679181748`) passed unit tests, JavaScript validation, clean, APK build, APK verification and artifact upload.
- Inspected both `06_detail_poster_light.png` and `06_detail_poster_dark.png` plus their HTML references.
- Added an owner-view detail screen with "Your task", Open status chip, edit affordance, Design/BSOPM tags, poster profile/rating, three pastel Budget/Deadline/Proposals cards, full task detail, Canva/Photoshop/Layout tags, overlapped three-person proposal preview card (4 proposals, 1 shortlisted), and fixed Cancel task / View proposals buttons.
- The My tasks item in the menu temporarily opens this exact owner-view fixture so the screen can be inspected now; when screen 15 My Tasks is implemented, that route should be updated to the list and task rows should open this owner detail.
- Edit, cancellation and proposal-list actions are preview-only and explicitly do not save/cancel anything. No Firestore write, rule/index change, merge, deployment or existing chat/Nearby logic change.
- Added three focused tests for owner/status fields, metric counts and detail/skills content.

## Exact next step

UI-06 is complete and verified. Stop for user review before implementing reference screen 07 (Post task step 1). Do not start the next screen until the user explicitly approves.


## UI-07 — Post a task, step 1 (Details)

- **Status:** complete and verified on commit `04090cc221cd7beeb90fd8189ce79bd267a395c7`. GitHub Actions run #34 (`39680950379`) passed tests, JavaScript validation, clean, APK build, APK verification and artifact upload. The earlier run #31 exposed compile errors; those were fixed before run #34.
- Inspected `07_post1_light.png`, `07_post1_dark.png`, and both HTML references.
- Added the reference first step with the 3-part progress line, back/title/subtitle, editable title, exact six categories, optional searchable-course style field, multiline description with a live character counter, and fixed lime Next CTA. Paper/Night palettes and Poppins are used.
- The Requests screen's Post a task CTA now opens this step. Input validation rejects blank/too-short titles, unknown category values and descriptions outside 20–500 characters; course remains optional. The visible counter shows actual characters from the supplied description (129/500); the ZIP mockup label says 142/500 despite the displayed text containing 129 characters, so code avoids a hard-coded inaccurate count.
- Next only validates and reports readiness; step 2 is not implemented or navigated to in this commit. No Firestore save, Firebase rules/index updates, merge/deployment, or changes to chat/Nearby logic.
- Added five focused JVM tests for sample validity, required title/category, description limits, optional course and counter behavior.

## Exact next step

UI-07 is complete and verified. Stop for user review before implementing screen 08 (Post task step 2). Do not wire any Firestore write or continue the next screen without explicit approval.


## UI-08 — Post a task, step 2 (Budget and time)

- **Status:** complete and verified on commit `46cb076763fb136a2af2d9121b4ecdf9761f31cc`. Run #37 (`39682920691`) passed unit tests, JavaScript validation, clean, debug APK build, APK verification and artifact upload. Initial run #36 found an `EditText` setter typo; fixed before the successful run. Artifact `superior-student-debug-apk`, 40,886,595 bytes, SHA-256 `5837aeceb1c9e4ef6bf91895e4d70d746321a078bf9cd6cec60355082f38ebb1`, expires 2026-10-28.
- Inspected `08_post2_light.png`, `08_post2_dark.png` and the light/dark HTML references.
- Added the second-step layout with two completed progress segments, Free help / Fixed / Negotiable choices, an amount field with PKR prefix, a date picker that defaults to the reference date (24 Oct 2026 while it is not in the past), selectable skill chips, the money-safety notice and fixed Back / Next actions.
- Step 1's validated Next now opens this screen and passes title, category, optional course and description as intent extras; the Back action returns to the still-open Step 1 form. No Step 3 navigation or task save is performed yet.
- Fixed amount must be a positive whole PKR amount; Free help and Negotiable may omit amount. Deadline may be today or later. Skills must be known, unique and no more than five; three reference skills are selected by default. Figma is offered as a fifth option so the displayed “up to 5” limit is usable.
- Added six focused JVM tests for reference values, budget/amount constraints, optional amount modes, date checks, and skill limits.
- No Firestore writes, rules/index edits, Firebase deployment, merge or existing chat/Nearby changes.


## UI-09 — Post a task, step 3 (Review)

- **Status:** implemented and CI verified.
- Inspected the exact `09_post3_light.png`, `09_post3_dark.png`, and the matching light/dark `09_post3.html` references from `pachattar_market_final(1).zip`.
- Implemented the review screen with the existing Market Paper/Night semantic colours, Poppins typography, three-step progress indicator, task preview card, budget/deadline/proposal summary, posting-rules acknowledgement, and Back / Post task actions.
- Connected Step 2 Next to Step 3 using internal Intent extras for title, category, course, description, budget type, amount, deadline and selected skills. Back closes Step 3 and reveals the still-open Step 2 form, preserving its values.
- Post task validates all prior-step fields and the rules acknowledgement locally, then displays a preview-only notice. **No Firestore task creation or publishing is connected.**
- Corrected the compile failure in `MarketPostTaskStep3Activity.kt` by converting the corner radius to physical pixels using `radius * resources.displayMetrics.density`.
- **Successful verification:** GitHub Actions run [#40 / run ID 39688515507](https://github.com/Webster-02/Superior-Student/actions/runs/39688515507), commit `54cc6c880d430999a300f7853c3bd08cc3a18dae`. JavaScript validation, clean, JVM unit tests, debug APK build, APK verification and artifact upload all passed.
- **Verified APK:** `app-debug.apk`, 66,900,937 bytes; SHA-256 `34845041422631e825ad6ae206ba674df2bef13aa743f7a0f756fcbf138ee7e0`. GitHub Actions artifact archive: 40,900,692 bytes; SHA-256 `66807b2a7513f92b72c46217d9db372cc42535012d3a9b2c78697bd55ee4fad8`. Artifact expires October 28, 2026.
- Scope remains limited to UI-09, Step 2 handoff, manifest, focused workflow paths and this progress note. No Firestore rules/indexes, protected top-level tests, Firebase deployment, branch merges or unrelated chat/Nearby functionality were changed.

## UI-10 — Posting rules bottom sheet

- **Status:** implemented and CI verified.
- Inspected the exact `10_rules_sheet_light.png`, `10_rules_sheet_dark.png`, and corresponding light/dark HTML references from `pachattar_market_final(1).zip`.
- Added the Screen 10 sheet over the existing Screen 09 review activity, matching the reference’s rounded top corners, dimmed background, handle, title, Allowed / Not allowed groups, rule copy, status icons, warning note, and full-width lime **I understand** action. It uses existing Paper/Night semantic colours and Poppins.
- Tapping the acknowledgement sentence opens the rules sheet. The checkbox retains its independent toggle. Tapping **I understand** confirms acknowledgement and dismisses the sheet; outside/back dismissal does not falsely confirm it.
- Task publishing remains disconnected and the sheet performs **no Firestore reads or writes**. No rules/index changes, deployment, merge, or unrelated U2U/chat/Nearby edits.
- **Successful verification:** GitHub Actions run [#42 / run ID 39737133611](https://github.com/Webster-02/Superior-Student/actions/runs/39737133611), code commit `a9b8bf4885ad8e4e3d7d22ff00ca2d1640a754a8`. JavaScript validation, clean, JVM unit tests, debug APK build, APK verification and artifact upload all passed.
- **Verified APK:** `app-debug.apk`, 66,900,947 bytes; SHA-256 `a1d0e2add59903da3ade079714b56e11aebc7dc2c0ca33248df77232ae24445f`. Artifact archive: 40,905,401 bytes; SHA-256 `5f4dd2dd12e229b1914bdf649c8a3e15bef182a9011b6ff3efbabbd53f72cb94`; expires October 29, 2026.
- Implementation is isolated to `MarketPostTaskStep3Activity.kt` plus this progress note. No manifest change was needed because the rules sheet is a dialog inside the existing review Activity.

## UI-11 — Send proposal form

- **Status:** implemented and CI verified.
- Inspected the exact `11_proposal_light.png`, `11_proposal_dark.png`, and the corresponding light/dark HTML references from `pachattar_market_final(1).zip`.
- Added the native proposal form matching the reference structure: back/header and task subtitle; task summary card; editable message with a live 400-character counter; side-by-side price and delivery fields; quick-pick delivery chips (1, 2, 3, 5 and 7 days); blue guidance panel; and fixed lime Send proposal action. Existing Market Paper/Night semantic resources and Poppins are reused.
- Connected the existing prospective-helper **Send proposal** action on the task detail screen to open the proposal form and pass the task title, category, budget, deadline and poster name. The form defaults to the reference message, Rs. 1,200 and 2 days for the Design fixture; the displayed counter reflects the actual sample message length (106/400) rather than copying the mockup's inaccurate 108 count.
- Quick-pick chips update the editable delivery field. Local validation checks a non-empty message up to 400 characters, a positive whole PKR amount, and delivery from 1 to 30 days. **The final action validates locally and displays preview-only feedback; it does not send or write a proposal to Firestore.**
- **Successful verification:** GitHub Actions run [#44 / run ID 39738584685](https://github.com/Webster-02/Superior-Student/actions/runs/39738584685), code commit `dee6458416be7490e7992450e08a33c86b0d0b36`. JavaScript validation, clean, all JVM unit tests (including seven proposal policy tests), debug APK build, APK verification and artifact upload all passed.
- **Verified APK:** `app-debug.apk`, 66,917,351 bytes; SHA-256 `2f696751a692398038667f31d998ad311f309a79c7f6ffcc944655a06ae7f612`. Artifact archive: 40,917,885 bytes; SHA-256 `2fda049b2ecb0c99df56aa31b74081e465b87ef6daaf733c605f920debd71f99`; expires October 29, 2026.
- Scope is limited to the new proposal Activity, pure UI policy, custom icons, focused policy tests, detail-screen CTA handoff, manifest registration, workflow path filters and this progress entry. No Firestore rules/index changes, protected top-level tests, deployment, merge, or unrelated U2U/chat/Nearby changes.

## UI-12 — Proposals list (poster side)

- **Status:** implemented and CI verified.
- Inspected `12_proposals_light.png`, `12_proposals_dark.png`, and the matching light/dark HTML references from `pachattar_market_final(1).zip`.
- Added the poster-side Proposals screen with the reference header and “4 of 15 · Poster for health camp” subtitle; horizontal All 4 / Shortlisted 1 / Cheapest / Top rated filters; proposal cards for Zain Malik, Noor Fatima and Ayesha Khan; pastel initials avatars; rating/age metadata; PKR prices; shortlist badge; short message preview; and Decline / Shortlist / Accept actions. Uses existing Paper/Night semantic colours and Poppins.
- Connected both entry points on the owner task screen (proposal summary card and fixed View proposals CTA) to open the dedicated proposals screen.
- Filter chips operate on local reference fixtures: cheapest sorts PKR ascending, top-rated sorts rating descending, and shortlisted filters the locally shortlisted previews. The shortlist button toggles only in-memory preview state. Decline and Accept show clear preview-only dialogs.
- **No proposal accept/decline/shortlist operation is persisted or sent.** The screen uses only local visual fixtures; no Firestore reads or writes, rules/index edits, Firebase deployment, or merges.
- Added seven focused JVM policy tests for reference data, filter order, cheapest/top-rated sorting, shortlist filtering/toggling and PKR formatting.
- The initial run #46 surfaced a Kotlin compile error on the filter bar's layout margin property; corrected `marginTop` to `topMargin` in commit `132295261e7a875d412a6ccba6e97db8719e008a`.
- **Successful verification:** GitHub Actions run [#47 / run ID 39740936003](https://github.com/Webster-02/Superior-Student/actions/runs/39740936003), fix commit `132295261e7a875d412a6ccba6e97db8719e008a`. JavaScript validation, clean, all JVM unit tests, debug APK build, APK verification and artifact upload passed.
- **Verified APK:** `app-debug.apk`, 66,917,406 bytes; SHA-256 `0c01dac185a492302bc21f88add16954b981b2f19aedfee96768405ddeb99bfd`. Artifact archive: 40,934,096 bytes; SHA-256 `870143cd2ee7a73317d561aa011d8a09d585e9cf1454adeb7b8fbcee9a9363e9`; expires October 29, 2026.
- Changes are limited to the new Proposals Activity, pure policy, custom icons, unit tests, manifest registration, the existing owner-detail navigation handoff, workflow path filters and this progress record.

## Exact next step

UI-12 is complete and verified. Stop for user review. Do not begin UI-13 or another screen until the user explicitly types `continue`.


## UI-13 — Accept proposal confirmation sheet

- **Status:** implemented and CI verified on code commit `3a513acfdbf9500d995351e827110daab59f2b1a`. GitHub Actions run [#50 / run ID 39743704895](https://github.com/Webster-02/Superior-Student/actions/runs/39743704895) passed JavaScript validation, clean, JVM unit tests, debug APK build, APK verification and artifact upload.
- Inspected the exact `13_accept_light.png`, `13_accept_dark.png`, and matching `13_accept.html` references from `pachattar_market_final(1).zip`.
- Added the acceptance confirmation as a dimmed, bottom-aligned sheet launched by each proposal card's **Accept** button. It includes the rounded handle/header, price and delivery summary, three consequence reminders, shield-style money-safety note, lime **Accept and open chat**, and bordered **Not now** actions. It reuses existing Market Paper/Night semantic colours and Poppins.
- The reference fixture identifies Zain's delivery as 2 days. Other fixture proposals show **Confirm in chat** instead of inventing unsupported delivery estimates.
- **Live acceptance and chat opening remain intentionally disconnected.** Tapping **Accept and open chat** explicitly confirms that no proposal was accepted and no chat was opened; **Not now** simply dismisses the sheet. This step does not write Firestore or alter task/proposal state.
- Added focused policy coverage for reference delivery labels, consequence copy and money-safety reminder.
- **Verified APK:** `app-debug.apk`, 66,933,785 bytes; SHA-256 `fbadf478c4dd9f5d29d449544bcb178697120c8964571ad1e28a6a8c35832368`. Workflow artifact archive: 40,936,950 bytes; SHA-256 `7a9a7473e9bedc9986eef4fdd951ae870ad066781ab309d0a74a8483cb72f5bc`; expires October 29, 2026.
- Scope is limited to the existing Proposals Activity, its policy/test and this progress entry. No manifest, Firestore rules/indexes, protected top-level tests, deployment, merge, or unrelated login/chat/Nearby logic changed.

## UI-14 — Task chat reference screen

- **Status:** implemented and CI verified on code commit `45ff31d5fdb99af03d517f9b2f294ca07d5e9562`. GitHub Actions run [#52 / run ID 39766111635](https://github.com/Webster-02/Superior-Student/actions/runs/39766111635) passed JavaScript validation, clean, JVM unit tests, debug APK build, APK verification and artifact upload.
- Inspected the exact `14_chat_light.png`, `14_chat_dark.png`, and matching light/dark `14_chat.html` references from `pachattar_market_final(1).zip`.
- Added a separate native Market Task Chat screen matching the header/avatar/flag action, assigned task card, budget/deadline, View task and Mark complete actions, accepted-event chip, three message bubbles with timestamps and delivery checks, and bottom attachment/message/send composer. Uses the existing Market Paper/Night resource palette and Poppins.
- Added a preview-only route from Screen 13's **Accept and open chat** action. A confirmation explicitly says this is sample chat preview only and that proposal acceptance, task changes, and message sending are not performed.
- Starter messages and the task card are local reference fixtures. Messages typed or attachment actions are local preview behavior only; this screen does not use Firestore, does not invoke the existing Student Connect chat, and does not change Nearby transfer or chat logic. View task, Mark complete and Report chat show explanatory preview dialogs.
- Added policy tests for fixture content, name initials, PKR labels, input trimming/length validation, and append-only preview messaging.
- **Verified APK:** `app-debug.apk`, 66,933,824 bytes; SHA-256 `716e5fb14ea86c6ebc2adbd6bf6f53aa5bd09d3e9f8b38503635e8725d1b399d`. Workflow artifact archive: 40,950,366 bytes; SHA-256 `5d70c4745f87e393ee22f9c82490b194bebaadf9a7c8f48bafe51b1f2b5b0fb3`; expires October 29, 2026.
- Scope is limited to the new Market chat Activity/policy/icons/tests, Screen 13 preview navigation, manifest, workflow path filters and this progress record. No Firestore rules/indexes, protected top-level tests, deployment, merge, login, existing chat logic or Nearby logic changed.

## UI-15 — My tasks (posted and applied)

- **Status:** implemented and CI verified on fix commit `0737b6291fe4e4f2ec32fd29ed760b0095348743`. GitHub Actions run [#55 / run ID 39779307403](https://github.com/Webster-02/Superior-Student/actions/runs/39779307403) passed JavaScript validation, clean, JVM unit tests, debug APK build, APK verification and artifact upload.
- Inspected the exact `15_mytasks_light.png`, `15_mytasks_dark.png`, and matching light/dark `15_mytasks.html` references from `pachattar_market_final(1).zip`.
- Added the native My tasks screen with back/header and “Posted and applied” subtitle, segmented **Posted 2 / Applied 3** tabs, and the four visible Applied cards from the supplied reference: Accepted, Shortlisted, Sent and Declined. Category/status chips, card corners, spacing, Paper/Night palette and Poppins follow the reference.
- The initial state is **Applied** to match the screenshot. Tabs switch between four supplied Applied fixture cards and two local Posted fixture cards; the applied count remains “3” because that is the exact reference label even though four sample cards are visible.
- Updated the My tasks entry in both Requests and Offers menu sheets to open this list instead of jumping directly to the owner-detail fixture. The first Posted item opens the existing owner task-detail preview. The Accepted Applied item opens Screen 14 after a clear preview confirmation; other card actions explain that they are local reference data.
- **No live Market/Firestore data, real acceptance, task updates, outgoing messages, or chat writes are connected.** Screen 15 navigation only opens reference previews; existing Student Connect chat and Nearby transfer code remain untouched.
- Added focused policy tests for tab labels/counts, visible item order and copy, accepted-chat preview routing, and existing owner-detail routing.
- The initial run #54 failed compilation because the new Activity omitted its `color(resourceId)` helper; the missing helper was added and a redundant row spacer removed in fix commit `0737b6291fe4e4f2ec32fd29ed760b0095348743`.
- **Verified APK:** `app-debug.apk`, 66,950,247 bytes; SHA-256 `9e2cdf87ff96b23b7a77c85dd05b4771496cd7dd3e710da1067c162d0610c44d`. Workflow artifact archive: 40,962,304 bytes; SHA-256 `3bc0a66143707bc64d838b065d87da2314bd4b9f6dd73a3ade3c8c48360edae0`; expires October 29, 2026.
- Scope is limited to the new My tasks Activity/policy/tests, Requests and Offers menu navigation, the My tasks menu description, manifest, workflow path filters and this progress record. No Firestore rules/indexes, protected top-level tests, deployment, merge, login, or unrelated chat/Nearby logic changed.

## UI-16 — Complete task and rating

- **Status:** implemented and CI verified on code commit `356c7afcaf7c4c185a1454f6e7bf15ee3eafe8ae`. GitHub Actions run [#57 / run ID 39782120016](https://github.com/Webster-02/Superior-Student/actions/runs/39782120016) passed JavaScript validation, clean, JVM unit tests, debug APK build, APK verification, and artifact upload.
- Inspected exact `16_rate_light.png`, `16_rate_dark.png`, and matching light/dark `16_rate.html` references from `pachattar_market_final(1).zip`.
- Added a native completion/rating page with the back and **Complete task** header, lime check hero, **Task completed** heading, helper-specific prompt, five selectable amber star controls (four selected by default), five togglable experience tags (the first three selected initially), optional multiline comment field, live character count, and pinned lime **Submit rating** action. Uses existing Market Paper/Night semantic resources and Poppins.
- The sample comment is exactly 51 characters, so the visible counter starts at **51/200** instead of reproducing the reference HTML's off-by-one 52/200 label.
- Connected the existing Screen 14 **Mark complete** action to a preview confirmation that can open Screen 16 and pass the helper name/task title. Both the confirmation and Submit rating action explicitly state that task status and ratings are not written.
- Added pure policy tests for reference defaults, star bounds, tag selection/toggling and optional comment limits.
- **Verified APK:** `app-debug.apk`, 66,966,668 bytes; SHA-256 `0b24fc05cc7c8554ec3a6d7137a69d6737019fbf34fe9f1783ed0d8e1fe39d7c`. Workflow artifact archive: 40,968,144 bytes; SHA-256 `78d37445154c217e713f60150dd407ef9eac6034ed20ea523d074ab993d8f69c`; expires October 29, 2026.
- **No live task completion, review submission, Firestore writes, or chat changes** are connected. Student Connect and Nearby transfer logic remain untouched.
- Scope is limited to the new rating Activity/policy/tests, the Screen 14 Mark complete handoff, manifest registration, workflow path filters, and this progress entry. No Firestore rules/indexes, protected top-level tests, deployments, merges, login, or unrelated features were changed.

## UI-17 — Offer detail

- **Status:** implemented and CI verified on code commit `28e0402f801d41f0c1280e8c2e1a624ef0a0f586`. GitHub Actions run [#59 / run ID 39784752359](https://github.com/Webster-02/Superior-Student/actions/runs/39784752359) passed JavaScript validation, clean, JVM unit tests, debug APK build, APK verification and artifact upload.
- Inspected the exact `17_offer_light.png`, `17_offer_dark.png`, and matching light/dark `17_offer.html` references from `pachattar_market_final(1).zip`.
- Added a native Offer detail page with a back/report header, category and course chips, full offer title, seller identity and rating/verification row, price and delivery cards, offer description, blue safety note and fixed **Save / Message** footer. It follows the Market Paper/Night palette, Poppins type and rounded reference-card styling.
- The first Offers fixture (Ayesha Khan's Ocular Anatomy notes) matches Screen 17's exact title, Rs. 300 price, rating 4.9 with 14 reviews, detailed colour-coded note description, delivery mode and safety note. The tutoring/typing fixtures receive their own course/about copy.
- Offer cards open Screen 17. Existing Offers-list Message and Screen 17 Message controls provide a confirmation-gated chat preview. The offer-specific transcript is separate from the unrelated accepted-poster conversation.
- Save toggles local in-memory preview state only. Report and Message are preview-only; the app confirms that no report or message is submitted/sent.
- Added policy tests for reference content, safety copy, rating labels and alternate fixture details, plus a chat test ensuring offer-preview messages stay separate.
- **No live messages, persistent saved-offer state, reports, Firestore writes, Student Connect changes, or Nearby transfer changes** are connected. No Firestore rules/indexes, protected top-level tests, deployment or merge touched.
- Scope is limited to the new Offer detail Activity/policy/icons/tests, Offers-card navigation and chat handoff, contextual sample chat copy, manifest registration, workflow path filters, and this progress record.
- **Verified APK:** `app-debug.apk`, 66,966,715 bytes; SHA-256 `5b24781a7c46935b8582851f8af0299a78638035a7a292785493a26e433c1173`. Workflow artifact archive: 40,979,512 bytes; SHA-256 `f0ca3dc0072fefd07942c918ee15b4c7cfe878608370eb5085efc657dfc5fb48`; expires October 29, 2026.

## UI-18 — Post an offer

- **Status:** implemented and verified. Code commit `d9cad483941135f24997e139ef335111a1511536`; GitHub Actions [run #61 / ID 39790398738](https://github.com/Webster-02/Superior-Student/actions/runs/39790398738) passed JavaScript validation, clean, JVM unit tests, APK build, APK verification, and upload.
- Inspected the reference assets `18_postoffer_light.png`, `18_postoffer_dark.png`, and their matching light/dark HTML from `pachattar_market_final(1).zip`.
- Added a native **Post an offer** form with the reference header, title field, six category chips (Notes, Tutoring, Design, Coding, Typing, Books), three-state price selector (Free, Swap, Rs.), conditional amount field, description with a live 400-character counter, dashed optional cover-photo picker and fixed lime **Post offer** button.
- Reference defaults match the artwork: `Handwritten Ocular Anatomy notes`, Notes, Rs. 300, and `Clean handwriting, colour-coded diagrams. Sample page on request.`. The counter reflects the actual description length (65/400).
- Cover images are selected with the system photo picker, downscaled and JPEG-compressed in memory to at most 150 KiB. The chosen photo and form remain local to this Activity; nothing is uploaded or persisted.
- The Offers screen’s existing **Post an offer** button now opens Screen 18. The submit action validates the form and shows a preview-only dialog explaining that no offer was published.
- Added policy tests for defaults, title/category/amount/description validation, price-mode behavior, character count and photo-size labels. Added the new Activity to the manifest and its changed files to the CI path filters.
- **No live offer publishing or Firestore writes** are connected. No Firebase rules/indexes, protected top-level tests, login, Student Connect or Nearby transfer behavior was changed.
- **Verified APK:** `app-debug.apk`, 66,983,136 bytes; SHA-256 `c1fb0d88b5d72913e3cecb2c6aa64e904b07cefd704ecebea0f931814ef23efe`. Workflow artifact archive: 40,995,497 bytes; SHA-256 `28235413ed960d51ebb005e0d1c641795706edc2b873fc3fa45b66791d645fcc`; expires October 29, 2026.

## UI-19 — My offers

- **Status:** implemented and CI verified on code commit `bda2bd6cacec28fae621d873713a1fa4d043a9a0`; the latest branch checkpoint `d05fa7cd7050938f1c3df050e5d9c846c31e586c` passed GitHub Actions [run #64 / ID 39795771352](https://github.com/Webster-02/Superior-Student/actions/runs/39795771352), including JavaScript validation, clean, JVM unit tests, debug APK build, APK verification, and artifact upload.
- Inspected the exact `19_myoffers_light.png`, `19_myoffers_dark.png`, and corresponding light/dark `19_myoffers.html` references in `pachattar_market_final(1).zip`.
- Added a native **My offers** screen with a back/header block, Active/Paused/Expired segmented tabs and live local counts (2/1/1 by default), rounded offer cards, category and price labels, posting/expiry details, four compact action buttons per offer, the reference blue renewal-policy note, and a fixed lime **Post an offer** button.
- Active fixtures match the reference’s two visible offers: Ocular Anatomy notes at Rs. 300 (expires in 18 days) and typing/report formatting for Swap (renewal available in 3 days). Additional local fixtures populate the Paused and Expired tabs.
- Pause/Resume and Renew update only the in-memory preview state; Delete requires confirmation and removes only the local fixture. Renew is disabled when the local fixture is not eligible. Edit opens Screen 18 prefilled as an edit preview. Post an offer opens Screen 18 without publishing.
- Added My offers navigation to the existing Offers menu and registered the Activity. Added policy tests for reference counts, status transitions, renewal rules, and category palette. The edit-mode handoff uses local intent extras; changing form values still does not persist.
- **No live offers, saved-state updates, deletions, pauses, renewals, or Firestore writes** are connected. No Firebase rules/indexes, protected top-level tests, login, Student Connect or Nearby transfer behavior was changed.
- **Latest verified APK (run #64):** `app-debug.apk`, 66,999,551 bytes; SHA-256 `62c11e6d173d3e40895fe016978a233f2dc125a7393cab81963304d4d1858915`. Workflow archive: 41,011,575 bytes; SHA-256 `3c2e78e06c2166099a80f553c1dc056cd5040796c97a55682aae448ca79f8297`; expires October 29, 2026.

## UI-20 — Report this task

- **Status:** implemented and verified. UI code/fix commit: `d02d94e30ed06bc7a89c88c8b5000d8c98619afc`; GitHub Actions [run #68 / ID 39814451651](https://github.com/Webster-02/Superior-Student/actions/runs/39814451651) logs confirm the JavaScript validation, clean, all 351 JVM unit tests, debug APK build, APK verification, and artifact upload succeeded.
- Inspected the exact `20_report_light.png`, `20_report_dark.png`, and matching `light_20_report.html` / `dark_20_report.html` references from `pachattar_market_final(1).zip`.
- Added a native bottom-sheet overlay for **Report this task**, with the reference order of five reasons, the graded-assignment/exam-work reason selected by default, an in-memory **Also block [task owner]** toggle, rounded top corners, dimmed task-detail background, Paper/Night colors and a lime **Submit report** button.
- Connected only the existing Task detail flag button to open Screen 20. Selecting a reason updates its radio state; toggling block changes only the preview state. Submit displays an explicit preview confirmation.
- **No report is submitted and no user is actually blocked.** No Firestore writes, live report/block actions, Firebase rules/indexes, login, Student Connect, Nearby transfer, or unrelated screens were changed.
- Added `MarketReportUiPolicy` plus unit tests for reference choices, safe fallbacks and truthful preview-only confirmation. Run #66 exposed one fallback-copy assertion; this was corrected in commit `d02d94e`, and run #68 logs then showed the full test suite/build and artifact upload succeeding.
- **Verified debug APK artifact:** `superior-student-debug-apk`, archive size 41,016,095 bytes; archive SHA-256 `da0bb982f33c55342bcc16fa3384e3f817dafae67f80af9dfbccc535a08b0c42`. [Download from run #68](https://github.com/Webster-02/Superior-Student/actions/runs/39814451651). Expires October 29, 2026.

## UI-21 — Loading, empty, offline, error and expired states

- **Status:** implemented and CI verified. Final UI commit: `d82de1a44d9d602bc4f920b8a1bdb3785ea1144f`. GitHub Actions [run #71 / ID 39824255433](https://github.com/Webster-02/Superior-Student/actions/runs/39824255433) passed JavaScript validation, clean, all JVM unit tests, debug APK build, APK verification and artifact upload.
- Inspected the exact `21_states_light.png`, `21_states_dark.png`, and corresponding light/dark `21_states.html` reference files.
- Added a native state-reference gallery matching the Screen 21 structure: Paper/Night colours, offline warning banner, loading skeleton card, Design empty state with **Post a task**, refresh-error card with **Retry**, and compact **Task expired** note. Skeleton lines retain the reference's varied widths.
- **Post a task** opens the existing Screen 07 form. **Retry** displays truthful preview guidance because live feed refresh is not wired in this gallery; saved content is not cleared. The standalone gallery is registered as a non-exported Activity but is not added to the customer-facing Market menu, preserving the Screen 04 menu reference.
- Added `MarketStatesUiPolicy`, `MarketStatesIcons`, and JVM tests for the reference text and order. Added the new files to the existing Market CI path filters.
- **No live Firestore read/write, real cache/network retry, status transitions, Firebase rules/index changes, Student Connect, U2U login, or Nearby transfer changes** were made. Live list/cache/offline behaviour remains a separate integration step.
- **Verified debug APK artifact:** `superior-student-debug-apk`, archive size 41,025,334 bytes; archive SHA-256 `cde9b5c18b16d1b8b466e339479480f99b7a5ec64cf84605392be19adf30dc6f`. [Download from run #71](https://github.com/Webster-02/Superior-Student/actions/runs/39824255433/artifacts/12446790180). Expires October 29, 2026.

## Exact next step

Stop after UI-21 verification for user review. Do not begin another screen or state integration until the user explicitly types `continue`.

## UI-22 — Notices inbox

- **Status:** implemented and CI verified on code commit `8890d764304f4d3d62ca4d0b5f11ffe127bc878a`. GitHub Actions [run #73 / ID 39827210430](https://github.com/Webster-02/Superior-Student/actions/runs/39827210430) passed JavaScript validation, clean, all JVM unit tests, debug APK build, APK verification and artifact upload.
- Inspected the exact `22_notices_light.png`, `22_notices_dark.png`, and matching `22_notices.html` references in `pachattar_market_final(1).zip`.
- Added a native Notices Activity matching the reference header, Activity/U2U team segmented control, rounded message list, icon tiles, divider lines, two unread dots on the team-message view, timestamps and the 60-day retention note. Paper/Night colours and Poppins are reused.
- The default tab is **U2U team 2**, matching the supplied screenshots. The Activity tab switches to three local preview fixtures. Row taps show an explicit preview dialog; the removed-offer dialog states that detail/appeal is a separate step and that no appeal is submitted.
- Connected the existing **Notices** row in the Market menu from both Requests and Offers to this Activity. Updated the menu helper copy and registered the Activity as non-exported.
- **All notice rows/counts/unread dots are local fixtures.** Live notice queries, persisted read state, detailed notice/appeal navigation and support-ticket updates are not connected. No Firestore rules/indexes, deployment, login, Student Connect or Nearby transfer behavior changed.
- Added focused policy tests for reference labels/order/copy, unread dots, retention text and the preview-only appeal disclosure. Added the new Notices Activity, policy, icons and test to the Market CI path filters.
- **Verified debug APK artifact:** `superior-student-debug-apk`, archive size 41,032,464 bytes; archive SHA-256 `46818bc16d605282f73d4c74a62532dc63c399255010988ea1d97176e0b99bf1`. [Download from run #73](https://github.com/Webster-02/Superior-Student/actions/runs/39827210430/artifacts/12447841012). Expires October 29, 2026.

## Exact next step

Stop after UI-22 verification for user review. Do not start Screen 23 (notice details) or any live notice integration until the user explicitly types `continue`.


## UI-23 — Notice detail (offer removed)

- **Status:** implemented and CI verified on code commit `a671c9fedeb9c2bf7245497e1d2b442666ab3a79`. GitHub Actions [run #75 / ID 39828451469](https://github.com/Webster-02/Superior-Student/actions/runs/39828451469) passed JavaScript validation, clean, all JVM unit tests, debug APK build, APK verification and artifact upload.
- Inspected the exact `23_notice_light.png`, `23_notice_dark.png`, and matching light/dark HTML references in `pachattar_market_final(1).zip`.
- Added a native notice-detail Activity matching the back/title header, alert tile, notice timestamp, offer summary (`Assignment writing service`, Rs. 500), Rule 1 explanation, warning ladder, blue information banner, and fixed **Appeal this decision** / **Read posting rules** actions. It reuses Paper/Night Market tokens and Poppins.
- Connected only the matching **Your offer was removed** row from Screen 22 to this detail page. Other sample notices retain their existing preview behavior.
- The appeal button and rules button provide explicit preview dialogs. **No appeal is submitted, no offer status changes, and no live rules page opens**; Screen 24 (appeal form) remains a separate step. The notice and offer details are local reference fixtures only.
- Added focused policy tests for reference copy, warning ladder, offer details and truthful preview disclosures. Added this screen and test to the Market CI path filters.
- **Verified debug APK artifact:** `superior-student-debug-apk`, archive size 41,041,863 bytes; archive SHA-256 `2df9530281329c93a4eaaabfc37cde415307e772185710a7ac16331fff5f2745`. [Download from run #75](https://github.com/Webster-02/Superior-Student/actions/runs/39828451469/artifacts/12447344194). Expires October 29, 2026.
- No Firestore rules/index changes, deployment, login, Student Connect or Nearby transfer logic changed.

## Exact next step

Stop after UI-23 verification for user review. Do not implement Screen 24 (appeal form) or live notice/appeal integration until the user explicitly types `continue`.


## UI-24 — Appeal form

- **Status:** implemented and CI verified on code commit `309112c95a76676913436ccd757e39136d52316b`. GitHub Actions [run #77 / ID 39830529145](https://github.com/Webster-02/Superior-Student/actions/runs/39830529145) passed JavaScript validation, clean, all JVM unit tests, debug APK build, APK verification and artifact upload.
- Inspected the exact `24_appeal_light.png`, `24_appeal_dark.png` and matching light/dark `24_appeal.html` references from `pachattar_market_final(1).zip`.
- Added a native appeal form with back/title/subtitle, offer summary and Removed badge, “Why should we review this?” editable multiline reason, live character count, blue response-time/one-appeal reminder, and fixed lime **Send appeal** action. Paper/Night Market resources and Poppins are reused.
- Prefilled the editable reason with the exact reference text and capped input at 400 characters. The supplied mock screenshot shows 118/400, but its displayed text is 120 characters; the app therefore calculates and shows the accurate count **120/400**. Blank/whitespace reasons are rejected with a guidance dialog.
- Connected Screen 23’s **Appeal this decision** action to Screen 24. Pressing **Send appeal** displays a clear preview-only confirmation.
- **No appeal is sent or saved, and the offer status does not change.** Live appeal creation/support replies and notice updates remain disconnected. No Firestore rules/index changes, deployment, login, Student Connect or Nearby transfer changes.
- Added focused tests for reference copy, actual initial count, the 400-character limit, blank validation and truthful preview messaging; added the new Activity and policy/icon/tests to the Market workflow path filters.
- **Verified debug APK artifact:** `superior-student-debug-apk`, archive size 41,044,962 bytes; archive SHA-256 `2d7d46e9c948d9f3a09cc24bc26f95e35060113d81ec5d357d58ef8e1df0a3b3`. [Download from run #77](https://github.com/Webster-02/Superior-Student/actions/runs/39830529145/artifacts/12448437178). Expires October 29, 2026.

## Exact next step

Stop after UI-24 verification for user review. Do not implement Screen 25 or live appeal submission until the user explicitly types `continue`.


## UI-25 — Posting paused / suspension state

- **Status:** implemented and CI verified on code commit `93c2a8b3ca13dcdb36641148dadb11e136dad973`. GitHub Actions [run #79 / ID 39832292089](https://github.com/Webster-02/Superior-Student/actions/runs/39832292089) passed JavaScript validation, clean, all JVM unit tests, debug APK build, APK verification and artifact upload.
- Inspected the exact `25_suspended_light.png`, `25_suspended_dark.png` and corresponding light/dark HTML references from `pachattar_market_final(1).zip`.
- Added a native paused-posting reference screen matching the Market header, centered warning card, suspension end date, Rule 1 reason, “You can still” three allowed actions, “You cannot” posting/proposal restriction, and fixed **Rules** / **Appeal** footer actions. Reuses existing Paper/Night tokens and Poppins.
- **Appeal** opens the existing Screen 24 appeal form. **Rules** shows transparent preview copy because the full rules page is not implemented yet.
- This is a **static reference fixture**: its 27 Oct 2026 / 5 days label is the supplied screenshot value. The Activity does not read account status or enforce posting restrictions; live suspension state, posting gates and account actions are separate integration work.
- Registered the new Activity as non-exported and added focused policy tests for the exact reason/date/actions and the static-preview disclosure. Added its files to the Market CI workflow path filters.
- **Verified debug APK artifact:** `superior-student-debug-apk`, archive size 41,047,993 bytes; archive SHA-256 `4584a3c357f4015dca803fb417d436cc78ddfc49f6b99e7c06a11209039ee509`. [Download from run #79](https://github.com/Webster-02/Superior-Student/actions/runs/39832292089/artifacts/12449581848). Expires October 29, 2026.
- No live Firebase read/write, Firestore rules/index changes, deployment, login, Student Connect or Nearby transfer changes.

## UI-26 — Help and safety

- **Status:** implemented and CI verified on fix commit `f96a0541d2c8a5f31aae05bd363c436bb8068944`. GitHub Actions [run #82 / ID 39836522782](https://github.com/Webster-02/Superior-Student/actions/runs/39836522782) passed JavaScript validation, clean, all JVM unit tests, debug APK build, APK verification and artifact upload. Initial run #81 caught an `EditText` Kotlin setter typo; it was corrected in the next commit and run #82 passed.
- Inspected the supplied `26_help_light.png`, `26_help_dark.png` and corresponding light/dark HTML references from `pachattar_market_final(1).zip`.
- Added a native Help and safety reference screen using the existing Paper/Night market tokens, Poppins and in-app vector icons. It includes the header/subtitle, searchable two-by-two topic tiles (**Posting rules**, **Safety tips**, **FAQs**, **My reports**), the four reference popular questions, and the fixed **Still stuck?** support card with the lime **Contact support** CTA.
- Connected **Help and safety** from both existing Market listing menus and registered the Activity as non-exported. Search filters topic/question titles; topic/question rows open explanatory previews.
- Rules, FAQ, My reports and Contact support are **preview-only** in this step. The screen does not fetch/create reports, send support messages, read live account status, process payments, or claim a request succeeded. Future reference steps can connect the dedicated destinations.
- Added focused policy tests and the menu description assertion; added Screen 26 source/test paths to Market CI filtering. No Firestore rules/index changes, live Firebase writes, login, Student Connect or Nearby transfer changes.
- **Verified debug APK artifact:** `superior-student-debug-apk`, archive size 41,062,601 bytes; archive SHA-256 `ffee4e007da0ceb2bd6541133d145b47369662a6b10d3ee4fddfbd3ebe8af43b`. [Download from run #82](https://github.com/Webster-02/Superior-Student/actions/runs/39836522782/artifacts/12451341741). Expires October 29, 2026.

## Exact next step

Stop after UI-26 verification for user review. Implement Screen 27 only after the user explicitly replies `continue`.
