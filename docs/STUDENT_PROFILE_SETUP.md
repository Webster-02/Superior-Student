# Student Connect: student profile and unique ID

This step adds a Firestore profile and a unique, shareable ID in the format \`UNI-XXXXXXX\`. Find Students searches only by the complete public ID. Results show display name and public ID only; bulk collection listing remains disabled.

## Before testing

1. In Firebase Console, choose the existing \`pachattarpercent\` project.
2. Open **Build → Firestore Database**. If no database exists, create it in **Production mode**. Choose the region carefully because a Firestore database location cannot simply be changed later.
3. Open the **Rules** tab. If this database is new and does not hold existing app data, copy the contents of the repository's \`firestore.rules\` into the editor and click **Publish**.
4. If Firestore already has collections or rules for another feature, **do not overwrite the current rules**. Stop and review/merge the existing rules first.
5. Keep Firebase Authentication **Email/Password** enabled and use a verified Student Connect account.

GitHub does not automatically publish Firestore rules. The \`firestore.rules\` file is a reviewed starting point for you to publish in the correct Firebase project.

## Test on the Android app

1. Install the debug APK built from \`feature/student-connect-step-1\`.
2. Sign in to the existing university app, open **Student Connect**, and make sure the Firebase email is verified.
3. If the app reports that Firestore cannot be read, confirm Firestore is created and the rules above are published; then close and reopen Student Connect.
4. Enter the display name classmates should see. If the ERP profile name was already cached, it will be prefilled and you can adjust it before creation.
5. Tap **Create my student profile**. A unique \`UNI-XXXXXXX\` ID should appear.
6. Tap **Copy student ID**, paste it into a notes app, and check it matches the ID shown.
7. Leave and reopen Student Connect. The same profile ID should still appear.
8. In Firebase Console → Firestore Data, check:
   - \`profiles/{Firebase Auth UID}\`: contains only UID, public ID, display name, and timestamps.
   - \`studentIds/{UNI-XXXXXXX}\`: reserves that ID for the same UID.
9. Confirm sign out/sign in to Student Connect does not sign the university session out.

## Security boundary to understand

The rules require a signed-in Firebase account with a verified email, prevent profile enumeration, allow only single-document reads of profile data with an explicit public-field allowlist, and reserve IDs atomically. The email address and ERP password are not stored in the profile. IDs are immutable and remain reserved.

However, a verified Gmail/Firebase account is **not proof of current Superior University enrollment**. The existing ERP session check runs inside the Android client and Firestore rules cannot independently prove it. Before treating the directory as student-only or exposing profiles to other users, a trusted enrollment verification design is needed. This step does not create chats, requests, contacts, or a student search directory.


## Find Students step

1. Publish the updated \`firestore.rules\` from the feature branch in Firebase Console → Firestore Database → Rules. The update is needed so a verified user may read an individual profile by the public ID index; it still denies collection listing and limits the profile to public fields.
2. This is a rules-only update; it does not delete your existing \`profiles\` or \`studentIds\` documents. If you added unrelated collections or other rules, review/merge the file rather than replacing those rules blindly.
3. The app searches by the full \`UNI-XXXXXXX\` ID only. It does not allow prefix or directory-wide searches.
4. To test a positive result, use a second verified Firebase account with a separately created profile and search its ID from the first account (or ask another tester to share their ID). Only display name and public ID should appear.
5. Search your own ID and a made-up ID too; the app should show a self-ID message and a helpful not-found message.

A verified Firebase email is still not proof of current Superior University enrollment. Institutional verification must be designed before advertising the directory as a university-verified student directory.


## Friend Requests step

This build adds directional friend requests in friendRequests/{senderUid}_{receiverUid} documents.

Before testing, publish the latest firestore.rules file in Firebase Console → Firestore Database → Rules. Preserve or merge rules for unrelated collections instead of blindly replacing them.

Test using two verified accounts that each have a profile. From account A, search account B by public ID and send a friend request. From account B, refresh Friend Requests, accept or reject the incoming request, and confirm account A sees the updated status after refreshing. Also test cancellation and resend after a rejected or cancelled request. This step records request status only; contacts and chat are not enabled yet.

The current Firebase email verification confirms access to an email address, not enrollment in Superior University. A trusted institutional verification flow is needed before presenting the directory as an enrollment-verified student directory.
