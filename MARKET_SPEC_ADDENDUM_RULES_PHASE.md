# Addendum to MARKET_FINAL_SPEC.md (decisions made during the Firestore rules phase)

Where this addendum and MARKET_FINAL_SPEC.md differ, THIS FILE WINS. The Firestore rules package (firestore.rules, firestore.indexes.json, tests) is the source of truth for data shapes.

## 1. Data model changes
- Market account state lives in `marketProfiles/{uid}` (NOT in `users/{uid}.market`). Fields: uid, skillTags[], blockedUids[], status (active|suspended|banned), suspendedUntil, warnings, settings{alertProposals, alertUpdates, alertNotices, alertMatchingTasks}, openTasks, activeOffers, proposalWindowCount, proposalWindowStartedAt, reportWindowCount, reportWindowStartedAt, openSupportTickets, lastMutation, createdAt, updatedAt. The client creates it once with fixed safe defaults and can never edit status, warnings, suspension or counters afterwards.
- `tasks/{taskId}/proposals/{userId}` documents now also carry `proposerId` (== document id == author uid, immutable). The helper's "Applied" list uses a collection-group query: `collectionGroup('proposals').where('proposerId','==',myUid)` ordered by updatedAt desc.
- `marketConversations/{conversationId}`: id = `kind_listingId_uidLow_uidHigh` (uids sorted). Fields: participants[2], kind (task|offer), listingId, listingOwnerUid, taskId|offerId, createdAt, updatedAt, optional lastMessage {text<=100, senderUid, at}, optional lastReadAt (per participant). The Chats list reads these fields, not the messages, to show last message and unread state. The task conversation is created in the SAME batch as the accept.
- `lastMutation` {kind, operation, targetId, at (== request.time), targetType?} on the profile ties every counter change to the exact document written in the same batch. Every Market write must go through ONE repository class that builds these batches. Do not write Market documents from anywhere else.
- Server time: `lastMutation.at`, `updatedAt` and most timestamps must be `serverTimestamp()`. `createdAt` (tasks) and `renewedAt` (offers) are client timestamps close to the server time, and `expiresAt` must equal createdAt + 14 days (tasks) or renewedAt + 30 days (offers), computed on the client from the same value.

## 2. Limits and windows
- The limits (3 open tasks, 10 proposals per window, 15 proposals per task, 5 active offers, 3 reports to hide) are fixed inside the security rules. In the admin panel the Limits card is READ-ONLY (mockup 08 shows editable fields: ignore that). Only announcement, blocked words and ranking weights are editable. Ranking weights must be non-negative and sum to 100.
- Daily limits are rolling 24-hour windows started by the first action. UI copy says "resets every 24 hours", never "today".

## 3. Moderation notes
- At 3 reports the rules move an `ok` item to `under_review` automatically as part of the reporting batch. The admin decides afterwards.
- Pins: admin only, max 3 enforced by the admin UI; the student app shows at most the first 3 pinned items.
- Admin uses a separate Google account that is NOT a U2U student. Existing U2U chat and Nearby rules are unchanged (the rules file only adds Market blocks).

## 4. Client rules for writing safely
- Content edits of own listings are validated like creates and require the user to be allowed to post. Cancel, expire, pause, remove and complete are lifecycle transitions that stay allowed and must carry the matching counter decrement.
- A Market write that fails with permission-denied must show a friendly message and never loop. If the phone clock is far from real time, show "Check your phone's date and time".
- Reads: feed queries must carry the constraints the rules require (moderation == 'ok', status open/active) and use the indexes listed in INDEX_QUERY_MAP.md. Expiry and reportCount >= 3 are filtered on the client.

---

# Post-review additions (sections 5-8)

These were added after the rules review and the first successful CI run. They do not change sections 1-4; they fill gaps found in review. Where anything here conflicts with firestore.rules, firestore.rules wins and this file must be corrected.

## 5. Statuses, lifecycle and completion
- Task `status` values: open | assigned | completed | cancelled | **expired**. A task moves open -> expired only by its owner, only after `expiresAt`, and with the matching `openTasks` decrement (lastMutation kind `task`, operation `close`).
- Offer `status` values: active | paused | **expired** | **removed**. `removed` is the owner's soft delete.
- Only the task **poster** can mark a task `completed` (the assigned helper cannot). Both participants can then rate it.
- An `assigned` task cannot be cancelled; it can only be completed.
- Accepting a proposal also closes the poster's open-task slot (`openTasks` decrement) in the same batch.
- Withdrawing a proposal does NOT decrement the task's `proposalCount`.
- The rules do not check `expiresAt` on proposals. The client must not allow a proposal on an expired-but-still-`open` task.
- The `reportCount < 3` client filter is now redundant (3 reports already flips the item to `under_review`) but stays as a harmless safety net.

## 6. Writing correctly (client checklist)
- **Phone clock.** `createdAt`/`renewedAt` must be <= server time and not older than 2 minutes. The client uses local time **minus 30 seconds** so a phone that is slightly ahead is not denied. `expiresAt` = that same value + 14 days (tasks) or + 30 days (offers).
- **Names.** `posterName`, `proposerName` and message `senderName` must equal `profiles/{uid}.displayName` exactly. The student's U2U `profiles/{uid}` document must exist before they can create a marketProfile, listing, proposal or chat message. Admin accounts do NOT need a U2U profile.
- **Verified email.** All Market writes require `email_verified == true`.
- **Deterministic IDs.**
  - report: `{targetType}_{targetId}_{reporterUid}`
  - appeal ticket: `appeal_{noticeId}_{uid}`
  - rating: `{taskId}_{fromUid}`
  - Market conversation: `kind_listingId_uidLow_uidHigh`
- **Ratings.** The `userStats/{toUid}` update (lastMutation kind `rating`) is written in the same batch as the rating document.
- **Exact field sets.** Rules use allow-lists, so a document with any extra or missing field is denied. Build documents only through the repository class and its converters.
- **Public data.** `ratings` and `userStats` are readable by any signed-in user. Never store private data in them.

## 7. Market chat (v1 decision): separate and text-only
- Market chats use `marketConversations/{id}/messages`. They do NOT use the existing `chatConversations` engine.
- v1 is **text only**: no file sending, reactions, typing indicators, read-state subcollection or Nearby transfer inside Market chats. This overrides MARKET_FINAL_SPEC.md screens 14 and 17 wherever they mention files or Nearby.
- Task chat opens at accept time (created in the same batch as the accept). Offer chat opens from the offer detail while the offer is public.
- Blocking prevents creating a new Market chat between the two users.

## 8. Admin notes
- Admin operations (hide/restore, pin, suspend + listing hide + notice + audit log, support reply, config edit) do not require a U2U profile and are not available to non-admins.
- Every admin action writes an `adminLogs` document in the same batch and, where it affects the user, a notice.
- Pins: the rules do not cap pinned items; the admin UI blocks a 4th pin.

## 9. Not production-ready yet
- Rules passed the Firestore Emulator suite in CI, but the Emulator log contains diagnostics about the 1,000-expression evaluation limit. Until that is reviewed and resolved, rules must not be deployed to production.
- Before any deploy: compare against exported live rules/indexes, backfill `proposerId` on old proposals, deploy to staging first.
