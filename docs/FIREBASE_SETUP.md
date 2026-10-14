# Firebase setup for Superior Student

This guide covers the initial Firebase SDK wiring only. It does not change the university ERP login, create chat collections, enable an auth provider, or deploy Firestore/Storage rules.

## Existing Android app details

- Firebase project: use your existing project; do not create a second project just for this feature.
- Android application ID / package name: `com.superiorstudent.app`
- Android module configuration file path: `app/google-services.json`
- Google Services Gradle plugin: `4.5.0`
- Firebase Android BoM: `34.19.0` (chosen to preserve the current `minSdk 23`)
- Kotlin Gradle plugin: `2.3.21` (required to read the Kotlin 2.3 metadata shipped in Firebase Auth 24.2.0)

## Get the Android configuration file

1. Open the Firebase Console and select the existing Firebase project.
2. Open **Project settings** (gear icon) > **General** > **Your apps**.
3. Look for an Android app registered with the exact package name `com.superiorstudent.app`.
4. If it already exists, select it and download its `google-services.json`.
5. If it does not exist, choose **Add app** > **Android**, enter `com.superiorstudent.app` exactly, register it, then download `google-services.json`.
6. Do not rename or edit the downloaded file. It belongs at `app/google-services.json`.

The Gradle setup applies the Google Services plugin only when that file exists. This lets the existing debug build continue during setup. Once the file is added, the plugin will process it automatically.

## Important security notes

- `google-services.json` is the normal Android client configuration file; it is not a Firebase Admin SDK service-account key. However, this repository is public, so committing the file makes its project identifiers publicly visible. Firebase Security Rules (and later App Check, where appropriate), not secrecy of this file, must protect data.
- Never share or commit a Firebase Admin SDK JSON file, service-account private key, legacy FCM server key, passwords, or other server credentials.
- Do not switch the existing university login to Firebase Authentication. Firebase identity for Student Connect will be designed separately before student data is written.
- Do not deploy permissive Firestore or Storage rules. Do not replace any existing rules until the current Firebase Console configuration and data usage have been reviewed.
- No collections or uploaded files are created by this SDK-only step. Firestore/Storage setup and profile-ID code will come in later confirmed steps.

## First verification

1. Add `app/google-services.json` to the project.
2. Sync Gradle / run `gradle clean testDebugUnitTest assembleDebug`.
3. Confirm the debug APK builds.
4. Install the APK and verify the existing university login and all current screens still work.

A successful build confirms SDK/config integration, not yet secure chat authorization or Firestore read/write rules. Those will be tested separately.
