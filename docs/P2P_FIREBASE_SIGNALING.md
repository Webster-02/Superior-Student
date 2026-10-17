# Firebase P2P signaling adapter

This is an additive Android signaling layer for Superior Student's existing one-to-one Student Connect conversations.

## What it does

- Uses Firebase Authentication's current verified account and existing chat conversation IDs.
- Creates small transfer-offer metadata records below each chat conversation.
- Replays pending incoming offers from Firestore after the receiving app reconnects.
- Stores WebRTC SDP, ICE candidates, LAN candidate metadata, resume bitmaps, cancellation, progress checkpoints, and completion hashes as small signaling documents.
- Allows the intended recipient to acknowledge (delete) signals after processing.
- Does not upload file bytes to Firestore or Firebase Storage.

## Deploying Firestore rules

This branch contains additive rules for transfer offers and signal messages. Review the complete diff first, then deploy the complete rules file from the repository root with:

    firebase deploy --only firestore:rules

Do not paste only the added match blocks over the existing console rules. The rules require verified email, an existing active chat conversation, and an accepted friend connection. A successful APK build does not deploy Firestore rules.

## TURN credentials

Never put the coturn shared secret in the APK, Firestore documents, or public GitHub configuration. Direct Firestore signaling works on Spark, but it cannot safely mint HMAC-based, time-limited TURN credentials on the client. Firebase Cloud Functions deployment requires Blaze billing. For TURN, configure a trusted credential issuer (for example, a callable Cloud Function on Blaze or another protected service) that returns only a short-lived username/password pair. If staying on Spark, test local LAN/direct WebRTC first; TURN relay remains unavailable until this issuer exists.

## Limits

- Firestore is signaling only; file bytes must use encrypted LAN HTTPS, WebRTC DataChannel, or TURN.
- Thumbnail metadata is limited to 20 KiB of base64 text.
- Throttle progress signaling to once every 1–2 seconds; do not create a write per chunk.
- Direct-client Firestore rules enforce participant authorization and payload shape, but are not a robust per-user server-side rate limiter. Add App Check and/or a trusted relay/rate-limit endpoint before public production rollout.
- Firestore is unavailable without internet; local LAN mode needs NSD/UDP discovery and local peer authentication as its no-internet fallback.
- This step adds the Firebase metadata/signaling foundation; actual encrypted file-stream integration and UI wiring remain separate steps.
