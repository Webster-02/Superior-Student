# U2U Market Wiring Audit (W0)

**Audit branch:** `feature/market-wiring`  
**Base branch:** `feature/market-ui-reference`  
**Base commit:** `c6b7d13578e82bbcb0724aaca1969d3fcccd35ca`  
**Scope:** static source audit only. No feature code, Rules, indexes or tests were modified in W0. UI source, Market repository/query builder/config code, Market rules, index map and the Market specification/addendum were inspected. Firebase production state was not queried as part of this source audit.

## Executive result

- **Current live-data coverage:** Requests and Offers have live list reads through `MarketQueryBuilders`; task/offer posting and My Offers mutations invoke `MarketRepository`. W1 adds idempotent profile bootstrap and current-profile reads, removes task/offer screens' direct `marketProfiles/{uid}` preflight reads, and connects Screen 25 to real account status/expiry. CI #110 passed tests, APK build, APK verification and artifact upload; broader Market feature wiring remains incomplete.
- **Confirmed fixture count:** **35 explicit fixture objects** in application-source collections: 3 public offer fixtures, 4 My Offers fixtures, 3 proposal fixtures, 6 My Tasks fixtures, 2 blocked-user fixtures, 4 report fixtures, 6 notice fixtures, 6 chat messages and 1 task-poster seed model. Additional stand-alone detail examples, fake/default counts, ratings and local-only action messages are listed below; they are not included in the 35-object count.
- **Unconnected feature screens:** Most of the details/lifecycle/trust-and-safety/settings screens currently display reference examples or validate input without invoking the corresponding repository operation.
- **Direct writes outside `MarketRepository`:** **0 found** in the Market UI activities scanned. The create-task/create-offer flows call repository methods. UI code does bypass the data layer for some reads, noted below.
- **Rules compatibility:** No feature was proven fundamentally impossible under the checked-in Rules. The client must comply with the exact per-operation Rules contracts in the “Rules constraints and potential blockers” section; these checks are implementation requirements, not permission to weaken Rules.
- **Admin site:** No admin web source, admin HTML/JS bundle, `.firebaserc` or Firebase Hosting configuration exists in this branch. Admin repository operations exist, but that does not create a usable admin page or URL.

## Screen-by-screen wiring matrix

Status meanings: **real** = works from live data or is intentionally static content; **partly real** = has some real navigation/data/write paths but important behaviour is still local or missing; **demo** = fixture-only, no live operation, or the screen source is absent.

