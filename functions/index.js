const crypto = require("node:crypto");
const { initializeApp, getApps } = require("firebase-admin/app");
const { FieldValue, getFirestore, Timestamp } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { logger } = require("firebase-functions");
const {
  onDocumentCreated,
  onDocumentUpdated
} = require("firebase-functions/v2/firestore");
const {
  notificationForCreatedRequest,
  notificationForUpdatedRequest
} = require("./src/notificationPayloads");

if (getApps().length === 0) initializeApp();

const db = getFirestore();
const messaging = getMessaging();
const TOKEN_COLLECTION = "studentNotificationTokens";
const DELIVERY_COLLECTION = "_studentConnectNotificationDeliveries";
const PROCESSING_LEASE_MS = 5 * 60 * 1000;

const triggerOptions = {
  document: "friendRequests/{requestId}",
  retry: true,
  maxInstances: 5,
  timeoutSeconds: 30
};

function deliveryDocumentId(eventId) {
  return crypto.createHash("sha256").update(String(eventId)).digest("hex");
}

async function claimDelivery(eventId, notification) {
  const deliveryRef = db.collection(DELIVERY_COLLECTION).doc(deliveryDocumentId(eventId));
  const claimed = await db.runTransaction(async (transaction) => {
    const existing = await transaction.get(deliveryRef);
    if (existing.exists) {
      const status = existing.get("status");
      if (status === "sent" || status === "skipped") return false;
      const claimedAt = existing.get("claimedAt");
      if (status === "processing" && claimedAt instanceof Timestamp &&
          Date.now() - claimedAt.toMillis() < PROCESSING_LEASE_MS) return false;
    }

    transaction.set(deliveryRef, {
      status: "processing",
      recipientUid: notification.recipientUid,
      requestId: notification.data.requestId,
      notificationType: notification.data.type,
      claimedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp()
    }, {merge:true});
    return true;
  });
  return {claimed, deliveryRef};
}

async function updateDelivery(deliveryRef, fields) {
  await deliveryRef.set({...fields, updatedAt:FieldValue.serverTimestamp()}, {merge:true});
}

async function sendStudentConnectNotification(event, notification) {
  if (!notification) return;
  if (typeof event.id !== "string" || event.id.trim().length === 0) {
    throw new Error("Firestore notification event did not include a stable event ID.");
  }

  const {claimed, deliveryRef} = await claimDelivery(event.id, notification);
  if (!claimed) {
    logger.info("Duplicate Student Connect notification event ignored.", {
      requestId:notification.data.requestId,
      notificationType:notification.data.type
    });
    return;
  }

  const tokenRef = db.collection(TOKEN_COLLECTION).doc(notification.recipientUid);
  try {
    const tokenSnapshot = await tokenRef.get();
    const token = tokenSnapshot.exists ? tokenSnapshot.get("token") : null;

    if (typeof token !== "string" || token.trim().length === 0) {
      await updateDelivery(deliveryRef, {
        status:"skipped",
        skipReason:"recipient_has_no_registered_token"
      });
      logger.info("Student Connect notification skipped; recipient has no registered token.", {
        recipientUid:notification.recipientUid,
        requestId:notification.data.requestId
      });
      return;
    }

    const messageId = await messaging.send({
      token,
      notification:{title:notification.title,body:notification.body},
      data:notification.data,
      android:{
        priority:"high",
        notification:{channelId:"student_connect_updates"}
      }
    });

    await updateDelivery(deliveryRef, {status:"sent",messageId});
    logger.info("Student Connect push notification sent.", {
      recipientUid:notification.recipientUid,
      requestId:notification.data.requestId,
      notificationType:notification.data.type
    });
  } catch (error) {
    const code = error && typeof error.code === "string" ? error.code : "unknown";
    if (code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token") {
      await tokenRef.delete().catch(() => undefined);
      await updateDelivery(deliveryRef, {
        status:"skipped",
        skipReason:"invalid_registration_token"
      });
      logger.warn("Removed an invalid Student Connect notification token.", {
        recipientUid:notification.recipientUid
      });
      return;
    }

    await updateDelivery(deliveryRef, {status:"failed",errorCode:code}).catch((writeError) => {
      logger.error("Could not save Student Connect notification failure status.", {
        error:writeError && writeError.message
      });
    });
    throw error;
  }
}

exports.notifyOnFriendRequestCreated = onDocumentCreated(triggerOptions, async (event) => {
  if (!event.data) return;
  await sendStudentConnectNotification(
    event,
    notificationForCreatedRequest(event.data.data(), event.params.requestId)
  );
});

exports.notifyOnFriendRequestUpdated = onDocumentUpdated(triggerOptions, async (event) => {
  if (!event.data) return;
  await sendStudentConnectNotification(
    event,
    notificationForUpdatedRequest(
      event.data.before.data(),
      event.data.after.data(),
      event.params.requestId
    )
  );
});
