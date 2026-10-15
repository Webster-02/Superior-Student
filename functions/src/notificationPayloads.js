const PENDING = "pending";
const ACCEPTED = "accepted";
const RESENDABLE_STATUSES = new Set(["rejected", "cancelled", "removed"]);

function isNonEmptyString(value, maxLength = 200) {
  return typeof value === "string" &&
    value.trim().length > 0 &&
    value.length <= maxLength;
}

function validateRequest(data) {
  if (!data || typeof data !== "object") return false;
  return isNonEmptyString(data.senderUid, 256) &&
    isNonEmptyString(data.receiverUid, 256) &&
    data.senderUid !== data.receiverUid &&
    isNonEmptyString(data.senderName, 60) &&
    isNonEmptyString(data.receiverName, 60);
}

function createNotification(recipientUid, title, body, type, requestId) {
  if (!isNonEmptyString(recipientUid, 256) || !isNonEmptyString(requestId, 512)) {
    return null;
  }
  return {
    recipientUid,
    title,
    body,
    data: { type, requestId }
  };
}

function notificationForCreatedRequest(data, requestId) {
  if (!validateRequest(data) || data.status !== PENDING) return null;
  return createNotification(
    data.receiverUid,
    "New friend request",
    data.senderName.trim() + " sent you a friend request.",
    "friend_request",
    requestId
  );
}

function notificationForUpdatedRequest(before, after, requestId) {
  if (!validateRequest(before) || !validateRequest(after)) return null;
  if (before.senderUid !== after.senderUid || before.receiverUid !== after.receiverUid) {
    return null;
  }

  if (before.status !== PENDING &&
      after.status === PENDING &&
      RESENDABLE_STATUSES.has(before.status)) {
    return createNotification(
      after.receiverUid,
      "New friend request",
      after.senderName.trim() + " sent you a friend request.",
      "friend_request",
      requestId
    );
  }

  if (before.status === PENDING && after.status === ACCEPTED) {
    return createNotification(
      after.senderUid,
      "Friend request accepted",
      after.receiverName.trim() + " accepted your friend request.",
      "friend_request_accepted",
      requestId
    );
  }

  return null;
}

module.exports = {
  notificationForCreatedRequest,
  notificationForUpdatedRequest
};
