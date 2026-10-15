# Student Connect: student profile and unique ID

This step adds a Firestore profile and a unique, shareable ID in the format \`UNI-XXXXXXX\`. Profile data is not searchable in this step; each profile document can only be read by its owner.

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

The rules require a signed-in Firebase account with a verified email, prevent profile enumeration, keep profile reads owner-only, and reserve IDs atomically. The email address and ERP password are not stored in the profile. IDs are immutable and remain reserved.

However, a verified Gmail/Firebase account is **not proof of current Superior University enrollment**. The existing ERP session check runs inside the Android client and Firestore rules cannot independently prove it. Before treating the directory as student-only or exposing profiles to other users, a trusted enrollment verification design is needed. This step does not create chats, requests, contacts, or a student search directory.
