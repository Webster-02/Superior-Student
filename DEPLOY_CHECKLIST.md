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
- Verify student flows: Market profile creation, task/offer creation and edits, proposal create/withdraw/Applied, accept transaction and text-only Market chat, poster-only completion, both-side ratings, reporting, blocks, suspension expiry, support tickets and notices. For ordinary support tickets, verify admin reply/resolve always include the linked new audit log and owner notice; test appeal decisions separately because they use a distinct admin path.
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


## 7. Fast path to real-device functional testing (staging only)

**Current evidence:** candidate Rules run #37 passed 46 static checks and 73 Emulator tests with zero expression-ceiling diagnostics. Android run #105 produced a verified debug APK, but that APK uses the Firebase project configured in the repository. The checked-in `app/google-services.json` currently identifies project ID `pachattarpercent`; treat it as the configured/live project until the owner confirms otherwise. Do not send candidate Rules to that project for convenience.

### A. Create an isolated staging target

1. In Firebase Console, create a separate test project and register an Android app whose package name is `com.superiorstudent.app`. Enable the same sign-in provider the app uses, then create two verified student test accounts and one separate admin test account.
2. Download the staging project's `google-services.json`. Keep it outside Git or in a local-only copy. Do not upload service-account keys, private keys, passwords or access tokens.
3. From the repository root, check out `review/market-rules` for deployment files and `feature/market-ui-reference` for the APK source (the branches stay separate). Confirm the Rules CI run is green before deploying.
4. Deploy only to the verified staging project, explicitly naming its ID:
   
   ```bash
   firebase login
   firebase projects:list
   export MARKET_STAGING_PROJECT_ID="REPLACE_WITH_THE_SEPARATE_STAGING_PROJECT_ID"
   test -n "$MARKET_STAGING_PROJECT_ID" || exit 1
   firebase deploy --only firestore:rules,firestore:indexes --project "$MARKET_STAGING_PROJECT_ID"
   ```

   Stop if the project shown by `firebase projects:list` is not the newly created staging project. Do not rely on the active/default alias. This command overwrites the target project's published Firestore Rules and deploys the index definitions in this branch; it must not be pointed at the configured `pachattarpercent` project just to speed up testing.

### B. Build an APK that talks to staging

The existing CI APK from run #105 is **not** a staging-configured APK. To create a staging-configured debug APK without changing the committed production config:

1. Back up `app/google-services.json` outside the repository.
2. Temporarily put the staging `google-services.json` at `app/google-services.json` (its Android package must match `com.superiorstudent.app`).
3. Run `./gradlew :app:clean :app:assembleDebug` from the repository root.
4. Restore the backed-up committed `google-services.json` immediately after the build; confirm `git status --short` shows no config change. Install the resulting debug APK on the test phone while signed into staging test accounts only. Do not distribute this staging APK as a production release.

If staging Rules and the staging APK do not point to the same project ID, stop. Do not “test” by publishing candidate Rules to the production project.

### C. Minimum smoke-test order

1. Sign in as verified student A and create a Market profile; verify invalid/unverified student writes are denied.
2. A creates an open task; student B sends a proposal; A accepts. Confirm the task becomes assigned, the proposal accepted, `openTasks` decrements once, and the new text-only Market conversation opens.
3. A completes the assigned task. Confirm completion does **not** decrement `openTasks` a second time; then rate from both participants.
4. Create, pause, resume, renew and remove an offer. Confirm `activeOffers` changes only on the specified counted transitions; removing a paused offer leaves it unchanged.
5. Test report creation/status changes and verify the matching audit log/notice pointers.
6. Test warning, suspension/ban, lift, support reply/resolve, appeal reject and appeal approve/restore. Verify every admin decision has its linked audit log and notice, and that suspension releases listing counters as one aggregate action.
7. Verify ordinary U2U sign-in, friend requests, private chat and Nearby transfer still work unchanged. Record device, app build, staging project ID, expected result and actual result for each case.

Do not promote from staging to production until the separate live Rules/index export comparison, Auth/profile readiness, any required legacy `proposerId` review, and explicit owner approval are complete.