| Screen / feature | Data source today | Repository method / query builder required | Rules operation / contract | Status |
|---|---|---|---|---|
| 01 Market entry card | Outside the Market package; route/visit count not verified in this pass | Entry should open the Requests/Offers host; unread count should come from the current user's notices | Signed-in Market read; notice access must remain owner-scoped | partly real — verify entry route and count |
| 02 Requests feed | Live `tasks` read through `MarketQueryBuilders.publicTasks`; client maps actual task fields | `publicTasks`, `MarketConverters.task`, `MarketRankingScorer.rankTasks`; profile/config for blocked and matching signals | Public query must include `moderation == ok` and `status == open`; hide expired/deadline-passed and blocked listings client-side | partly real — no pagination/pull refresh/config ranking/blocked-user filtering; `matchesUser` is hard-coded false |
| 03 Offers feed | Live `offers` read through `MarketQueryBuilders.publicOffers`; client maps actual offer fields | `publicOffers`, converter, cached `MarketConfigProvider`, ratings/userStats input and `MarketRankingScorer.rankOffers` | Query must include `moderation == ok` and `status == active`; hide expired/blocked listings client-side | partly real — ratings are currently forced to `0.0 / 0 / false`, and ranking/cache/expiry/block filters are not integrated |
| 04 Market menu | Static menu/navigation; static badge values in menu data | Current-user notice query/count from `myNotices`; real navigation destinations | Notices may only be read/updated by their owner | partly real — menu routes exist, badge is not live |
| 05 Task detail (helper) | Some task details passed from the live Requests card; fallback/category tags and rating are hard-coded | Repository task lookup, real poster/profile rating source, `submitReport`, `createProposal`, bookmark store | Public task visibility constraints; report uses deterministic ID and same-batch profile counter; proposal only by eligible helper on open task | partly real — report/proposal/save actions are not real, and task detail displays a hard-coded 4.8 rating |
| 06 Task detail (poster) | Reference model/data; not connected to a selected live owned task | Owner-scoped task lookup via repository; proposals via `MarketQueryBuilders.taskProposals`; `editTask` / `cancelTask` | Owner-only reads/mutations; edits require an open task; counters must be paired atomically | demo |
| 07 Post task — title/category | User-entered local draft | Keep draft between steps; no remote write until submit | Validate shape before final repository create | partly real — draft UI exists |
| 08 Post task — details/budget/skills/deadline | User-entered local draft plus fixed selectable category/skill vocabulary | Carry the draft through to step 3; normalize validation in repository | Exact allowed fields, length/range limits and timestamps | partly real — draft UI exists |
| 09 Post task — review/publish | Calls `MarketRepository.ensureMarketProfile` then `createTask`; verified-email check shown before submit | `ensureMarketProfile`, `createTask` | Verified email + existing `profiles/{uid}.displayName`; task creation and `openTasks` increment/lastMutation in one valid transaction | partly real — profile bootstrap/read is centralized; live counter/status UX remains incomplete |
| 10 Task posting rules acknowledgement | Acknowledgement is local state in the posting flow; no separately wired live operation | Rules content can remain static; final submit should require acknowledgement | Rules enforce the write contract, not the UI checkbox | partly real — acknowledgement is local, not a permission boundary |
| 11 Send proposal | Form validation/quick picks; submit ends with “sending is not connected” | `MarketRepository.createProposal` | Verified student, task open and not expired, proposer not poster, deterministic document ID/immutable `proposerId`, daily and per-task limits | demo |
| 12 Proposals list | Three hard-coded people/proposals; count constant says 4 despite 3 fixture cards | `MarketQueryBuilders.taskProposals`, converters, `setProposalStatus` | Task poster only; proposal reads limited to the parent task; status changes only on allowed transitions | demo |
| 13 Accept proposal sheet | Local confirmation/preview | `MarketRepository.acceptProposal` | Accept proposal + assign task + decline others + release poster counter + create deterministic Market conversation in one transaction | demo |
| 14 Task/Market chat | Six hard-coded chat messages; message controls show preview-only feedback | `MarketQueryBuilders.marketChats`, `sendMarketMessage`, `markConversationRead`; real-time listener scoped to current user | Private participant-only `marketConversations/{id}/messages`; text-only; admins and nonparticipants must not read | demo |
| 15 My Tasks (Posted/Applied) | 2 hard-coded posted tasks + 4 hard-coded applied tasks; tab labels say “Posted 2”/“Applied 3” despite 4 applied cards | `myTasks`, `appliedProposals` collection-group query, task/proposal converters; lifecycle methods | Owner-scoped tasks; Applied query must use `proposerId == auth.uid` and `updatedAt desc` | demo |
| 16 Rate completed task | Form and selected tags are local; no live rating submit observed | `MarketRepository.rateCompletedTask`; load existing rating to prevent duplicate UI path | Only participants of completed task; one rating per user/task; rating + `userStats` update in same batch | demo |
| 17 Offer detail | Live offer fields are passed from the Offers card; save/report/message outcomes are local previews | Repository offer lookup; `submitReport`, local bookmark store, `openOfferConversation` | Public/owner visibility; do not report own listing; Market chat must be separate text-only flow | partly real — primary display can be live, actions cannot |
| 18 Post/Edit offer | Create calls `MarketRepository.ensureMarketProfile` then `createOffer`; edit calls `editOffer`; no UI-side profile preflight read | `ensureMarketProfile`, `createOffer`, `editOffer` | Verified email, existing `profiles/{uid}.displayName`, exact offer schema, 5-active-offer counter; owner edits require allowed posting status | partly real — identity/profile setup is centralized; full offer lifecycle remains in W4 |
| 19 My Offers | Current branch contains a live owner-scoped `MarketQueryBuilders.myOffers` read and repository calls for pause/resume/renew/remove/edit | `myOffers`, `pauseOffer`, `resumeOffer`, `renewOffer`, `removeOffer`, `editOffer`, `expireOffer` | Owner query (`sellerId == auth.uid`); counters only move with valid lifecycle `lastMutation`; paused removal must not decrement twice | partly real — wired in source, but current base commit fails compilation (see blocker) |
| 20 Report sheet | Validates selected reason then shows “preview only”; optional block is local | `submitReport`; `blockUser` only if the user confirms the separate block action | Deterministic report ID; target owner cannot report own listing; one report per user/item; report counter/status change in same batch | demo |
| 21 Loading/empty/offline/error/expired states | Static reference ordering and copy; no complete offline/cache banner integration | Shared query/error/cache state in live screens; expiry cleanup via owner lifecycle methods | Must keep public feed query constraints, avoid retry loops and use allowed lifecycle transitions | demo as a unified real-state system |
| 22 Notices inbox | Six local notice fixtures; unread counts fixed at Activity=3 and U2U team=2 | `MarketQueryBuilders.myNotices` + `markNoticeRead` and actual tab/count calculations | Student reads/updates only `users/{auth.uid}/notices`; user must not create notices | demo |
| 23 Notice detail | Hard-coded “Your offer was removed” content | Load the notice ID from the owner-scoped notices path; mark read through repository | Owner-only notice read/update; appeals must refer to a real appealable notice | demo |
| 24 Appeal | Example removed offer and “appeal preview” message; nothing is sent | `MarketRepository.submitAppeal` using deterministic `appeal_{noticeId}_{uid}` | Verified account; one appeal per removal/notice; create allowed support ticket shape and open-ticket limit | demo |
| 25 Suspended account | Reads current profile through `MarketRepository.loadCurrentMarketProfile`; displays real status and formatted `suspendedUntil` | `loadCurrentMarketProfile`; connect exact decision notice via W7's owner-scoped notice flow | Owner-scoped profile read; banned/active-suspended users cannot post; Rules/addendum allow posting at/after `suspendedUntil` | partly real — status/expiry is live; reason is in Notices because the profile schema has no reason field |
| 26 Help and safety | Navigation and static help copy exist; several links/messages explicitly describe preview-only features | Route to real FAQ/rules/reports/support screens; live reports via `myReports` | Any account/report data must remain owner-scoped | partly real |
| 27 FAQs | Static FAQ list/copy; no account records needed | Static content is acceptable; remove “preview/not connected” copy where it implies this page itself is incomplete | No Firestore write needed for static FAQs | real as static content, subject to content review |
| 28 Posting rules and safety | Static rule copy; no Firestore operation needed | Static policy page aligned with `MARKET_FINAL_SPEC.md` and addendum | Rules in Firestore remain authoritative; UI is informational only | partly real — verify content completeness |
| 29 Contact support | Form validation exists but submit says nothing has been saved/sent; attachment selects a sample listing | `createSupportTicket`; `mySupportTickets` to show replies/status | Verified user; exact support schema; open ticket counter paired atomically; admin reply/resolve must link log and notice | demo |
| 30 My reports | Four hard-coded sample report rows | `MarketQueryBuilders.myReports` | Query must include `reporterId == auth.uid`; status decisions are admin-only and logged | demo |
| 31 Market settings | Skills/toggles change locally; policy includes reference defaults; no persistence | `updateSkillTags`, `updateSettings`, config cache for announcement/ranking; evaluate matching alerts on app open only (no Cloud Functions) | Self-service fields only on `marketProfiles/{uid}`; status/counters/warnings are not user-editable | demo |
| 32 Blocked users | Two hard-coded people/IDs; remove only changes preview list | Read `marketProfiles/{uid}.blockedUids`; `blockUser` / `unblockUser` | Self-service update only to allowed `blockedUids`; new Market chat blocked if either user blocks the other | demo |
| Admin Login | No admin web source or Hosting target exists | Static admin web app + Firebase Auth Google sign-in; validate `admins/{uid}` | `admins/{uid}` may only be created from Firebase Console; all admin operations require verified admin auth | demo / source absent |
| Admin Dashboard | No page source | Add admin read queries/builders for reports/listings/users/support/config/log metrics | Admin-only query branches; avoid private chat/message collections | demo / source absent |
| Admin Reports | No page source; repository mutation exists | Admin reports queue query builder + `adminSetReportStatus` | Admin-only list/update; paired `adminLogs` and reporter notice | demo / source absent |
| Admin Listings | No page source; repository moderation methods exist | Admin task/offer queue query builders + `adminModerateTask` / `adminModerateOffer` | Moderation status/pin only via admin branches; listing counter/notice/log transitions must match Rules | demo / source absent |
| Admin Users / user detail | No page source; repository warn/suspend/lift operations exist | User-list/detail query builders + `adminWarnUser`, `adminSuspendUser`, `adminLiftSuspension` | Admin listing access only; suspension/ban/hide/counter/notice/log operations must be a Rules-valid batch | demo / source absent |
| Admin Support & Appeals | No page source; repository reply/resolve/decision methods exist | Admin support queue query builders + `adminReplyToSupportTicket`, `adminResolveSupportTicket`, `adminDecideAppeal` | Required audit record and affected-user notice in the same accepted operation | demo / source absent |
| Admin Rules & Settings | No page source; `adminUpdateMarketConfig` exists | Admin config reader/editor + validated `MarketConfig` model | Create/update `config/market` only as admin with the matching new `adminLogs` record; limits remain read-only | demo / source absent |
| Admin Audit log | No page source or log query builder | Admin-only `adminLogs` list/detail query builder | Verified admin read; logs are append-only and cannot be updated/deleted | demo / source absent |

