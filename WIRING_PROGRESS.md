# U2U Market Wiring Progress

**Working branch:** `codex/marketplace-live-data-audit`

**Starting commit:** `c6b7d13578e82bbcb0724aaca1969d3fcccd35ca` (from `feature/market-ui-reference`)

**Current part:** W3–W7 live wiring is implemented on the task branch; W9 is in progress; W10 CI passed and device verification remains.

**Rules:** No main merge, no deployment, no changes to `firestore.rules`, `firestore.indexes.json` or tests without explicit approval.

## Part status

| Part | Scope | Status | Evidence / note |
|---|---|---|---|
| W0 | Audit only; create wiring report | **COMPLETE** | `WIRING_REPORT.md`; no feature code modified |
| W1 | Identity, verified-email gate, profile bootstrap, suspension state/until | **COMPLETE — verified** | Run [#111](https://github.com/Webster-02/Superior-Student/actions/runs/40026265620) on commit `693390b6` passed unit tests, APK build, APK verification and upload. Screen 25 loads actual status/expiry and the owner-scoped restriction notice. Artifact `superior-student-debug-apk`, ID `12532932046`. |
| W2 | Live Requests/Offers feeds, ranking/cache, filtering, paging, pull refresh, states | **COMPLETE — CI verified** | Both feeds use `MarketRepository` wrappers over index-compatible query builders, cursor paging, pull-to-refresh, loading/empty/error states, configured report thresholds, expiry/status/moderation/blocked-user filters, ranking/matching and max 3 pins. Bounded persistent cache holds up to 50 real listings plus the signed-in user's Market profile and seller aggregate stats; saved cards remain visible with an offline message. Fresh all-category caches use indexed `updatedAt > lastSync` delta queries with paging and a 30-second overlap; checkpoints are captured before network round trips. Reference sort choices are preserved; local deadline/budget/rating/price sorts do not invent server indexes. Final source verification [run #142](https://github.com/Webster-02/Superior-Student/actions/runs/40031623298) passed tests, APK build, APK verification and upload; artifact ID `12535691277`. |
| W3 | Tasks lifecycle, My Tasks, proposals, accept, complete/rate, Applied | **IMPLEMENTED — CI verified** | Create/edit/cancel/expire/accept/withdraw/complete/rate and proposal/list/chat routes use live repository data. My Tasks reconciles expired open tasks. Run [#611](https://github.com/Webster-02/Superior-Student/actions/runs/40044769582) passed unit tests and APK build. |
| W4 | Offers lifecycle and My Offers | **IMPLEMENTED — CI verified** | Create/edit/pause/renew/remove/expire, live offer detail, seller rating, report and Market offer chat are wired. Delivery terms are not stored in the current schema, so detail directs users to chat. Run #611 passed. |
| W5 | Market text-only chat | **IMPLEMENTED — CI verified** | Participant-scoped conversation reads, realtime text messages, send, read markers and task completion action use Market repositories and existing Rules. Run #611 passed. |
| W6 | Report, block, rating/trust-and-safety | **IMPLEMENTED — CI verified** | Account-owned report history, report submission, optional block, blocked-user management and completed-task ratings use live data. Run #611 passed. |
| W7 | Notices, support, appeals, settings | **IMPLEMENTED — CI verified** | Live notices/read state, eligible-notice appeals, support ticket create/history/replies, saved preferences and blocked counts are connected. Run #611 passed. |
| W8 | Admin panel source, admin setup, real admin queries and mutations | NOT STARTED | No admin web source or Firebase Hosting config exists |
| W9 | Expiry/counter cleanup, offline and consistency | **IN PROGRESS** | My Offers and My Tasks reconcile expired owner listings through transactional repository methods. Wider background cleanup for inactive users and emulator consistency checks remain open. |
| W10 | Zero fixture/direct write audit, QA checklist, tests/build | **CI VERIFIED — runtime QA pending** | `git diff --check` and runtime fixture/direct-Firestore scans pass. Run #611 passed unit tests, APK build, APK verification and artifact upload; device-level validation against live Firebase remains outstanding. |

## Per-screen checkpoint

“Demo” means the screen is currently fixture/preview-only or source is absent. “Partly real” means a real data path exists but important actions/behaviour remain unwired. The detailed source and Rules contract for each row is in `WIRING_REPORT.md`.

| Screen | Current status | Next part |
|---|---|---|
| 01 Market entry | Student Connect Market tile opens the live Requests feed directly; no separate Market entry counters are shown | Verified |
| 02 Requests | Feed verified in CI; device/Firebase runtime check still pending | W10 runtime QA |
| 03 Offers | Feed verified in CI; device/Firebase runtime check still pending | W10 runtime QA |
| 04 Menu/badges | Live unread notice count | W7 |
| 05 Helper task detail | Live feed record, seller aggregate rating, proposal/report actions | W3/W6 |
| 06 Poster task detail | Live owned task, proposals and lifecycle actions | W3 |
| 07 Post-task title/category | Local multi-step form draft; persisted with the task at publish | W3 |
| 08 Post-task details | Local multi-step form draft; persisted with the task at publish | W3 |
| 09 Publish task | Live profile bootstrap and repository-backed task create | W3 |
| 10 Posting rules acknowledgement | Local confirmation in the publish flow; not a separate account record | W3 |
| 11 Send proposal | Live task check and repository create | W3 |
| 12 Proposals list | Live proposals and shortlist/decline/accept/chat actions | W3 |
| 13 Accept sheet | Acceptance is live in Proposals | W3 |
| 14 Task/Market chat | Live participant-scoped text chat | W5 |
| 15 My Tasks | Live posted/applied records, withdraw/rate/chat and expiry reconciliation | W3/W9 |
| 16 Rate task | Live completed-task rating | W6 |
| 17 Offer detail | Live details, seller aggregates, report and offer chat | W4/W6 |
| 18 Post/Edit offer | Live repository create/edit | W4 |
| 19 My Offers | Live owner offers and lifecycle/expiry actions | W4/W9 |
| 20 Report sheet | Live report and optional block | W6 |
| 21 States/offline/expiry | Feeds show live loading/error/cache states; isolated gallery is reference-only | W2/W9 |
| 22 Notices | Live owner-scoped inbox and unread state | W7 |
| 23 Notice detail | Live notice content/date and eligible appeal route | W7 |
| 24 Appeal | Live submission linked to eligible notice | W7 |
| 25 Suspended account | Live status/end date and matching decision notice; Rules/appeal routes | W7 |
| 26 Help & safety | Live routes to Rules, FAQ, reports and support; static safety guidance | W7 |
| 27 FAQs | Real static content; review copy | W7 |
| 28 Rules/safety | Static guidance content; the app opens the real Rules screen and uses the current published Firestore Rules | W7 |
| 29 Contact support | Live ticket create/history/replies | W7 |
| 30 My reports | Live reporter-scoped report history | W6 |
| 31 Market settings | Live profile skills/alerts and blocked count | W7 |
| 32 Blocked users | Live block list and unblock action | W6/W7 |
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
- **W1 verified:** [run #111](https://github.com/Webster-02/Superior-Student/actions/runs/40026265620) passed unit tests, APK build, APK verification and upload for commit `693390b6`; artifact ID `12532932046`.
- **W2 feed/paging verified:** [run #124](https://github.com/Webster-02/Superior-Student/actions/runs/40028547655) passed unit tests, APK build, verification and upload after refresh/paging race guards; artifact ID `12533264170`.
- **Configured visibility verified:** [runs #125](https://github.com/Webster-02/Superior-Student/actions/runs/40028681099) and [#126](https://github.com/Webster-02/Superior-Student/actions/runs/40028718552) passed after the `reportsToHide` checks were wired into Requests and Offers.
- **Persistent cache verified:** [runs #129](https://github.com/Webster-02/Superior-Student/actions/runs/40029663535) and [#130](https://github.com/Webster-02/Superior-Student/actions/runs/40029674196) passed unit tests, APK build, APK verification and upload.
- **Incremental sync and race-safe checkpoint verified:** [runs #136](https://github.com/Webster-02/Superior-Student/actions/runs/40030471393), [#137](https://github.com/Webster-02/Superior-Student/actions/runs/40030742342), [#138](https://github.com/Webster-02/Superior-Student/actions/runs/40030751428), and [#139](https://github.com/Webster-02/Superior-Student/actions/runs/40031163243) passed. Run #139 uploaded artifact ID `12534482791` (41,176,899 bytes).
- **Repository feed-read wrappers verified:** [run #140](https://github.com/Webster-02/Superior-Student/actions/runs/40031341835) passed; artifact ID `12534783307` (41,176,809 bytes).
- **Reference sort behavior verified:** [runs #141](https://github.com/Webster-02/Superior-Student/actions/runs/40031513998) and [#142](https://github.com/Webster-02/Superior-Student/actions/runs/40031623298) passed. Latest artifact `superior-student-debug-apk`, ID `12535691277`, size 41,177,059 bytes.
- **Rules/indexes/tests:** `firestore.rules`, `firestore.indexes.json` and `tests/` remain untouched. No merge or deployment occurred.

- Source-priority exception: the addendum/Rules allow sign-in-only self-service writes for `skillTags`, `settings` and `blockedUids`; listing/content, profile bootstrap, chat/message, reports/ratings/support/appeals require verified email. These rule-aligned exceptions were not tightened beyond the published contract.
- Safe profile bootstrap uses empty skills/blocks, active status, null suspension/window markers, zero counters/warnings, and default alerts with task-match alerts off (opt-in). Screen 25 loads the real matching suspension/ban notice through the owner-scoped notices query and displays its title/body without implicitly marking it read.
- After every completed wiring part, update this table and `WIRING_REPORT.md` in that part's commit. If interrupted, start from this file and `git log`; do not redo W0.
