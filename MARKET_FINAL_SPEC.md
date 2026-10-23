# U2U Market: FINAL SPEC (student app + admin panel + help system)

This file REPLACES the earlier MARKET_SPEC.md and the earlier ranking/admin ideas. Where they differ, this file wins.

Reference visuals (use them as the source of truth for layout, spacing, colours, text):
- `student/light` and `student/dark`: 32 screens, numbered 01-32. `student/html` has the exact HTML/CSS.
- `admin/`: 9 desktop pages (light only). `admin/html` has the exact HTML/CSS.
- Screens are 360x800 dp (student) and 1280x800 (admin). Text in the mockups is the real copy; use it.

## 0. Ground rules (read first, these prevent most mistakes)

1. **Firebase FREE (Spark) plan only.** No Cloud Functions, no Cloud Storage, no payments, no server code. Anything that needs a server is out of scope.
2. **The marketplace is small and simple.** Do not add features that are not in this file. No payments, no escrow, no recommendations engine, no real-time feed, no bidding wars.
3. **Do not change existing U2U logic**: auth, friend system, the existing `chatConversations` engine, Nearby transfer, existing collections, and existing view IDs. Market chats use the separate `marketConversations` collection and are text-only in v1; they must not reuse or modify the existing U2U chat/Nearby engine.
4. **Security lives in Firestore rules, not in hidden buttons.** Never trust the client. Every restriction below needs a rule, and I want the rules shown to me before implementing.
5. **Admins must never be able to read private chats.** Market task chats are also private; admin panel never reads `chats`.
6. **Smoothness is the top priority** (section 11). If a choice conflicts with smoothness or the free-tier read budget, choose smoothness and tell me.
7. Use the existing Pachattar% tokens (Paper/Night/Auto, lime #D4F34A, Poppins, dark green hero). The mockups already use them.
8. If anything here is unclear or conflicts with the repo, STOP and ask me, do not guess.

## 1. Scope

Market = two tabs: **Requests** (tasks people need help with, with proposals) and **Offers** (things people offer). Plus: My tasks, My offers, Notices, Help and safety (rules, FAQs, support, my reports, appeals), Market settings, Blocked users, and a separate **Admin web panel** for moderation.

Entry: "Market" card on U2U Home (screen 01). Market has its own back arrow; the U2U bottom nav is hidden inside Market. The Market header has Search and a menu button (screen 04) with a red dot when there are unread notices. Menu items: My tasks, My offers, Notices, Help and safety, Market settings.

## 2. Navigation map

```
U2U Home (01) -> Market
  Requests tab (02) | Offers tab (03)   [menu button -> Market menu (04)]
  Requests: task card -> Task detail
     helper view (05): Send proposal (11), Save, Report (20)
     poster view (06): View proposals (12) -> Accept sheet (13) -> Task chat (14) -> Mark complete -> Rate (16)
  Offers: offer card -> Offer detail (17) -> Message (separate text-only Market chat), Report (20)
  FAB: Post a task (07 -> 08 -> 09 -> Rules sheet 10) | Post an offer (18)
  Menu (04):
     My tasks (15) | My offers (19) | Notices (22 -> 23 -> Appeal 24)
     Help and safety (26) -> FAQs (27), Posting rules and safety (28), Contact support (29), My reports (30)
     Market settings (31) -> Blocked users (32)
  Suspended account: posting actions show screen 25
  States reference: 21 (loading, empty, offline, error, expired)
```

## 3. Data model (Firestore)

Use these exact names. All timestamps are Firestore Timestamps.

`tasks/{taskId}`: title (5-80), category, courseTag?, description (20-500), budgetType (free|fixed|negotiable), budgetAmount?, deadline, skills[] (max 5), status (open|assigned|completed|cancelled|expired), moderation (ok|under_review|hidden|removed), pinned (bool, default false), posterId, posterName, posterInitials, proposalCount, assignedTo?, reportCount, createdAt, updatedAt, expiresAt (= createdAt + 14 days). An open task can transition to `expired` only by its owner after `expiresAt`, in the same batch that decrements `marketProfiles/{posterId}.openTasks`. An assigned task cannot be cancelled; only its poster can mark it completed.
`tasks/{taskId}/proposals/{userId}` (doc id = proposer uid, so one proposal per user): proposerId (must equal the document id and authenticated UID on create; immutable), message (20-400), price, deliveryDays, status (sent|shortlisted|accepted|declined|withdrawn), proposerName, proposerInitials, createdAt, updatedAt.
`offers/{offerId}`: title, category, courseTag?, description (<=400), priceType (free|swap|amount), priceAmount?, thumbnail? (compressed JPEG base64 <=150KB inside doc), status (active|paused|expired|removed), moderation (ok|under_review|hidden|removed), pinned, sellerId, sellerName, sellerInitials, reportCount, createdAt, renewedAt (default = createdAt), updatedAt, expiresAt (= renewedAt + 30 days).
`ratings/{taskId_fromUid}`: taskId, fromUid, toUid, stars (1-5), tags[], comment (<=200), createdAt.
`userStats/{uid}`: ratingSum, ratingCount, completedCount (update in the same batch that writes the rating, with `lastMutation.kind == "rating"`). `ratings` and `userStats` are readable by any signed-in user; therefore neither collection may contain private profile, contact, or other confidential information.
`reports/{targetType_targetId_reporterId}` (doc id makes it one report per user per item): targetType (task|offer|user), targetId, reporterId, reason (graded_work|scam|spam|abusive|erp_login|other), createdAt, status (open|under_review|action_taken|dismissed).
`users/{uid}` additions (new map `market`): skillTags[], blockedUids[], status (active|suspended|banned), suspendedUntil?, warnings (number), settings {alertProposals, alertUpdates, alertNotices, alertMatchingTasks}.
`users/{uid}/notices/{noticeId}`: type (warning|removal|support_reply|appeal_result|announcement|activity), title, body, relatedType?, relatedId?, read (bool), createdAt, canAppeal (bool), appealed (bool).
`support/{ticketId}`: userId, type (listing|account|bug|person|other|appeal), subject, body, relatedType?, relatedId?, status (open|replied|resolved), replies[] (max 20, each {from: user|admin, text, at}), createdAt, updatedAt.
`admins/{uid}`: {} (written ONLY from the Firebase console, never from any client).
`adminLogs/{logId}`: adminId, action, targetType, targetId, reason, note, createdAt.
`config/market` (single doc, cached by clients): announcement {text, active, until}, blockedWords[], rankWeights {newness, rating, match} (each non-negative; sum 100), limits {openTasks:3, proposalsPerDay:10, proposalsPerTask:15, activeOffers:5, reportsToHide:3}. Limits are displayed read-only in admin UI; changing them requires a rules deploy.

Notes: no collection stores images other than the small offer thumbnail. **Market chats are separate and TEXT-ONLY for v1:** use `marketConversations/{kind}_{listingId}_{uidLow}_{uidHigh}/messages/{messageId}` with lexicographically sorted participant UIDs. No files, reactions, typing indicators, Nearby transfer, or existing `chatConversations` engine are used for Market chats. The existing U2U chat and Nearby rules/behavior remain unchanged. Every student-initiated Market write requires `email_verified == true` and an existing `profiles/{uid}` document. `posterName`, `proposerName`, and Market-message `senderName` must exactly match that document's `displayName`. Verified admin-only Market operations require an `admins/{uid}` document but do not require `profiles/{uid}` or `marketProfiles/{uid}` for the admin actor.

### Timestamp and deterministic-ID protocol

The client creates `createdAt` and `renewedAt` using the phone's local clock **minus 30 seconds**. Rules accept these fields only when their value is at or before `request.time` and no earlier than `request.time - 2 minutes`. Derive `expiresAt` from that exact same timestamp: task `expiresAt = createdAt + 14 days`; offer `expiresAt = renewedAt + 30 days`. Keep `updatedAt` and mutation timestamps server-timestamped (`request.time`).

Use deterministic IDs: report `{targetType}_{targetId}_{uid}`; appeal ticket `appeal_{noticeId}_{uid}`; rating `{taskId}_{fromUid}`; Market conversation `{kind}_{listingId}_{uidLow}_{uidHigh}` where the participant UIDs are sorted lexicographically. `proposerId` must equal the proposal document ID and `request.auth.uid` on create and is immutable.

## 4. Security rules (propose first, then implement; must enforce)

- All student-initiated Market writes require a signed-in user with `email_verified == true` and an existing `profiles/{uid}`. Create/edit task, offer, or proposal content only when `marketProfiles/{uid}.status == "active"`, or when suspension has expired according to `request.time`; banned users never regain posting rights. Verified admins are exempt from the U2U-profile prerequisite only on explicit admin-only branches. Limits remain 3 open tasks, 5 active offers, 15 proposals per task, and 10 proposals per rolling 24-hour window.
- Task/offer content edits use the same validation as creation and require `marketUserCanPost(owner)`. Suspended/banned owners can still perform permitted lifecycle/cleanup transitions, including cancellation/expiry/removal with the corresponding counter mutation, but cannot edit content. Owners cannot change `moderation`, `pinned`, or `reportCount`. An `open` task becomes `expired` only after `expiresAt` and only by its owner with the matching open-task counter decrement; an assigned task cannot be cancelled.
- `reportCount` may only increase by exactly 1, and only in the same batch that creates a deterministic report document `{targetType}_{targetId}_{uid}` for that reporter. The client-side `reportCount < 3` feed filter is redundant once rules move an item to `under_review` after the report threshold, but remains as a harmless UI safety net.
- Proposal doc: create/edit only by its own UID while the task is `open`, `proposalCount < 15`, and the proposer is not the poster. Every proposal stores immutable `proposerId` equal to both its document ID and the authenticated UID. The poster can read all proposals below their task and change proposal `status`. The Applied collection-group query must filter `proposerId == auth.uid` and order by `updatedAt` descending; other users' proposals must not be exposed. Withdrawing a proposal does **not** decrement `proposalCount`. Rules do not check `expiresAt` on proposal creation, so the client must block proposals on expired-but-still-open tasks.
- Accept = one transaction/batch: proposal accepted, task assigned, assignedTo set, other proposals declined. Rules must reject a second accept.
- Ratings: only the two participants of a completed task, one per user per task. Both the rating document and the target user's `userStats` update, including the `lastMutation` entry with kind `rating`, must be written in the same batch.
- `users/{uid}/notices`: read/update(`read`, `appealed`) by owner; create only by admins. Appeal support tickets use deterministic ID `appeal_{noticeId}_{uid}`.
- `support`: create by owner; owner can append to `replies` as `from: user`; admins can read/write all.
- `admins`: no client writes. Admin check function `isAdmin()` = `exists(/databases/$(database)/documents/admins/$(request.auth.uid))`. Evaluate owner checks BEFORE `isAdmin()` so normal users do not trigger extra reads.
- Admins can update `moderation`, `pinned`, user Market fields, create notices, reply to support, read reports, write `adminLogs`, and edit `config/market`. Admins have no access to `marketConversations` or their messages and cannot read private chats. The separate admin account needs `admins/{uid}` but no U2U `profiles/{uid}` or `marketProfiles/{uid}` document. Ratings and `userStats` remain readable to any signed-in user.
- `config/market` readable by all signed-in users, writable by admins only.

## 5. Ranking (simple, on-device, no extra reads)

Rank only the items already loaded (a page is 15-20 items, cache-first). No server-side sorting beyond `createdAt` desc.

**Offers**
```
newness  = max(0, 1 - ageDays / 30)            // ageDays from renewedAt (or createdAt)
quality  = ((5 * 4.2) + ratingSum) / (5 + ratingCount) / 5   // 0..1, new sellers start at 0.84
match    = 1 if offer category or courseTag matches my skillTags or my course, else 0
score    = (w.newness * newness + w.rating * quality + w.match * match) / 100
```
Default weights from `config/market.rankWeights`: newness 60, rating 30, match 10.

**Order**: pinned first (max 3 pinned, newest pin first), then by score desc, then newest. After sorting, fix repeats: never show two consecutive items from the same seller (swap with the next different seller).

**Tasks**: pinned first; then tasks matching my `skillTags` or course; then newest first. Hide tasks whose deadline has passed.

**Visibility filter (apply BEFORE ranking)**: show an item only if `moderation == "ok"`, `reportCount < limits.reportsToHide` (3), status is open/active, not expired, and owner is not in my `blockedUids` (and I am not in theirs if available). Items I own always show in My tasks / My offers with their status. The report-count condition is an intentionally redundant UI safety net after report-triggered `under_review` moderation.

**Renew** (offers): owner can renew once every 7 days; sets `renewedAt = now` and `expiresAt = now + 30 days`. Button is disabled with "Renew available in N days" otherwise. Posting the same title again within 7 days by the same user is treated as a duplicate and blocked.

Put scoring in ONE pure, unit-tested class. The weights come from `config/market` (cached) so admin can change them without an app update.

## 6. Student screens (what each must do)

Numbers match the file names in `student/`.

**01 Home card**: "Market. Get help or offer yours. N new tasks". N counts cached tasks created after my last Market visit (no extra reads).

**02 Requests / 03 Offers**: segmented tabs keep their own scroll and data (no reload on switch). Search icon opens an inline search over the loaded cached list (title, category, course). Filter chips are on-device. Cards show what the mockup shows. Pull-to-refresh and infinite scroll (15-20 per page). FAB changes per tab (Post a task / Post an offer). If `config/market.announcement.active` is true and not expired, show a small dismissible banner on top of the list.

**04 Market menu**: bottom sheet. Notices row shows an unread badge (count of unread notices).

**05 Task detail (helper)**: info tiles, details, skills, Save (local bookmark), Report (sheet 20). Bottom button states: "Send proposal" | "Proposal sent. View" | disabled "Task closed" | disabled "Proposal limit reached". Contact and chat unlock only after accept.

**06 Task detail (poster)**: status chip, Edit (only while open; if proposals exist only the description is editable), proposals summary opens 12, Cancel task (confirm dialog) sets status cancelled and the proposers see "Task cancelled" in Activity.

**07-09 Post a task (3 steps) + 10 Rules sheet**: validation inline, Next disabled until valid, Back keeps values, draft saved locally (survives rotation, background, process death). The rules sheet appears once per install and again if the rules version changes; "I understand" then submits. Submit is optimistic: the task appears instantly with "Posting...", confirmed or rolled back with Retry (draft kept).

**11 Send proposal**: required message/price/delivery; shows "Proposals left today: n of 10". Edit or withdraw while status is `sent`. Not allowed on own task; one per task (doc id = uid).

**12 Proposals (poster)**: filter chips (All, Shortlisted, Cheapest, Top rated) on-device. Actions Decline / Shortlist / Accept. **13 Accept sheet**: shows consequences and the money note; "Accept and open chat" runs the transaction, then opens 14. A second tap or a second device must fail gracefully.

**14 Task chat (Market chat, text-only v1)**: use the separate `marketConversations` and its `messages` subcollection, with a pinned task card (title, price, due, status, View task, Mark complete for poster only). Created in the same accept transaction/batch without needing a friend request. Text messages only. No file sending, reactions, typing indicators, Nearby transfer, or reuse of the existing U2U `chatConversations` engine.

**15 My tasks**: Posted | Applied tabs; statuses as shown; works from cache offline.

**16 Complete and rate**: poster marks complete (confirm) -> both users are asked to rate. Rating is skippable. Updates `userStats` in the same batch.

**17 Offer detail / 18 Post an offer**: thumbnail is optional, compressed on-device to <=150KB JPEG stored in the doc; reject larger. Message opens a separate text-only Market conversation (creating it without a friend request, carrying an offer reference card). No files, reactions, typing indicators, or Nearby transfer are available in Market chats in v1.

**19 My offers**: tabs Active | Paused | Expired. Actions: Edit, Pause/Resume, Renew (7-day limit, see section 5), Delete (soft: status removed by owner, with confirm dialog). Paused/expired items are hidden from the feed.

**20 Report sheet**: reason radio, optional "Also block this user". Creating a report = batch: create `reports/{...}` + increment `reportCount` by 1. Max 10 reports per user per day. Button label turns into "Reported" afterwards (cannot report the same item twice).

**21 States**: every list/screen implements loading skeleton (only when no cache), empty, offline banner, error with Retry (saved data stays visible), expired/closed.

**22 Notices** (tabs Activity | U2U team): Activity = proposal received, shortlisted, accepted, declined, task cancelled, new rating (generated from data the app already reads, no polling). U2U team = admin notices (warning, removal, support reply, appeal result, announcement), stored in `users/{uid}/notices`. Fetch on Market open, `limit 20`, ordered by `createdAt` desc. Unread dot until opened. Team notices older than 60 days are hidden.

**23 Notice detail**: shows reason, rule, what happened, and buttons: **Appeal this decision** (only if `canAppeal && !appealed`) and Read posting rules.

**24 Appeal**: one appeal per decision. Creates `support/{id}` of type `appeal` linked to the notice (relatedType/relatedId) and sets `appealed = true` on the notice. Shows "We reply in Notices, usually within 3 days."

**25 Suspended / banned screen**: shown instead of any posting action when `market.status` is suspended or banned (`suspendedUntil` in the future). Shows reason, days left, what the user can still do (browse, message existing chats, read notices, contact support) and cannot do (post tasks/offers, send proposals). Buttons: Rules, Appeal. When `suspendedUntil` passes, treat as active (check on open; admin does not need to un-suspend).

**26 Help and safety**: search over local FAQ list; tiles to Posting rules, Safety tips, FAQs, My reports; popular questions; "Contact support" button. The FAQ and rules text is bundled in the app (no reads).

**27 FAQs**: category chips + accordion (one open at a time). Required entries: how to get a proposal accepted, does U2U handle payments (no, never), why was my listing removed, how to report someone, how long a task stays open (14 days), can I edit a task after posting, how to appeal a decision.

**28 Posting rules and safety**: Allowed, Not allowed, Stay safe, escalation ladder (1 Warning, 2 pause 7 days, 3 pause 30 days, 4 Ban). Same copy as the rules sheet.

**29 Contact support**: type chips (Listing problem, Account, App bug, Report a person, Other), subject (5-80), details (10-500), optional attach a listing (stores type and id only). Limit 5 open tickets per user. Replies come back as notices of type `support_reply` and also append to `replies`.

**30 My reports**: list of my reports and status (Received, Under review, Action taken, Dismissed). Never reveals the other person's identity beyond the reported item.

**31 Market settings**: my skill tags (used for matching), alert toggles (stored in `market.settings`, local notifications only when the app is open or from local checks), links to Blocked users, Help, My reports.

**32 Blocked users**: list with Unblock. Blocking hides their tasks, offers and proposals from me, and (enforced in rules by checking the target's `blockedUids`) prevents them from starting a new Market chat with me.

## 7. Moderation logic (state rules)

- Visible in feed: `moderation == "ok"` and `reportCount < 3`. At 3 reports every client hides it (client filter). That is only a temporary hide: the admin decides.
- Admin actions (each writes an `adminLogs` doc in the same batch, and sends a notice to the user when it affects them):
  - **Dismiss report**: report status dismissed; item stays; reportCount is NOT reset unless admin presses "Clear reports" (optional).
  - **Hide**: `moderation = hidden` (soft; restorable).
  - **Hide and warn**: hide + `market.warnings += 1` + notice type `removal` with the rule reason and `canAppeal = true`.
  - **Delete**: `moderation = removed` (soft delete, restorable for 30 days). Permanent delete only from the Removed filter, manually.
  - **Warn user**: `warnings += 1` + notice type `warning`.
  - **Suspend 7 / 30 days**: `market.status = suspended`, `suspendedUntil = now + N days`; also set all of the user's active listings to `moderation = hidden` in one batch (max 500 writes; chunk if more); notice with reason and `canAppeal`.
  - **Ban**: `market.status = banned`; hide all their listings; notice. Lifting a ban/suspension is a manual action from the user page.
  - **Pin / Unpin**: `pinned` true/false, max 3 pinned at a time (admin UI blocks a 4th).
  - **Approve appeal**: restore listing (`moderation = ok`), notice `appeal_result`, ticket resolved. **Reject appeal**: notice with reason, ticket resolved.
- Suggested next step shown to admin: based on `warnings` (0 -> warning, 1 -> warning again, 2 -> suspend 7 days, 3 -> suspend 30 days, 4+ -> ban). It is only a suggestion; admin chooses.
- Blocked-word check when posting (client-side, using `config/market.blockedWords`): matching post is not published immediately; it is saved with `moderation = under_review` and the user sees "Sent for review". Admin approves (sets ok) or removes.
- Mass-report protection: auto-hide only goes to "under review" (client filter), never to deleted. Accounts younger than 7 days: their reports are stored but the client counts them at half weight (use `reportCount` as written; the admin panel shows reporter account age so a human can judge).

## 8. Admin panel (web app on Firebase Hosting)

- Static web app (HTML/JS, Firebase JS SDK), deployed on a non-obvious URL. It is NOT part of the student APK.
- Sign in with Google (a separate admin account with 2-step verification). After sign-in, check `admins/{uid}`; if missing, show an empty "No access" state and sign out. All data protection is in rules, so a leaked URL gives nothing.
- Pages (match `admin/`): 01 Login, 02 Dashboard, 03 Reports, 04 Listings, 05 Users, 06 User detail, 07 Support and appeals, 08 Rules and settings, 09 Audit log.
- **Dashboard**: counts via Firestore `count()` aggregation (cheap): active offers, open tasks, reports today, open appeals, students. 14-day listing chart (use `createdAt` count queries, cache for 10 minutes). "Needs attention" = top 3 open reports. Recent actions = last 3 audit logs.
- **Reports**: left list of open reports grouped by target, most reports first; right detail with the item, reasons breakdown, seller history, message template dropdown (rule text), internal note, and action buttons (Hide and warn, Hide only, Dismiss, Suspend 7, Suspend 30, Ban). Every action follows section 7.
- **Listings**: search and filters (All, Offers, Tasks, Reported, Under review, Hidden, Pinned), row actions (Hide, Restore, Pin/Unpin, Delete). Paginate 25 rows.
- **Users**: search by name or student ID; filters; user detail with tabs (Listings, Reports against, Ratings, Timeline) and actions (Send warning, Suspend, Ban, Lift).
- **Support and appeals**: ticket list and thread; reply (appends to `replies` and creates a `support_reply` notice), quick reply chips, Resolve; for appeals: Approve and restore / Reject appeal.
- **Rules and settings**: announcement banner (text, on/off, until date), blocked words (add/remove), and ranking weights (3 non-negative numbers that must sum to 100; Save is disabled otherwise) are editable. Display limits (3 open tasks, 10 proposals/day, 15 proposals/task, 5 active offers, 3 reports to hide) as read-only values; changing a limit requires a reviewed Firestore Rules deployment. Only announcement, `blockedWords`, and `rankWeights` are editable in the admin panel. Each rank weight must be individually non-negative as well as summing to 100.
- **Audit log**: read-only table of `adminLogs`, filter by action, search by target.
- Admin reads must be paginated (25 per page) and use `count()` where possible to stay in the free quota.
- Admin panel is light theme only, desktop first. Keep it usable down to 1024px wide.

## 9. Notifications (free plan)
In-app only: the Notices screen and badges. Local notifications only for things the app already learns while open or via cheap periodic checks. No background push (needs Cloud Functions).

## 10. Free-tier discipline (enforce and measure)
- 15-20 items per page, local persistent cache, fetch only changed items (`updatedAt > lastSync`), no realtime listener on whole feeds (listeners only on the single open task or open chat).
- `config/market` is read once per app open and cached for 6 hours.
- Notices: one query on Market open (limit 20).
- Limits enforced in app AND rules: 3 open tasks, 5 active offers, 15 proposals per task, 10 proposals per day, 10 reports per day, 5 open support tickets.
- Tasks expire after 14 days, offers after 30 (or renewal). Hide expired items client-side; owners clean up their own expired items when they open My tasks / My offers.
- Combine related writes in one batch. Do not write on every keystroke.
- Add a debug read/write counter per screen.

## 11. SMOOTHNESS REQUIREMENTS (apply at every step)
1. Cache-first: every screen renders saved data on the first frame; the network only refreshes quietly.
2. No layout jumps: skeletons match real card size; updates fade in place.
3. Instant feedback: pressed state under 100 ms; optimistic UI with rollback and a clear message.
4. 60 fps lists: recycled lazy lists, stable ids, diffing; parsing, ranking and image work off the main thread.
5. No stuck loaders: every loader times out (10-15 s) to saved data or an inline error with Retry; loading flags reset in finally/failure paths.
6. State survives rotation, background and process death: tab, scroll, search text, filters, drafts.
7. Safe resume: after long background time the screen is interactive within ~1 s from cache, then revalidates quietly; no invisible overlays that block touches.
8. Race safety: late responses never overwrite newer data; accept/propose/report use batches or transactions; duplicate taps ignored.
9. Network hygiene: timeouts, retry with backoff, request de-duplication, cancel on screen exit.
10. Measure: log first-render time per screen and Firestore call duration (debug). Targets: cached screen opens under 300 ms, tab switch under 100 ms, smooth scrolling on a mid-range phone.
11. Respect system insets, reduce-motion setting, and Paper/Night/Auto with no flicker.

## 12. Common mistakes to avoid (the AI must double-check each)
- Putting admin buttons or admin code in the student APK.
- Trusting the client for suspended/banned checks (rules must enforce posting limits and status).
- Hard-deleting listings (use soft delete; subcollections would be orphaned).
- Auto-deleting on reports (only hide for review).
- Letting `reportCount` be changed freely (+1 only, with the report doc).
- Forgetting to write `adminLogs` and notices for every admin action.
- Using realtime listeners on whole collections, or reading all proposals for counts (use `proposalCount`).
- Double accept, double propose, double report (doc ids and transactions prevent them).
- Showing contact details before accept.
- Changing existing chat/friend/Nearby logic or view IDs.
- Adding payments or Storage/Functions dependencies.
- Forgetting loading, empty, offline, error and expired states.
- Hiding an owner's own items from them in My tasks / My offers.

## 13. Build order (small steps; build + unit tests after each; send APK after steps 3, 6 and 9)
1. Data model, `config/market`, security rules (show me first), local cache layer, scoring class with unit tests.
2. Market entry, Requests/Offers lists, filters, search, pagination, states, ranking.
3. Task detail, report, block, My tasks.
4. Post a task (3 steps, drafts, rules sheet), blocked-word check.
5. Proposals, shortlist, accept transaction, task chat link, complete and rate.
6. Offers: post, detail, My offers (pause, renew, delete).
7. Notices, notice detail, appeals, suspended screen, Market settings, Blocked users.
8. Help and safety: help hub, FAQs, rules page, contact support, My reports.
9. Admin web panel (all 9 pages) + deploy steps for Firebase Hosting, how to add the first admin in the console.
10. Polish: Night mode check, accessibility (44dp targets, content descriptions, font scaling), performance pass using the logs.

## 14. Test checklist (accounts: A poster, B helper, C troublemaker, Admin)
- Full flow: A posts, B proposes, A shortlists and accepts, a separate text-only Market chat opens with pinned card, A (the poster) completes the task, and both participants may rate. Confirm there is no file send, reaction, typing indicator, Nearby-transfer, or existing U2U chat engine dependency for Market chats.
- Limits: 16th proposal, 4th open task, 6th offer, 11th proposal in a day, 11th report in a day all blocked (UI and rules). Own task cannot be proposed on.
- Double-tap Accept and accept from two devices: only one result.
- Ranking: fresh well-rated matching offer ranks above an old unrated one; pinned stays on top; no same seller twice in a row; renew limited to once per 7 days.
- Reports: one per user per item; 3 reports hide for review; admin dismiss/hide/hide and warn/delete/restore; reporter sees status in My reports.
- Moderation: warning notice arrives; suspension blocks posting (rules, not only UI) and shows screen 25; suspension expires on its date; ban; lift.
- Appeal: user appeals once; admin approves (listing restored) or rejects; result arrives in Notices.
- Support: ticket, admin reply, user sees it in Notices; Resolve.
- Blocked words: matching post goes to review and the user sees "Sent for review".
- Block user: their items disappear for me and they cannot start a Market chat with me. Confirm the deterministic Market conversation ID is `{kind}_{listingId}_{uidLow}_{uidHigh}` with sorted participant UIDs.
- Admin security: a normal account cannot read/write `admins`, `adminLogs`, support of others, `config` write, or change `moderation`/`pinned`; admin cannot read `marketConversations` or private U2U chats. Verify each admin operation works with an `admins/{uid}` document and no `profiles/{uid}` or `marketProfiles/{uid}` document. Verify non-admin students without `profiles/{uid}` are denied marketProfile creation, task/offer/proposal creation, and Market-message sends.
- Offline: cached feed visible, posting blocked clearly, drafts kept; online resumes.
- Rotate, background 1 hour, kill process and reopen: tab, filters, scroll and draft restored.
- Paper and Night on every screen; large font size; keyboard open on every form.
- Performance: 100 items scroll smoothly, cold start to cached Market under 1 s, read counter within budget.


## 15. Counter transition contract

These counters represent capacity/rolling-window usage, not a historical event total. Every counted transition must update its target document and its relevant profile counter in the same atomic transaction or batch. If the transition does not change counted eligibility, it must not change the counter. Never apply both an aggregate account moderation reset and individual listing decrements in the same operation.

### Counter meanings

- `marketProfiles/{uid}.openTasks`: tasks owned by the student whose lifecycle status is `open` and moderation status is `ok` or `under_review`. An open task hidden/removed by admin moderation releases its slot; a report moving `ok` to `under_review` does not release a slot.
- `marketProfiles/{uid}.activeOffers`: offers owned by the student whose lifecycle status is `active` and moderation status is `ok` or `under_review`. Paused, expired, removed, hidden, or otherwise non-counted offers do not occupy an active-offer slot.
- `tasks/{taskId}.proposalCount`: number of proposal documents created for that task. It is incremented once on proposal create, and is not decremented on withdraw, decline, acceptance, or task completion. A per-user proposal is unique by document ID.
- `marketProfiles/{uid}.proposalWindowCount`: proposals created by the user in the current rolling 24-hour window. Each newly created proposal consumes one; editing, withdrawing, declining, or accepting does not refund the usage. The window reset behavior follows the same server-validated start timestamp used by the Rules.
- `marketProfiles/{uid}.reportWindowCount`: reports created by the user in the current rolling 24-hour window. Each allowed report consumes one; changing a report's status does not refund usage. The window may reset only after the existing rolling-window interval has passed.
- `tasks/{taskId}.reportCount` and `offers/{offerId}.reportCount`: unique reports against that listing; increment exactly once with its deterministic report document. Dismissing a report does not decrement this number. A separate explicit Clear reports action, if implemented, needs its own reviewed rule and audit design.
- `marketProfiles/{uid}.openSupportTickets`: support tickets in `open` or `replied` status. Creating a normal ticket or appeal increments once. Admin replies leave it unchanged. The first resolution changes the ticket to `resolved` and decrements once; repeat resolution is denied. Appeal restoration of a listing and resolution of its ticket are separate transactions and affect different counters.
- `marketProfiles/{uid}.warnings`: lifetime warning count, increased by exactly one for a warning action. Suspension, ban, appeal outcome, and lift do not silently decrement it.

### Required counter transitions

| Operation | Counter rule |
| --- | --- |
| Create an open eligible task | `openTasks + 1` |
| Owner cancels or expires an open task | `openTasks - 1`; expiry only after `expiresAt` |
| Accept a proposal (task `open -> assigned`) | `openTasks - 1` in the same acceptance write that assigns the task, accepts the proposal and creates the Market conversation |
| Complete an already assigned task | **No counter change**; acceptance already released the slot |
| Admin hides/removes a counted open task | `openTasks - 1` |
| Admin restores an eligible hidden/removed open task to `ok` | `openTasks + 1`, only if the profile remains below the task limit |
| Pin/unpin a task, or moderate it without changing counted eligibility | No counter change |
| Create an active eligible offer | `activeOffers + 1` |
| Pause, expire or remove an active offer | `activeOffers - 1` |
| Remove an already paused offer | **No counter change** |
| Resume a paused, unexpired offer | `activeOffers + 1`; subject to the active-offer limit |
| Renew an already active offer after the renewal interval | No counter change |
| Renew a paused/expired offer after the renewal interval | `activeOffers + 1`; subject to the active-offer limit |
| Admin hides/removes a counted active offer | `activeOffers - 1` |
| Admin restores an eligible hidden/removed active offer to `ok` | `activeOffers + 1`, only if the profile remains below the active-offer limit |
| Pin/unpin an offer, or moderate without changing counted eligibility | No counter change |
| Suspend/ban an account | Aggregate operation: hide the user's counted active listings and set `openTasks` and `activeOffers` to zero in the same transaction; do not also decrement one-by-one |
| Report a task/offer | Listing `reportCount + 1` and reporter `reportWindowCount + 1`; moderation to `under_review` at the threshold does not change the owner's slot counter |
| Create a support ticket or appeal | `openSupportTickets + 1` |
| Reply to an open/replied support ticket | No `openSupportTickets` change |
| Resolve a support ticket or appeal | `openSupportTickets - 1` exactly once in the same write as ticket resolution |
| Warning | `warnings + 1` exactly |
| Decline, withdraw or accept a proposal; mark an assigned task complete | No decrement of `proposalCount` or any profile counter other than the documented acceptance `openTasks - 1` |

### Edge cases and regression expectations

- Counters follow the before/after counted state, not just the label a UI button uses. Removing a paused offer must succeed without a profile counter mutation and must fail if the same batch tries to decrement `activeOffers` again.
- An accepted task releases its `openTasks` slot once. The later `assigned -> completed` transition is status-only and must not decrement the profile again.
- During suspension/ban, a listing slot is released by the aggregate profile reset. When an admin later restores an eligible listing, only that restore operation increments the matching counter; assigned/completed tasks and paused/removed offers do not regain a slot.
- If a profile counter is already inconsistent or zero when an operation requires decrement, reject and surface the existing “counter needs attention” flow; never write a negative counter to force the operation through.
- Each operation-specific `lastMutation` marker is consumed once per target transition. For task and offer changes that can release or reclaim a slot, the target listing status/moderation and profile delta must validate each other in the same atomic write.