## Concrete fixture/demo findings

The **35 explicit fixture objects** above are source objects, not live Firestore records:

- Offers: 3 (example sellers Ayesha Khan, Zain Malik, Noor Fatima; hard-coded ratings 4.9/14, 4.7/9, 5.0/6).
- My Offers: 4 static example offers.
- Proposals: 3 static people/proposals, while `TOTAL_PROPOSALS` is 4.
- My Tasks: 2 posted + 4 applied examples, while the Applied label says 3.
- Blocked users: 2 example names/university IDs.
- My Reports: 4 example report rows.
- Notices: 3 team + 3 activity examples; fixed unread counts 3 and 2.
- Market chats: 3 task-chat + 3 offer-chat messages.
- Task poster detail: 1 reference UI model.

Additional placeholders not counted as those fixture objects: a fixed `2` menu badge, a fixed 4.8 task-author rating, example appeal/notice/support details, preview-only report/save/message/accept/decline/shortlist/rating/settings actions, and default values that substitute for real rating/matching signals. Some static lists (category/skill choices, report reasons, FAQ text and rating tags) are legitimate UI vocabularies, not user-record fixtures; they should not be removed just because they are constants.

## Data-layer bypass audit

**Direct Market writes outside `MarketRepository`: none found in Market UI activities.** Task and offer publishing call repository methods, and the new My Offers actions call repository methods.

