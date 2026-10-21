# U2U Market Firestore pre-deploy checklist

**Status: review only. No deployment is authorized by this document.** Firebase Spark constraints apply: no Cloud Functions, Firebase Storage, custom backend, or server-side app service. Use Firestore Security Rules as the authorization boundary. A one-off trusted Admin SDK migration may be run from an authorized maintainer workstation or Cloud Shell, but it must not be deployed as a function or embedded in the client.

## 1. Capture the production baseline first

- Confirm the exact Firebase **production project ID** in Firebase Console and record it, the operator, date/time, and current rules/index versions.
- Export/copy the *currently published production* Firestore rules into `rollback/firestore.production-before-market.rules`.
- Export the *currently deployed* composite indexes into `rollback/firestore.production-before-market.indexes.json`.
- Record SHA-256 hashes for both exports. Keep a protected copy outside the review branch as well.
- Compare the live rules with `firestore.base.rules` (and the selected review branch base commit). Compare the live indexes with the pre-Market index export. If the live snapshot differs from the selected base, **stop** and rebase/merge the candidate on the actual live source before testing or deploying. The packaged branch snapshot is not proof of production equivalence.
- Preserve the live exports unchanged. Never overwrite the only rollback copies with the candidate rules/indexes.

## 2. Verify existing data and Auth prerequisites

- Check Firebase Authentication users for `emailVerified == true`. Record the number of verified/unverified accounts; decide how unverified students are prompted to verify before Market writes. Do not weaken the rules to compensate for unverified users.
- Check that each Market student has a valid `profiles/{uid}` U2U profile with a valid `displayName`; admin accounts do not need `profiles/{uid}` or `marketProfiles/{uid}`.
- **Backfill legacy proposals:** the current rules require immutable `proposerId == proposal document ID == proposer UID`. Before deployment, use a trusted, one-off Firebase Admin SDK script to scan `tasks/*/proposals/*` and fill missing `proposerId` from the proposal document ID. First run a dry-run that reports total documents, missing values, mismatches, and proposed writes. Validate that each proposal document ID is actually the original proposer's UID; stop and manually reconcile mismatches rather than assigning a guessed UID. Export/backup the affected documents before applying. Write in bounded batches (well below 500 writes/batch), make the script idempotent, log each path and outcome, and re-read every changed document after the write. Do not ship the Admin SDK credential or script in the Android app.
- Confirm historic docs contain the fields required by the candidate rules (especially proposal `proposerId`, status and timestamps), and assess whether malformed legacy records need a separate data-migration plan.

## 3. Local and CI gate

- Use Node.js 20 and Java 21.
- Install dependencies from the committed `package-lock.json` using `npm ci` when the lockfile is available. If no lockfile exists, generate and review it in the review branch with registry access, then use `npm ci`. Do not silently regenerate a lockfile during a production deployment.
- Run `npm run test:static` and `npm run test:emulator`; both must exit zero with actual output artifacts retained.
- Confirm the Emulator cases exercise the high-cost operations: task create, proposal create, accept plus Market-conversation creation in one transaction, report/counter batch, admin suspend/hide/notice/log batch, and all admin-only operations when the admin has no U2U profile. A static check does not compile Firestore rules or prove access-call limits.
- Compare every changed rule against `DIFF_AGAINST_BASE.patch`. Existing auth, friend chat, `chatConversations`, and Nearby transfer behavior must remain unchanged. Admins must be denied `marketConversations` and messages; ordinary U2U chat/Nearby behavior must have regression coverage.
- Verify every app/admin list query against `INDEX_QUERY_MAP.md`, including the required `proposerId == auth.uid` filter on the `proposals` collection-group query. Do not run an unconstrained Applied query.

## 4. Staging deployment gate

- Use a separate Firebase **staging project** with no production data or credentials.
- Deploy only the reviewed rules and indexes to staging after the CI gate passes.
- Verify student flows: Market profile creation, task/offer creation and edits, proposal create/withdraw/Applied, accept transaction and text-only Market chat, poster-only completion, both-side ratings, reporting, blocks, suspension expiry, support tickets and notices.
- Verify admin flows using an admin account that has an `admins/{uid}` document but no U2U `profiles/{uid}` or `marketProfiles/{uid}`: hide/restore and pin/unpin listings, suspend/hide in one batch with notice and audit log, create notice, write admin log, reply to support, and edit `config/market`. Verify the same account has no `marketConversations` or Market-message access.
- Confirm limits (3 open tasks, 10 proposals per rolling 24 hours, 15 per task, 5 active offers, 3 reports before moderation) remain enforced and read-only in the admin panel. Only announcement, `blockedWords`, and non-negative `rankWeights` (sum 100) are editable.
- Verify feeds and query behavior, indexes finished building, no rule access-call failures, and no unexpected denied requests in Emulator/staging logs.
- Save staging test results and artifact hashes. Review with the product owner and receive explicit approval before touching production.

## 5. Production deployment — only after explicit approval

- Re-export current production rules/indexes immediately before deployment and compare hashes against the saved baseline. If anything changed, stop and re-review.
- Confirm the `proposerId` backfill has completed and been validated, and verify email-verification readiness.
- Confirm CI and staging test artifacts are attached to the change and both Suites passed.
- After explicit approval only, deploy:

```bash
firebase deploy --only firestore:rules,firestore:indexes --project <PRODUCTION_PROJECT_ID>
```

- Verify the deployed rules version and index states in Firebase Console. Perform smoke tests with a normal verified student and the separate admin account. Do not merge this review branch to `main` as part of this checklist and do not deploy without the explicit approval.

## 6. Rollback plan

- If the new rules cause regressions, use the **saved live production export**, not merely `rollback/firestore.rollback.branch-snapshot.rules`, unless you have first proven that snapshot byte-identical to production.
- Restore rules from `rollback/firestore.production-before-market.rules` and indexes from `rollback/firestore.production-before-market.indexes.json` (if changed). Confirm project ID and file hashes before deploying rollback.
- After explicit rollback approval only, deploy the previous rules and indexes:

```bash
firebase deploy --only firestore:rules,firestore:indexes --project <PRODUCTION_PROJECT_ID>
```

- Verify U2U sign-in, friend requests, existing private chats, Nearby transfer, Market reads/writes, and admin moderation after rollback. A Rules/index rollback does not remove Market data and does not undo data migrations. Keep any proposal `proposerId` backfill in place unless a separate reviewed data rollback is specifically required; older app clients may not tolerate removing it.

## Spark-plan guardrails

- No Cloud Functions, Firebase Storage, server, or deployed Admin SDK runtime.
- Trusted Admin SDK usage is limited to a supervised one-off migration/verification run from a secure administrative environment.
- Security decisions must remain enforced in Firestore Security Rules; client checks are UX safeguards only.
