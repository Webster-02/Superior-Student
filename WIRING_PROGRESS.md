# U2U Market Wiring Progress

**Working branch:** `feature/market-wiring`  
**Starting commit:** `c6b7d13578e82bbcb0724aaca1969d3fcccd35ca` (from `feature/market-ui-reference`)  
**Current part:** W2 feed wiring implemented in source; CI/build verification pending.  
**Rules:** No main merge, no deployment, no changes to `firestore.rules`, `firestore.indexes.json` or tests without explicit approval.

## Part status

| Part | Scope | Status | Evidence / note |
|---|---|---|---|
| W0 | Audit only; create wiring report | **COMPLETE** | `WIRING_REPORT.md`; no feature code modified |
| W1 | Identity, verified-email gate, profile bootstrap, suspension state/until | **COMPLETE — verified** | Run [#111](https://github.com/Webster-02/Superior-Student/actions/runs/40026265620) on commit `693390b6` passed unit tests, APK build, APK verification and upload. Screen 25 loads actual status/expiry and the owner-scoped restriction notice. Artifact `superior-student-debug-apk`, ID `12532932046`. |
| W2 | Live Requests/Offers feeds, ranking/cache, filtering, paging, pull refresh, states | IMPLEMENTED IN SOURCE — latest verification pending | Both feeds: index-compatible category/order queries, cursor paging, pull-to-refresh, loading/empty/error states, expiry/status/moderation/report-threshold/blocked-user filters, matching/ranking, and at most 3 pinned results. Config is loaded via the six-hour `MarketConfigProvider`; Offers also reads real aggregate `userStats`. Runs #121/#122 passed earlier feed revisions; #124 passed after refresh/paging race guards. Report-threshold/config updates in #125/#126 were still running at this checkpoint. |
| W3 | Tasks lifecycle, My Tasks, proposals, accept, complete/rate, Applied | NOT STARTED | Task create exists; most lifecycle screens remain fixtures/preview |
| W4 | Offers lifecycle and My Offers | IN PROGRESS | My Offers repository actions exist; offer detail/message/expiry/lifecycle coverage remains open. CI #110 is an earlier revision and does not verify the latest branch. |
| W5 | Market text-only chat | NOT STARTED | Repository APIs exist; screens are fixture messages, not connected |
| W6 | Report, block, rating/trust-and-safety | NOT STARTED | Repository methods exist; report/block/rating UI remains preview |
| W7 | Notices, support, appeals, settings | NOT STARTED | Repository methods exist; screens do not call them |
| W8 | Admin panel source, admin setup, real admin queries and mutations | NOT STARTED | No admin web source or Firebase Hosting config exists |
| W9 | Expiry/counter cleanup, offline and consistency | NOT STARTED | My Offers expiry reconciliation exists; My Tasks cleanup and offline/cached-feed banners remain open. |
| W10 | Zero fixture/direct write audit, QA checklist, tests/build | NOT STARTED | Must re-run source searches and automated tests after wiring |

## Per-screen checkpoint

“Demo” means the screen is currently fixture/preview-only or source is absent. “Partly real” means a real data path exists but important actions/behaviour remain unwired. The detailed source and Rules contract for each row is in `WIRING_REPORT.md`.

| Screen | Current status | Next part |
|---|---|---|
| 01 Market entry | Partly real; route/count not verified | W1/W2 |
| 02 Requests | Feed wired in source; waiting on build verification | W2 verification / W10 |
| 03 Offers | Feed wired in source; waiting on build verification | W2 verification / W10 |
| 04 Menu/badges | Partly real, badge static | W7 |
| 05 Helper task detail | Partly real display; actions preview | W3/W6 |
| 06 Poster task detail | Demo | W3 |
| 07 Post-task title/category | Partly real draft | W3 |
| 08 Post-task details | Partly real draft | W3 |
| 09 Publish task | Partly real; repository bootstrap/create real | W3 |
| 10 Posting rules acknowledgement | Partly real/local state | W3 |
| 11 Send proposal | Demo | W3 |
| 12 Proposals list | Demo | W3 |
| 13 Accept sheet | Demo | W3 |
| 14 Task/Market chat | Demo | W5 |
| 15 My Tasks | Demo | W3/W9 |
| 16 Rate task | Demo | W6 |
| 17 Offer detail | Partly real display; actions preview | W4/W6 |
| 18 Post/Edit offer | Partly real; repository bootstrap/create/edit | W4 |
| 19 My Offers | Partly real source; latest branch not build-verified | W4/W10 |
| 20 Report sheet | Demo | W6 |
| 21 States/offline/expiry | Demo as a connected system | W2/W9 |
| 22 Notices | Demo | W7 |
| 23 Notice detail | Demo | W7 |
| 24 Appeal | Demo | W7 |
| 25 Suspended account | Partly real; status/until read live | W7 for actual notice reason/detail |
| 26 Help & safety | Partly real navigation/copy | W7 |
| 27 FAQs | Real static content; review copy | W7 |
| 28 Rules/safety | Partly real static content | W7 |
| 29 Contact support | Demo/no live submit | W7 |
| 30 My reports | Demo | W6 |
| 31 Market settings | Demo/no persistence | W7 |
| 32 Blocked users | Demo | W6/W7 |
| Admin Login | No source/hosted app | W8 |
| Admin Dashboard | No source | W8 |
| Admin Reports | No source; repository operation exists | W8 |
| Admin Listings | No source; repository operations exist | W8 |
| Admin Users | No source; repository operations exist | W8 |
| Admin User detail | No source | W8 |
| Admin Support & appeals | No source; repository operations exist | W8 |
| Admin Rules & settings | No source; config mutation exists | W8 |
| Admin Audit log | No source/query builder | W8 |

## Verification checkpoint

- **Base revision:** `c6b7d13578e82bbcb0724aaca1969d3fcccd35ca`.
- **W1 verification:** [run #111](https://github.com/Webster-02/Superior-Student/actions/runs/40026265620) passed unit tests, APK build, APK verification and upload for commit `693390b6`; artifact ID `12532932046`.
- **W2 earlier verification:** [run #124](https://github.com/Webster-02/Superior-Student/actions/runs/40028547655) passed unit tests, APK build, APK verification and upload after both feeds gained cursor resets and refresh/paging race guards. Artifact `superior-student-debug-apk`, ID `12533264170` (41,156,388 bytes).
- **Latest source at this checkpoint:** `b0e51bd2` adds the configured `reportsToHide` filter to Offers; its parent `7494b0cf` adds cached `config/market` and configured report-threshold filtering to Requests. Runs #125 and #126 were in progress; this checkpoint must be updated with their final results before calling W2 verified.
- **Rules/indexes/tests:** `firestore.rules`, `firestore.indexes.json` and `tests/` remain untouched. No deployment or merge occurred.
- **Rules/indexes/tests:** `firestore.rules`, `firestore.indexes.json`, and `tests/` were not edited during W1/W2. No test or APK build output is available for the latest changes.
- **Deploy/merge:** none.
- Source-priority exception: the addendum/Rules allow sign-in-only self-service writes for `skillTags`, `settings` and `blockedUids`; listing/content, profile bootstrap, chat/message, reports/ratings/support/appeals require verified email. These rule-aligned exceptions were not tightened beyond the published contract.
- Safe profile bootstrap uses empty skills/blocks, active status, null suspension/window markers, zero counters/warnings, and default alerts with task-match alerts off (opt-in). Screen 25 loads the real matching suspension/ban notice through the owner-scoped notices query and displays its title/body without implicitly marking it read.
- After every completed wiring part, update this table and `WIRING_REPORT.md` in that part's commit. If interrupted, start from this file and `git log`; do not redo W0.