**Read/query paths to centralize or bring under the query-builder contract:**

1. `MarketRepository.adminSuspendUser` performs two collection queries directly (`tasks` by `posterId/status`, `offers` by `sellerId/status`) rather than using `MarketQueryBuilders`.
2. `MarketConfigReader` reads singleton `config/market` directly. A single-document read need not be a composite-index query, but the desired query-builder/repository contract should be made explicit so it is not an ad hoc UI query.
3. **Resolved in W1:** `MarketPostOfferActivity` and `MarketPostTaskStep3Activity` call `MarketRepository.ensureMarketProfile()`; their direct `marketProfiles/{uid}` reads and UI bootstrap logic are removed.
4. Requests, Offers and My Offers are the only list feeds found wired through their required builders. Notices, My Tasks/Applied, proposals, reports, support and chat lists are still unwired rather than incorrectly querying collections.

## Rules constraints and potential blockers

No feature was proven **fundamentally blocked** by the checked-in Rules in this static pass; most missing features have matching repository APIs already. The following are mandatory contracts and will cause permission-denied if client wiring ignores them:

- Public task list: constrain `moderation == ok` and `status == open`; public offer list: `moderation == ok` and `status == active`.
- Applied proposals: collection-group query must include `proposerId == request.auth.uid` and order by `updatedAt desc`; the rules allow the signed-in user to read only their proposals through this collection-group path.
- Student writes generally require a verified email and existing `profiles/{uid}`; names stored in tasks/proposals/messages must match the U2U profile's `displayName` exactly. `marketProfiles` creation must use the safe defaults; student profile updates may only change allowed self-service fields.
- Limits/counters are not UI-only: task create/cancel/expire/accept, offer create/pause/resume/remove/renew, proposal create, report create and support-ticket create must use repository transactions/batches that include the exact corresponding counter/`lastMutation` contract. Completing an assigned task must not decrement `openTasks` a second time.
- Reports require the deterministic ID `{targetType}_{targetId}_{uid}`; users cannot report their own task/offer. Three reports move an approved listing to `under_review` as part of the reporting operation.
- Rating writes must be limited to the two participants of a completed task and update `ratings` + `userStats` in the same batch.
- Market chat is separate and text-only. Do not read/write existing U2U `chatConversations`, messages, Nearby or transfer collections for Market features; admins must not query Market chat or private message collections.
- Admin operations require a verified UID with an `admins/{uid}` document. `admins` is client-read-only. Admin config/report/support/moderation operations must pair required `adminLogs` entries and affected-user notices.
- The rules allow a verified admin to list admin queues and update status/moderation through explicit admin paths; absence of an admin web UI is a **code/hosting gap**, not a Rules prohibition.

