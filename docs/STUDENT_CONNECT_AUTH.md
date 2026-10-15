# Student Connect: Firebase identity setup

This step adds a separate Firebase Authentication identity for Student Connect. It does not replace the university ERP login and does not create student profiles, public IDs, chats, or Firestore documents yet.

## One-time Firebase Console setup

1. Open Firebase Console and select the existing `pachattarpercent` project.
2. Go to **Build → Authentication → Get started** (if needed).
3. Open **Sign-in method**.
4. Enable **Email/Password** and save. Email link/passwordless sign-in is not needed for this step.
5. No Firestore database or Storage rules are needed yet; this step only uses Firebase Authentication.

## In-app test

1. Build/install the APK from the `feature/student-connect-step-1` branch.
2. Log in to the app with the existing university credentials.
3. Open the side menu → **Student Connect**.
4. Create a Student Connect account with an email inbox you can access and a new password (at least 6 characters). Do not reuse the university ERP password.
5. Open the verification email, then return to the app and tap **I've verified my email — check again**.
6. Close and reopen Student Connect. It should remember the signed-in account.
7. Tap **Sign out of Student Connect**, then sign in again with the same Student Connect email/password. Confirm that your university login and existing screens remain unchanged.

## Design decision

Email/password Firebase identity was chosen instead of anonymous auth so that the same identity can be restored after reinstalling the app by signing in again. Students must keep access to the email and password. The existing ERP login continues to be handled by the current app and is not sent to Firebase.

This client-only step gates the entry point behind the current app session, but client-side gating is not a cryptographic proof of university enrollment. Enforcing institutional eligibility against an attacker who bypasses the app requires a trusted server/custom-token flow; do not assume an email/password Firebase account alone proves someone is a Superior University student.

Next step, after this is tested, will be to add a Firestore student profile and unique public ID. That step must include restrictive Firestore Security Rules before any real student directory or chat data is used.
