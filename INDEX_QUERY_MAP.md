# Composite index → query map

`firestore.indexes.json` contains the 45 base composite indexes plus the new collection-group `proposals(proposerId ASC, updatedAt DESC)` index (46 total). The table maps each declared composite index to its intended app/admin query and the Firestore Rules query constraints required for access.

A matching index does **not** grant access. Firestore Rules evaluate authorization separately. In particular, student task/offer feeds must carry both public-state constraints; the Applied collection-group query must carry `proposerId == auth.uid`; poster proposal lists must be scoped to a task whose `posterId == auth.uid`; admin moderation queries require an admin UID present in `/admins/{uid}`.

| Query / use | Composite index fields (`collectionGroup`, scope; order) | Rules constraint / authorization |
|---|---|---|
| Admin audit log filtered by action, newest first | `adminLogs` / `COLLECTION`: action ascending, createdAt descending | Admin-only: path/query must use admin auth; list rule is admin-only. |
| Admin audit log for a target UID/listing, newest first | `adminLogs` / `COLLECTION`: targetId ascending, createdAt descending | Admin-only: list rule is admin-only. |
| Items/bookmarks filtered by read state and sorted newest first | `items` / `COLLECTION`: read ascending, createdAt descending | Use the current user’s own item collection/path. Base item rule remains authoritative. |
| Market Chats list: participants array-contains current UID, updatedAt descending | `marketConversations` / `COLLECTION`: participants array-contains, updatedAt descending | Use verified user; include `array-contains participants == auth.uid`; each returned row must be participant-visible, unblocked and non-admin. |
| Admin suspension queue filtered by status and expiry | `marketProfiles` / `COLLECTION`: status ascending, suspendedUntil ascending | Admin-only: `marketProfiles` list is admin-only. |
| Admin profiles queue sorted by status and most recently updated | `marketProfiles` / `COLLECTION`: status ascending, updatedAt descending | Admin-only. |
| Admin profiles queue sorted by status and warning count | `marketProfiles` / `COLLECTION`: status ascending, warnings descending | Admin-only. |
| Notices for a single user filtered by read state, newest first | `notices` / `COLLECTION`: read ascending, createdAt descending | Query only `/users/{auth.uid}/notices` (or admin scope); verified user required. |
| Offer feed by category, public moderation/status, pinned then renewed newest | `offers` / `COLLECTION`: category ascending, moderation ascending, status ascending, pinned descending, renewedAt descending | For student feed include `category == ...`, `moderation == ok`, `status == active`; non-admin public feed must constrain both moderation/status. |
| Offer feed by category and newest renewal | `offers` / `COLLECTION`: category ascending, moderation ascending, status ascending, renewedAt descending | For student feed include `moderation == ok` and `status == active`; category optional. |
| Admin offer queue by moderation and status, newest created | `offers` / `COLLECTION`: moderation ascending, status ascending, createdAt descending | Admin-only unless additionally constrained to an owner query; public rules require moderation=ok and status=active. |
| Offer feed sorted pinned then renewal time | `offers` / `COLLECTION`: moderation ascending, status ascending, pinned descending, renewedAt descending | Student feed must include `moderation == ok`, `status == active`; admin may use review variants. |
| Main offer feed newest renewal | `offers` / `COLLECTION`: moderation ascending, status ascending, renewedAt descending | Student feed must include `moderation == ok` and `status == active`. |
| Admin offer updates queue | `offers` / `COLLECTION`: moderation ascending, status ascending, updatedAt descending | Admin-only unless owner-scoped; public offer access requires moderation=ok and status=active. |
| Admin moderation review sorted by update time | `offers` / `COLLECTION`: moderation ascending, updatedAt descending | Admin-only: moderation alone does not prove public `status == active`. |
| Admin pinned offer list or an owner-scoped pinned view | `offers` / `COLLECTION`: pinned descending, renewedAt descending | Admin-only unless `sellerId == auth.uid`; public feed also needs moderation/status constraints. |
| My Offers ordered by renewal time | `offers` / `COLLECTION`: sellerId ascending, renewedAt descending | Constrain `sellerId == auth.uid`; seller can view own statuses, including hidden/paused/expired. |
| My Offers filtered by status, newest created | `offers` / `COLLECTION`: sellerId ascending, status ascending, createdAt descending | Constrain `sellerId == auth.uid`; optional status filter. |
| My Offers filtered by status and renewal time | `offers` / `COLLECTION`: sellerId ascending, status ascending, renewedAt descending | Constrain `sellerId == auth.uid`; optional status filter. |
| Admin status queue by renewal time | `offers` / `COLLECTION`: status ascending, renewedAt descending | Admin-only unless query also scopes `sellerId == auth.uid`; public feed requires moderation=ok. |
| Proposals under one task filtered by status, oldest/newest per createdAt | `proposals` / `COLLECTION`: status ascending, createdAt descending | Poster-only for list: the path must be `/tasks/{taskId}/proposals` and the task `posterId == auth.uid`. |
| Ratings written by one UID, newest first | `ratings` / `COLLECTION`: fromUid ascending, createdAt descending | Signed-in reads are permitted by the current ratings rule; use `fromUid == UID`. |
| Ratings related to a task, newest first | `ratings` / `COLLECTION`: taskId ascending, createdAt descending | Signed-in reads are permitted by current ratings rule; use `taskId == taskId`. |
| Ratings received by one UID, newest first | `ratings` / `COLLECTION`: toUid ascending, createdAt descending | Signed-in reads are permitted by current ratings rule; use `toUid == UID`. |
| My reports, newest first | `reports` / `COLLECTION`: reporterId ascending, createdAt descending | Student must include `reporterId == auth.uid`; admin can list via admin branch. |
| My reports filtered by target type | `reports` / `COLLECTION`: reporterId ascending, targetType ascending, createdAt descending | Student must constrain `reporterId == auth.uid`; admin branch also allowed. |
| Admin report moderation queue by status | `reports` / `COLLECTION`: status ascending, createdAt descending | Admin-only unless `reporterId == auth.uid`; non-admin reads require reporterId match. |
| Admin reports by status and type | `reports` / `COLLECTION`: status ascending, targetType ascending, createdAt descending | Admin-only unless query is additionally reporter-scoped. |
| Admin report moderation queue for one target/status | `reports` / `COLLECTION`: status ascending, targetType ascending, targetId ascending, createdAt descending | Admin-only unless reporter-scoped. |
| Admin report history for one target | `reports` / `COLLECTION`: targetType ascending, targetId ascending, createdAt descending | Admin-only unless reporterId matches caller. |
| Admin support queue by status and type | `support` / `COLLECTION`: status ascending, type ascending, updatedAt descending | Admin-only; non-admin can query only own tickets via `userId == auth.uid`. |
| Admin support queue by status | `support` / `COLLECTION`: status ascending, updatedAt descending | Admin-only unless userId-scoped. |
| Admin support queue by type and status | `support` / `COLLECTION`: type ascending, status ascending, updatedAt descending | Admin-only unless userId-scoped. |
| My support tickets by status, recently updated | `support` / `COLLECTION`: userId ascending, status ascending, updatedAt descending | Must constrain `userId == auth.uid`; verified-user access. |
| Task feed filtered by category, newest created | `tasks` / `COLLECTION`: category ascending, moderation ascending, status ascending, createdAt descending | Student feed must include `moderation == ok` and `status == open`; category optional. |
| Task feed by category, pinned then creation time | `tasks` / `COLLECTION`: category ascending, moderation ascending, status ascending, pinned descending, createdAt descending | Student feed must include `moderation == ok` and `status == open`. |
| Admin review queue sorted by creation time | `tasks` / `COLLECTION`: moderation ascending, createdAt descending | Admin-only unless owner-scoped; public task rule needs `status == open` too. |
| Admin review/pinned task queue | `tasks` / `COLLECTION`: moderation ascending, pinned descending, createdAt descending | Admin-only unless owner-scoped; public feed must also constrain status=open. |
| Public task feed newest first | `tasks` / `COLLECTION`: moderation ascending, status ascending, createdAt descending | Student feed must include `moderation == ok` and `status == open`; admin may use broader filters. |
| Task feed pinned first, then newest | `tasks` / `COLLECTION`: moderation ascending, status ascending, pinned descending, createdAt descending | Student feed must include `moderation == ok` and `status == open`. |
| Admin task updates queue or public feed sorted by update time | `tasks` / `COLLECTION`: moderation ascending, status ascending, updatedAt descending | Student feed must constrain `moderation == ok` and `status == open`; moderation queues are admin-only. |
| Admin pinned/status curation list or owner-scoped tasks | `tasks` / `COLLECTION`: pinned ascending, createdAt descending | Admin-only unless `posterId == auth.uid`; a public feed must also constrain moderation/status. |
| My Tasks ordered by creation date | `tasks` / `COLLECTION`: posterId ascending, createdAt descending | Must constrain `posterId == auth.uid`; includes owner listings of any status/moderation. |
| My Tasks filtered by status | `tasks` / `COLLECTION`: posterId ascending, status ascending, createdAt descending | Must constrain `posterId == auth.uid`; optional status filter. |
| Admin listing queue filtered by status | `tasks` / `COLLECTION`: status ascending, createdAt descending | Admin-only unless owner-scoped; public feed must also constrain moderation=ok. |
| My Tasks → Applied collection-group list, newest update first | `proposals` / `COLLECTION_GROUP`: proposerId ascending, updatedAt descending | Must use collectionGroup(`proposals`) with `where(proposerId, ==, auth.uid)`; verified user. Unconstrained and other-UID queries are denied. |

## Applied query must match the rules

```js
query(
  collectionGroup(db, 'proposals'),
  where('proposerId', '==', auth.currentUser.uid),
  orderBy('updatedAt', 'desc'),
  limit(20)
)
```

The rule `match /{path=**}/proposals/{proposalId}` permits get/list only when `resource.data.proposerId == request.auth.uid`. The query’s `where` clause is therefore mandatory. The nested `/tasks/{taskId}/proposals/{userId}` rules remain separate and still let a task poster list that task’s proposals. The static and emulator tests include allowed own-UID results plus denied unconstrained and other-UID collection-group queries.

## Query implementation check

These shapes are derived from the supplied Market specification and rules/index manifest; this package does not contain the production student/admin UI query code, so the mapping confirms the required compatible shapes rather than claiming to have scanned live app call sites. Before deployment, check every implemented query against this table and remove any unused composite index only in a separate review.