Do not widen Rules to accommodate unconnected screens. For any implementation where emulator tests show a mismatch between a required feature and a rule, stop and document the exact match/function and denied write before requesting a Rules change.

## Build verification

The pre-W1 revision failed [run #108](https://github.com/Webster-02/Superior-Student/actions/runs/40020865893) due to a `Throwable`/Google Tasks `Exception` mismatch, and the first W1 run #109 exposed a malformed `mapOf(` helper declaration. Those compile issues were fixed in follow-up commits. [Run #110](https://github.com/Webster-02/Superior-Student/actions/runs/40024930878) passed unit tests, APK build, APK verification and artifact upload on commit `c8afe815b681de380189eccd8d01da6395a185dd`. Artifact `superior-student-debug-apk` ID `12531714428` (41,141,011 bytes) was uploaded successfully.

## Admin hosting inventory

- `firebase.json` configures Firestore rules/indexes only; it contains no `hosting` block.
- There is no `admin/` directory, admin HTML/JS entry point, web-app build or admin page route in this repository.
- Repository methods for admin mutations are not a deployed panel. There is no valid hosted admin URL to report from this source tree.
- Creating `admins/{uid}` authorizes the Google account but cannot create or host a web page by itself.

## W1 implementation notes

- `MarketRepository.ensureMarketProfile()` performs an idempotent transaction: it requires a verified account and existing `profiles/{uid}.displayName`, then validates the current Market profile or creates it once using the allowlisted schema.
- `MarketRepository.loadCurrentMarketProfile()` reads only the current user's Market profile and fails closed if an existing document cannot be decoded.
- Task/offer creation screens no longer read `marketProfiles/{uid}` directly; bootstrap is repository-owned. Listing writes still re-check status/counters in the repository and remain subject to Rules.
- Screen 25 displays live profile status and `suspendedUntil`, and loads the matching account-level suspension/ban notice from the signed-in user's `users/{uid}/notices` collection via `MarketQueryBuilders.myNotices`. It displays the real title/body without marking the notice read implicitly; appeal action and the general Notices inbox remain W7.
- First-run defaults use empty skill/block lists, active status, null suspension/window markers, zero warnings/counters and proposal/update/notice alerts enabled with task-matching alerts off (opt-in). Per addendum/Rules, `skillTags`, `settings` and `blockedUids` self-service writes remain sign-in-only.
- No Rules, indexes or test files are changed. Baseline W1 CI passed on run #110; this follow-up wiring of the real notice title/body still needs its own CI run.

## W0 exit criteria

- [x] Created branch `feature/market-wiring` from current Market branch commit `c6b7d13578e82bbcb0724aaca1969d3fcccd35ca`.
- [x] Inspected Market screen activities/policies, repository/query builder/config code, Rules, indexes, query map and specifications.
- [x] Identified fixture data and action gaps; identified direct query/read bypasses.
- [x] No feature code changed.
- [x] No Rules, indexes or test files changed.
- [ ] Build green — blocked before W0 by the compile error above; fix must be a subsequent wiring part, not smuggled into the audit commit.
