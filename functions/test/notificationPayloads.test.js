const test = require("node:test");
const assert = require("node:assert/strict");
const {
  notificationForCreatedRequest,
  notificationForUpdatedRequest
} = require("../src/notificationPayloads");

const request = {
  senderUid: "student_sender",
  receiverUid: "student_receiver",
  senderName: "Awais Khan",
  senderPublicId: "UNI-ABC2345",
  receiverName: "Sara Malik",
  receiverPublicId: "UNI-DEF4567",
  status: "pending"
};

test("a newly created pending request notifies the receiver", () => {
  const result = notificationForCreatedRequest(request, "request_1");
  assert.equal(result.recipientUid, "student_receiver");
  assert.equal(result.title, "New friend request");
  assert.equal(result.body, "Awais Khan sent you a friend request.");
  assert.deepEqual(result.data, {type:"friend_request", requestId:"request_1"});
});

test("created records that are not pending do not notify", () => {
  assert.equal(notificationForCreatedRequest({...request,status:"accepted"}, "request_1"), null);
});

test("invalid or self-directed request records do not notify", () => {
  assert.equal(notificationForCreatedRequest(null, "request_1"), null);
  assert.equal(notificationForCreatedRequest({...request,receiverUid:request.senderUid}, "request_1"), null);
  assert.equal(notificationForCreatedRequest(request, ""), null);
});

test("accepting a request notifies its original sender", () => {
  const result = notificationForUpdatedRequest(
    {...request,status:"pending"},
    {...request,status:"accepted"},
    "request_2"
  );
  assert.equal(result.recipientUid, "student_sender");
  assert.equal(result.title, "Friend request accepted");
  assert.equal(result.body, "Sara Malik accepted your friend request.");
  assert.equal(result.data.type, "friend_request_accepted");
});

test("resending a rejected, cancelled, or removed request notifies the receiver", () => {
  for (const status of ["rejected","cancelled","removed"]) {
    const result = notificationForUpdatedRequest(
      {...request,status},
      {...request,status:"pending"},
      "request_" + status
    );
    assert.equal(result.recipientUid, "student_receiver");
    assert.equal(result.data.type, "friend_request");
  }
});

test("unrelated status changes do not notify", () => {
  assert.equal(notificationForUpdatedRequest(
    {...request,status:"pending"},
    {...request,status:"rejected"},
    "request_3"
  ), null);
  assert.equal(notificationForUpdatedRequest(
    {...request,status:"accepted"},
    {...request,status:"removed"},
    "request_4"
  ), null);
});

test("an update cannot swap the participants", () => {
  assert.equal(notificationForUpdatedRequest(
    {...request,status:"pending"},
    {...request,receiverUid:"other_user",status:"accepted"},
    "request_5"
  ), null);
});
