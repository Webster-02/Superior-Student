import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { after, before, beforeEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} from '@firebase/rules-unit-testing';
import {
  collection, collectionGroup, doc, getDoc, getDocs, query, setDoc, updateDoc, deleteDoc,
  serverTimestamp, Timestamp, writeBatch, runTransaction, where, orderBy, limit,
} from 'firebase/firestore';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rulesPath = process.env.MARKET_RULES_FILE || path.resolve(__dirname, '../firestore.rules');
const rulesText = fs.readFileSync(rulesPath, 'utf8');
const projectId = process.env.GCLOUD_PROJECT || 'u2u-market-rules-review';
let testEnv;

const DEFAULT_SETTINGS = {
  alertProposals: true, alertUpdates: true, alertNotices: true, alertMatchingTasks: true,
};
function profileSeed(uid, overrides = {}) {
  return {
    uid, skillTags: [], blockedUids: [], status: 'active', suspendedUntil: null,
    warnings: 0, settings: { ...DEFAULT_SETTINGS }, openTasks: 0, activeOffers: 0,
    proposalWindowCount: 0, proposalWindowStartedAt: null,
    reportWindowCount: 0, reportWindowStartedAt: null, openSupportTickets: 0,
    lastMutation: null, createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    ...overrides,
  };
}
function taskSeed(taskId, posterId, overrides = {}) {
  const createdAt = Timestamp.now();
  return {
    title: 'Help me with Biology notes', category: 'Study help', description: 'I need help organising biology notes for this semester.',
    budgetType: 'negotiable', deadline: Timestamp.fromMillis(Date.now() + 7 * 86400000), skills: ['Biology'],
    status: 'open', moderation: 'ok', pinned: false, posterId, posterName: displayNameFor(posterId),
    posterInitials: 'PU', proposalCount: 0, reportCount: 0, createdAt,
    updatedAt: serverTimestamp(), expiresAt: Timestamp.fromMillis(createdAt.toMillis() + 14 * 86400000),
    ...overrides,
  };
}
function offerSeed(offerId, sellerId, overrides = {}) {
  const createdAt = Timestamp.now();
  return {
    title: 'Biology study notes', category: 'Study materials', description: 'Carefully organised notes for biology students.',
    priceType: 'free', status: 'active', moderation: 'ok', pinned: false, sellerId,
    sellerName: displayNameFor(sellerId), sellerInitials: 'SU', reportCount: 0, createdAt,
    renewedAt: createdAt, updatedAt: serverTimestamp(),
    expiresAt: Timestamp.fromMillis(createdAt.toMillis() + 30 * 86400000), ...overrides,
  };
}
function user(uid, verified = true) {
  return testEnv.authenticatedContext(uid, { email_verified: verified, email: `${uid}@example.edu` }).firestore();
}
const allowedPublicIdAlphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
function publicIdFor(uid) {
  let n = 0;
  for (const ch of uid) n = (n * 31 + ch.charCodeAt(0)) >>> 0;
  let suffix = '';
  for (let i = 0; i < 7; i++) { suffix += allowedPublicIdAlphabet[n % allowedPublicIdAlphabet.length]; n = Math.floor(n / allowedPublicIdAlphabet.length) + 17 * (i + 1); }
  return `UNI-${suffix}`;
}
function displayNameFor(uid) { return uid.charAt(0).toUpperCase() + uid.slice(1); }
async function seed(pathName, id, data) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => setDoc(doc(ctx.firestore(), pathName, id), data));
}
async function seedProfile(uid, overrides = {}) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const publicId = publicIdFor(uid);
    await setDoc(doc(db, 'marketProfiles', uid), profileSeed(uid, overrides));
    await setDoc(doc(db, 'profiles', uid), {
      uid, publicId, displayName: displayNameFor(uid),
      createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
    });
    await setDoc(doc(db, 'studentIds', publicId), { uid, createdAt: Timestamp.now() });
  });
}
async function seedTask(taskId, posterId, overrides = {}) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => setDoc(doc(ctx.firestore(), 'tasks', taskId), taskSeed(taskId, posterId, overrides)));
}
async function seedOffer(offerId, sellerId, overrides = {}) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => setDoc(doc(ctx.firestore(), 'offers', offerId), offerSeed(offerId, sellerId, overrides)));
}
function marketConfigAuditBatch(adminUid, logId, configPatch) {
  const db = user(adminUid);
  const batch = writeBatch(db);
  batch.update(doc(db, 'config/market'), { lastAuditLogId: logId, ...configPatch });
  batch.set(doc(db, 'adminLogs', logId), {
    adminId: adminUid, action: 'update_market_config', targetType: 'config', targetId: 'market',
    reason: 'Market settings updated', note: 'Settings change test', createdAt: serverTimestamp(),
  });
  return batch.commit();
}
function adminListingMutationBatch({
  adminUid, collectionName, listingId, ownerUid, listingPatch, logId, noticeId,
  profilePatch = null, noticeType = 'activity', canAppeal = false, logTargetType = null, logTargetId = null,
}) {
  const db = user(adminUid);
  const batch = writeBatch(db);
  const targetType = collectionName === 'tasks' ? 'task' : 'offer';
  if (profilePatch) batch.update(doc(db, 'marketProfiles', ownerUid), profilePatch);
  batch.update(doc(db, collectionName, listingId), {
    ...listingPatch, lastAuditLogId: logId, lastAdminNoticeId: noticeId,
  });
  batch.set(doc(db, 'adminLogs', logId), {
    adminId: adminUid,
    action: targetType === 'task' ? 'moderate_task' : 'moderate_offer',
    targetType: logTargetType ?? targetType,
    targetId: logTargetId ?? listingId,
    reason: 'Listing moderation test',
    note: '',
    createdAt: serverTimestamp(),
  });
  const noticePath = 'users/' + ownerUid + '/notices';
  batch.set(doc(db, noticePath, noticeId), {
    type: noticeType,
    title: 'Listing moderation update',
    body: 'A moderator updated this listing.',
    relatedType: targetType,
    relatedId: listingId,
    read: false,
    createdAt: serverTimestamp(),
    canAppeal,
    appealed: false,
  });
  return batch;
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId,
    firestore: { rules: rulesText },
  });
});
beforeEach(async () => testEnv.clearFirestore());
after(async () => testEnv.cleanup());

describe('marketProfiles', () => {
  it('allows a verified student to create only fixed safe defaults', async () => {
    await seed('profiles', 'alice', { uid: 'alice', publicId: 'UNI-ABCDEFG', displayName: 'Alice', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    const db = user('alice');
    const p = profileSeed('alice');
    await assertSucceeds(setDoc(doc(db, 'marketProfiles', 'alice'), p));
  });
  it('denies a student attempting to create a suspended or banned profile', async () => {
    await assertFails(setDoc(doc(user('alice'), 'marketProfiles', 'alice'), profileSeed('alice', { status: 'banned' })));
  });
  it('denies owner edits to status, warnings, counters and suspension fields', async () => {
    await seedProfile('alice');
    await assertFails(updateDoc(doc(user('alice'), 'marketProfiles', 'alice'), { status: 'banned', warnings: 1, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(user('alice'), 'marketProfiles', 'alice'), { openTasks: 3, updatedAt: serverTimestamp() }));
  });
  it('allows owner preferences but denies deleting the profile', async () => {
    await seedProfile('alice');
    await assertSucceeds(updateDoc(doc(user('alice'), 'marketProfiles', 'alice'), { skillTags: ['Biology'], updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(user('alice'), 'marketProfiles', 'alice')));
  });
  it('requires a verified email for Market profile updates', async () => {
    await seedProfile('alice');
    await assertFails(updateDoc(
      doc(user('alice', false), 'marketProfiles', 'alice'),
      { skillTags: ['Biology'], updatedAt: serverTimestamp() },
    ));
    await assertSucceeds(updateDoc(
      doc(user('alice', true), 'marketProfiles', 'alice'),
      { skillTags: ['Biology'], updatedAt: serverTimestamp() },
    ));
  });
  it('allows an admin list/read but does not let a non-admin read another profile', async () => {
    await seedProfile('alice');
    await seed('admins', 'admin', {});
    await assertFails(getDoc(doc(user('bob'), 'marketProfiles', 'alice')));
    await assertSucceeds(getDoc(doc(user('admin'), 'admins', 'admin')));
  });
});

describe('tasks', () => {
  it('denies posting by a banned user', async () => {
    await seedProfile('alice', { status: 'banned' });
    await assertFails(setDoc(doc(user('alice'), 'tasks', 't1'), taskSeed('t1', 'alice')));
  });
  it('denies posting by a suspended user before suspendedUntil', async () => {
    await seedProfile('alice', { status: 'suspended', suspendedUntil: Timestamp.fromMillis(Date.now() + 3600000) });
    await assertFails(setDoc(doc(user('alice'), 'tasks', 't1'), taskSeed('t1', 'alice')));
  });
  it('task-create batch stays under Firestore access-call limits and allows post after suspension expiry according to request.time', async () => {
    await seedProfile('alice', { status: 'suspended', suspendedUntil: Timestamp.fromMillis(Date.now() - 60000) });
    const task = taskSeed('t1', 'alice');
    const db = user('alice');
    const b = writeBatch(db);
    b.set(doc(db, 'tasks', 't1'), task);
    b.update(doc(db, 'marketProfiles', 'alice'), {
      openTasks: 1, updatedAt: serverTimestamp(),
      lastMutation: { kind: 'task', operation: 'create', targetId: 't1', at: serverTimestamp() },
    });
    // This assertion intentionally exercises the real rules timestamp rather than a client-side timer.
    await assertSucceeds(b.commit());
  });
  it('denies an unverified user from posting', async () => {
    await seedProfile('alice');
    await assertFails(setDoc(doc(user('alice', false), 'tasks', 't1'), taskSeed('t1', 'alice')));
  });
  it('allows a public feed query constrained to moderation=ok and status=open', async () => {
    await seedTask('t1', 'alice');
    const db = user('bob');
    await assertSucceeds(getDocs(query(collection(db, 'tasks'), where('moderation', '==', 'ok'), where('status', '==', 'open'), orderBy('createdAt', 'desc'), limit(20))));
  });
  it('denies a feed query that can return hidden listings', async () => {
    await seedTask('t1', 'alice');
    await assertFails(getDocs(query(collection(user('bob'), 'tasks'), orderBy('createdAt', 'desc'), limit(20))));
  });
  it('denies owner updates to moderation, pinned and reportCount', async () => {
    await seedTask('alice_task', 'alice');
    const db = user('alice');
    await assertFails(updateDoc(doc(db, 'tasks', 'alice_task'), { pinned: true, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'tasks', 'alice_task'), { moderation: 'hidden', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'tasks', 'alice_task'), { reportCount: 1, updatedAt: serverTimestamp() }));
  });
  it('denies early expiry cleanup, allows only rule-valid lifecycle transitions', async () => {
    await seedTask('t1', 'alice');
    await assertFails(updateDoc(doc(user('alice'), 'tasks', 't1'), { status: 'expired', updatedAt: serverTimestamp() }));
  });
  it('denies hard deleting tasks', async () => {
    await seedTask('t1', 'alice');
    await assertFails(deleteDoc(doc(user('alice'), 'tasks', 't1')));
  });
  it('allows valid active-owner content edits and denies invalid or suspended content edits', async () => {
    await seedProfile('alice'); await seedTask('valid-edit', 'alice');
    await assertSucceeds(updateDoc(doc(user('alice'), 'tasks', 'valid-edit'), { title: 'Updated Biology notes', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(user('alice'), 'tasks', 'valid-edit'), { title: 'bad', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(user('alice'), 'tasks', 'valid-edit'), { category: '', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(user('alice'), 'tasks', 'valid-edit'), { description: 'too short', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(user('alice'), 'tasks', 'valid-edit'), { budgetAmount: -1, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(user('alice'), 'tasks', 'valid-edit'), { budgetType: 'not-a-budget-type', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(user('alice'), 'tasks', 'valid-edit'), { skills: [''], updatedAt: serverTimestamp() }));
    await seedProfile('suspended', { status: 'suspended', suspendedUntil: Timestamp.fromMillis(Date.now() + 3600000), openTasks: 1 });
    await seedTask('suspended-task', 'suspended');
    await assertFails(updateDoc(doc(user('suspended'), 'tasks', 'suspended-task'), { title: 'Edited while suspended', updatedAt: serverTimestamp() }));
    await seedProfile('banned', { status: 'banned', openTasks: 1 });
    await seedTask('banned-task', 'banned');
    await assertFails(updateDoc(doc(user('banned'), 'tasks', 'banned-task'), { title: 'Edited while banned', updatedAt: serverTimestamp() }));
  });

  it('allows suspended and banned owners lifecycle cleanup without allowing content edits', async () => {
    for (const uid of ['suspended', 'banned']) {
      await seedProfile(uid, { status: uid === 'suspended' ? 'suspended' : 'banned', suspendedUntil: uid === 'suspended' ? Timestamp.fromMillis(Date.now() + 3600000) : null, openTasks: 1 });
      const taskId = `cleanup-task-${uid}`;
      const expiry = Timestamp.fromMillis(Date.now() - 60000);
      const overrides = uid === 'banned' ? { createdAt: Timestamp.fromMillis(expiry.toMillis() - 14 * 86400000), expiresAt: expiry } : {};
      await seedTask(taskId, uid, overrides);
      const db = user(uid); const batch = writeBatch(db);
      batch.update(doc(db, 'tasks', taskId), { status: uid === 'suspended' ? 'cancelled' : 'expired', updatedAt: serverTimestamp() });
      batch.update(doc(db, 'marketProfiles', uid), { openTasks: 0, updatedAt: serverTimestamp(), lastMutation: { kind: 'task', operation: 'close', targetId: taskId, at: serverTimestamp() } });
      await assertSucceeds(batch.commit());
    }
  });
});

describe('offers', () => {
  it('denies banned users and unverified users from creating offers', async () => {
    await seedProfile('alice', { status: 'banned' });
    await assertFails(setDoc(doc(user('alice'), 'offers', 'o1'), offerSeed('o1', 'alice')));
    await seedProfile('bob');
    await assertFails(setDoc(doc(user('bob', false), 'offers', 'o2'), offerSeed('o2', 'bob')));
  });
  it('allows offer feed queries ordered by renewedAt rather than createdAt', async () => {
    await seedOffer('o1', 'alice');
    const db = user('bob');
    await assertSucceeds(getDocs(query(collection(db, 'offers'), where('moderation', '==', 'ok'), where('status', '==', 'active'), orderBy('renewedAt', 'desc'), limit(20))));
  });
  it('denies owner moderation/pin/reportCount edits and hard deletion', async () => {
    await seedOffer('o1', 'alice');
    const db = user('alice');
    await assertFails(updateDoc(doc(db, 'offers', 'o1'), { pinned: true, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'offers', 'o1'), { moderation: 'hidden', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'offers', 'o1'), { reportCount: 1, updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(db, 'offers', 'o1')));
  });
  it('validates offer content edits, blocks suspended content changes and banned content changes, and allows lifecycle cleanup', async () => {
    await seedProfile('alice'); await seedOffer('offer-edit', 'alice');
    await assertSucceeds(updateDoc(doc(user('alice'), 'offers', 'offer-edit'), { title: 'Updated biology materials', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(user('alice'), 'offers', 'offer-edit'), { title: 'no', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(user('alice'), 'offers', 'offer-edit'), { priceType: 'amount', priceAmount: 25, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(user('alice'), 'offers', 'offer-edit'), { priceType: 'amount', priceAmount: -1, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(user('alice'), 'offers', 'offer-edit'), { category: '', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(user('alice'), 'offers', 'offer-edit'), { description: '', updatedAt: serverTimestamp() }));
    await seedProfile('suspended', { status: 'suspended', suspendedUntil: Timestamp.fromMillis(Date.now() + 3600000), activeOffers: 1 });
    await seedOffer('suspended-offer', 'suspended');
    await assertFails(updateDoc(doc(user('suspended'), 'offers', 'suspended-offer'), { description: 'Changed while suspended', updatedAt: serverTimestamp() }));
    await seedProfile('banned', { status: 'banned', activeOffers: 1 });
    await seedOffer('banned-offer', 'banned');
    await assertFails(updateDoc(doc(user('banned'), 'offers', 'banned-offer'), { priceAmount: 99, updatedAt: serverTimestamp() }));
    for (const uid of ['suspended', 'banned']) {
      const offerId = uid === 'suspended' ? 'suspended-offer' : 'banned-offer';
      const db = user(uid); const batch = writeBatch(db);
      batch.update(doc(db, 'offers', offerId), { status: uid === 'suspended' ? 'paused' : 'removed', updatedAt: serverTimestamp() });
      batch.update(doc(db, 'marketProfiles', uid), { activeOffers: 0, updatedAt: serverTimestamp(), lastMutation: { kind: 'offer', operation: 'deactivate', targetId: offerId, at: serverTimestamp() } });
      await assertSucceeds(batch.commit());
    }
  });
});

describe('proposals and single accept', () => {
  it('denies a proposal from the task poster', async () => {
    await seedProfile('alice'); await seedTask('t1', 'alice');
    const db = user('alice');
    await assertFails(setDoc(doc(db, 'tasks', 't1', 'proposals', 'alice'), {
      proposerId: 'alice', message: 'This proposal text is long enough to satisfy length', price: 0, deliveryDays: 1,
      status: 'sent', proposerName: displayNameFor('alice'), proposerInitials: 'AL', createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
  });
  it('denies a 16th proposal even when the required batch writes are supplied', async () => {
    await seedProfile('alice');
    await seedProfile('bob', { proposalWindowCount: 0 });
    await seedTask('t1', 'alice', { proposalCount: 15 });
    const db = user('bob'); const batch = writeBatch(db);
    batch.set(doc(db, 'tasks', 't1', 'proposals', 'bob'), {
      proposerId: 'bob', message: 'This proposal text is long enough to satisfy length', price: 0, deliveryDays: 1,
      status: 'sent', proposerName: displayNameFor('bob'), proposerInitials: 'BO', createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    });
    batch.update(doc(db, 'tasks', 't1'), { proposalCount: 16, updatedAt: serverTimestamp() });
    batch.update(doc(db, 'marketProfiles', 'bob'), {
      proposalWindowCount: 1, proposalWindowStartedAt: serverTimestamp(), updatedAt: serverTimestamp(),
      lastMutation: { kind: 'proposal', operation: 'create', targetId: 't1', at: serverTimestamp() },
    });
    await assertFails(batch.commit());
  });
  it('denies direct acceptance without the parent task switching to assigned in the same batch', async () => {
    await seedTask('t1', 'alice');
    await seed('tasks/t1/proposals', 'bob', { proposerId: 'bob', message: 'This proposal text is long enough to satisfy length', price: 0, deliveryDays: 1, status: 'sent', proposerName: displayNameFor('bob'), proposerInitials: 'BO', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await assertFails(updateDoc(doc(user('alice'), 'tasks', 't1', 'proposals', 'bob'), { status: 'accepted', updatedAt: serverTimestamp() }));
  });
  it('denies a second accept after a task is already assigned', async () => {
    await seedTask('t1', 'alice', { status: 'assigned', assignedTo: 'bob' });
    await seed('tasks/t1/proposals', 'bob', { proposerId: 'bob', message: 'This proposal text is long enough to satisfy length', price: 0, deliveryDays: 1, status: 'accepted', proposerName: displayNameFor('bob'), proposerInitials: 'BO', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await seed('tasks/t1/proposals', 'carol', { proposerId: 'carol', message: 'This proposal text is long enough to satisfy length', price: 0, deliveryDays: 1, status: 'sent', proposerName: displayNameFor('carol'), proposerInitials: 'CA', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await assertFails(updateDoc(doc(user('alice'), 'tasks', 't1', 'proposals', 'carol'), { status: 'accepted', updatedAt: serverTimestamp() }));
  });
  it('allows the Applied collection-group query only for the caller proposerId, while preserving poster task access', async () => {
    await seedProfile('alice'); await seedProfile('bob'); await seedProfile('carol');
    await seedTask('t1', 'alice'); await seedTask('t2', 'alice');
    await seed('tasks/t1/proposals', 'bob', { proposerId: 'bob', message: 'I can help organise these notes in a useful way.', price: 0, deliveryDays: 1, status: 'sent', proposerName: displayNameFor('bob'), proposerInitials: 'BO', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await seed('tasks/t2/proposals', 'carol', { proposerId: 'carol', message: 'I can help organise these notes in a useful way.', price: 0, deliveryDays: 1, status: 'sent', proposerName: displayNameFor('carol'), proposerInitials: 'CA', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    const bobDb = user('bob');
    await assertSucceeds(getDocs(query(collectionGroup(bobDb, 'proposals'), where('proposerId', '==', 'bob'), orderBy('updatedAt', 'desc'), limit(20))));
    await assertFails(getDocs(query(collectionGroup(bobDb, 'proposals'), orderBy('updatedAt', 'desc'), limit(20))));
    await assertFails(getDocs(query(collectionGroup(bobDb, 'proposals'), where('proposerId', '==', 'carol'), orderBy('updatedAt', 'desc'), limit(20))));
    await assertFails(getDoc(doc(bobDb, 'tasks', 't2', 'proposals', 'carol')));
    await assertSucceeds(getDocs(collection(user('alice'), 'tasks', 't1', 'proposals')));
  });

  it('denies proposal creation when proposerId does not equal document id and auth UID', async () => {
    await seedProfile('alice'); await seedProfile('bob'); await seedTask('t1', 'alice');
    const db = user('bob'); const batch = writeBatch(db);
    batch.set(doc(db, 'tasks', 't1', 'proposals', 'bob'), {
      proposerId: 'carol', message: 'This proposal text is long enough to satisfy length', price: 0, deliveryDays: 1,
      status: 'sent', proposerName: displayNameFor('bob'), proposerInitials: 'BO', createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    });
    batch.update(doc(db, 'tasks', 't1'), { proposalCount: 1, updatedAt: serverTimestamp() });
    batch.update(doc(db, 'marketProfiles', 'bob'), {
      proposalWindowCount: 1, proposalWindowStartedAt: serverTimestamp(), updatedAt: serverTimestamp(),
      lastMutation: { kind: 'proposal', operation: 'create', targetId: 't1', at: serverTimestamp() },
    });
    await assertFails(batch.commit());
  });

  it('denies attempts to change the immutable proposerId', async () => {
    await seedTask('t1', 'alice');
    await seed('tasks/t1/proposals', 'bob', { proposerId: 'bob', message: 'This proposal text is long enough to satisfy length', price: 0, deliveryDays: 1, status: 'sent', proposerName: displayNameFor('bob'), proposerInitials: 'BO', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await assertFails(updateDoc(doc(user('bob'), 'tasks', 't1', 'proposals', 'bob'), { proposerId: 'carol', updatedAt: serverTimestamp() }));
  });
});

describe('reports and ratings', () => {
  it('prevents duplicate reports by deterministic report IDs', async () => {
    await seedProfile('bob'); await seedTask('t1', 'alice');
    const db = user('bob');
    const report = { targetType: 'task', targetId: 't1', reporterId: 'bob', reason: 'spam', createdAt: serverTimestamp(), status: 'open' };
    await assertFails(setDoc(doc(db, 'reports', 'task_t1_bob'), report)); // missing linked counter mutation
    await testEnv.withSecurityRulesDisabled(async ctx => setDoc(doc(ctx.firestore(), 'reports', 'task_t1_bob'), { ...report, createdAt: Timestamp.now() }));
    await assertFails(setDoc(doc(db, 'reports', 'task_t1_bob'), report));
  });
  it('denies direct reportCount increments without a same-batch report', async () => {
    await seedTask('t1', 'alice');
    await assertFails(updateDoc(doc(user('alice'), 'tasks', 't1'), { reportCount: 1, updatedAt: serverTimestamp() }));
  });
  it('denies a rating for a task that has not completed', async () => {
    await seedTask('t1', 'alice');
    await assertFails(setDoc(doc(user('alice'), 'ratings', 't1_alice'), {
      taskId: 't1', fromUid: 'alice', toUid: 'bob', stars: 5, tags: [], comment: '', createdAt: serverTimestamp(),
    }));
  });
  it('denies client writes to userStats', async () => {
    await assertFails(setDoc(doc(user('alice'), 'userStats', 'alice'), { ratingSum: 0, ratingCount: 0, completedCount: 0 }));
  });
});

describe('notices, support, admin data and config', () => {
  it('lets notice owners change read/appealed only', async () => {
    await seed('users/alice/notices', 'n1', { type: 'warning', title: 'Notice', body: 'Body', read: false, createdAt: Timestamp.now(), canAppeal: true, appealed: false });
    const db = user('alice');
    await assertSucceeds(updateDoc(doc(db, 'users/alice/notices/n1'), { read: true }));
    await assertFails(updateDoc(doc(db, 'users/alice/notices/n1'), { title: 'Tampered' }));
  });
  it('denies reading another user notice and support ticket', async () => {
    await seed('users/alice/notices', 'n1', { type: 'warning', title: 'Notice', body: 'Body', read: false, createdAt: Timestamp.now(), canAppeal: true, appealed: false });
    await seed('support', 's1', { userId: 'alice', type: 'bug', subject: 'Bug report', body: 'Long enough support ticket details', status: 'open', replies: [], createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await assertFails(getDoc(doc(user('bob'), 'users/alice/notices/n1')));
    await assertFails(getDoc(doc(user('bob'), 'support/s1')));
  });
  it('denies non-admin edits to config and admin logs', async () => {
    await seed('config', 'market', { announcement: { text: '', active: false, until: null }, blockedWords: [], rankWeights: { newness: 60, rating: 30, match: 10 }, limits: { openTasks: 3, proposalsPerDay: 10, proposalsPerTask: 15, activeOffers: 5, reportsToHide: 3 } });
    await assertFails(updateDoc(doc(user('alice'), 'config/market'), { blockedWords: ['word'] }));
    await assertFails(setDoc(doc(user('alice'), 'adminLogs', 'l1'), { adminId: 'alice', action: 'hide', targetType: 'task', targetId: 't1', reason: 'reason', note: '', createdAt: serverTimestamp() }));
  });
  it('does not allow client-created admin membership', async () => {
    await assertFails(setDoc(doc(user('alice'), 'admins', 'alice'), {}));
  });
  it('allows only admins to create audit log entries, and forbids edits/deletes', async () => {
    await seed('admins', 'admin', {});
    const data = { adminId: 'admin', action: 'hide', targetType: 'task', targetId: 't1', reason: 'rule violation', note: '', createdAt: serverTimestamp() };
    await assertSucceeds(setDoc(doc(user('admin'), 'adminLogs', 'l1'), data));
    await assertFails(updateDoc(doc(user('admin'), 'adminLogs', 'l1'), { note: 'changed' }));
    await assertFails(deleteDoc(doc(user('admin'), 'adminLogs', 'l1')));
  });
});

describe('Market conversations and existing U2U regression', () => {
  it('denies Market conversation creation when either participant blocks the other', async () => {
    await seedProfile('alice', { blockedUids: ['bob'] }); await seedProfile('bob');
    await seedTask('t1', 'alice', { status: 'assigned', assignedTo: 'bob' });
    const participants = ['alice', 'bob'].sort();
    await assertFails(setDoc(doc(user('bob'), 'marketConversations', 'task_t1_alice_bob'), {
      participants, kind: 'task', listingId: 't1', taskId: 't1', listingOwnerUid: 'alice',
      createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
  });
  it('denies an admin from reading a Market conversation even if their UID is an admin', async () => {
    await seed('admins', 'admin', {});
    await seed('marketConversations', 'offer_o1_admin_bob', { participants: ['admin', 'bob'], kind: 'offer', listingId: 'o1', offerId: 'o1', listingOwnerUid: 'bob', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await assertFails(getDoc(doc(user('admin'), 'marketConversations', 'offer_o1_admin_bob')));
  });
  it('preserves existing friend chat access for ordinary accepted participants', async () => {
    await seed('profiles', 'alice', { uid: 'alice', publicId: 'UNI-ABCDEFG', displayName: 'Alice', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await seed('profiles', 'bob', { uid: 'bob', publicId: 'UNI-BCDEFGH', displayName: 'Bob', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await seed('friendRequests', 'alice_bob', { senderUid: 'alice', receiverUid: 'bob', senderName: 'Alice', senderPublicId: 'UNI-ABCDEFG', receiverName: 'Bob', receiverPublicId: 'UNI-BCDEFGH', status: 'accepted', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await seed('chatConversations', 'alice_bob', { participantUids: ['alice', 'bob'], createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await assertSucceeds(getDoc(doc(user('alice'), 'chatConversations/alice_bob')));
  });
  it('preserves Nearby-transfer rule privacy for ordinary U2U participants', async () => {
    await seed('profiles', 'alice', { uid: 'alice', publicId: 'UNI-ABCDEFG', displayName: 'Alice', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await seed('profiles', 'bob', { uid: 'bob', publicId: 'UNI-BCDEFGH', displayName: 'Bob', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await seed('friendRequests', 'alice_bob', { senderUid: 'alice', receiverUid: 'bob', senderName: 'Alice', senderPublicId: 'UNI-ABCDEFG', receiverName: 'Bob', receiverPublicId: 'UNI-BCDEFGH', status: 'accepted', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await seed('chatConversations', 'alice_bob', { participantUids: ['alice', 'bob'], createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await seed('chatConversations/alice_bob/transferOffers', 'x1', { transferId: 'x1', conversationId: 'alice_bob', senderUid: 'alice', receiverUid: 'bob', fileName: 'note.pdf', sizeBytes: 10, mimeType: 'application/pdf', sha256: 'a'.repeat(64), chunkSize: 16384, senderPublicKey: 'A'.repeat(64), status: 'offered', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await assertSucceeds(getDoc(doc(user('alice'), 'chatConversations/alice_bob/transferOffers/x1')));
  });
});

describe('rolling 24-hour windows and paired mutation counters', () => {
  async function submitProposalWithWindow(uid, windowCount, windowStart, taskId = 'window-task') {
    const db = user(uid);
    const batch = writeBatch(db);
    const proposalRef = doc(db, 'tasks', taskId, 'proposals', uid);
    const taskRef = doc(db, 'tasks', taskId);
    const profileRef = doc(db, 'marketProfiles', uid);
    batch.set(proposalRef, {
      proposerId: uid, message: 'I can help organise and explain these notes clearly.', price: 0, deliveryDays: 2,
      status: 'sent', proposerName: displayNameFor(uid), proposerInitials: uid.slice(0, 2).toUpperCase(),
      createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    });
    batch.update(taskRef, { proposalCount: 1, updatedAt: serverTimestamp() });
    batch.update(profileRef, {
      proposalWindowCount: windowCount,
      proposalWindowStartedAt: windowStart,
      lastMutation: { kind: 'proposal', operation: 'create', targetId: taskId, at: serverTimestamp() },
      updatedAt: serverTimestamp(),
    });
    return batch.commit();
  }

  it('proposal-create batch stays under Firestore access-call limits and allows the tenth proposal at 23h59m', async () => {
    await seedProfile('alice');
    const start = Timestamp.fromMillis(Date.now() - (23 * 60 + 59) * 60_000);
    await seedProfile('bob', { proposalWindowCount: 9, proposalWindowStartedAt: start });
    await seedTask('window-task', 'alice', { proposalCount: 0 });
    await assertSucceeds(submitProposalWithWindow('bob', 10, start));
  });

  it('resets proposal count to one at 24h01m using request.time', async () => {
    await seedProfile('alice');
    await seedProfile('bob', {
      proposalWindowCount: 10,
      proposalWindowStartedAt: Timestamp.fromMillis(Date.now() - (24 * 60 + 1) * 60_000),
    });
    await seedTask('window-task', 'alice', { proposalCount: 0 });
    await assertSucceeds(submitProposalWithWindow('bob', 1, serverTimestamp()));
  });

  it('denies proposal eleven within the same rolling window', async () => {
    await seedProfile('alice');
    const start = Timestamp.fromMillis(Date.now() - 2 * 60 * 60_000);
    await seedProfile('bob', { proposalWindowCount: 10, proposalWindowStartedAt: start });
    await seedTask('window-task', 'alice', { proposalCount: 0 });
    await assertFails(submitProposalWithWindow('bob', 11, start));
  });
});

describe('counter drift recovery and lifecycle transitions', () => {
  it('lets the owner expire a task only after expiresAt and decrements openTasks atomically', async () => {
    await seedProfile('alice', { openTasks: 1 });
    const old = Timestamp.fromMillis(Date.now() - 60_000);
    await seedTask('expired-task', 'alice', { createdAt: Timestamp.fromMillis(old.toMillis() - 14 * 86400000), expiresAt: old });
    const db = user('alice'); const batch = writeBatch(db);
    batch.update(doc(db, 'tasks', 'expired-task'), { status: 'expired', updatedAt: serverTimestamp() });
    batch.update(doc(db, 'marketProfiles', 'alice'), {
      openTasks: 0, updatedAt: serverTimestamp(),
      lastMutation: { kind: 'task', operation: 'close', targetId: 'expired-task', at: serverTimestamp() },
    });
    await assertSucceeds(batch.commit());
  });

  it('lets the owner expire an offer only after expiresAt and decrements activeOffers atomically', async () => {
    await seedProfile('alice', { activeOffers: 1 });
    const old = Timestamp.fromMillis(Date.now() - 60_000);
    await seedOffer('expired-offer', 'alice', {
      createdAt: Timestamp.fromMillis(old.toMillis() - 30 * 86400000), renewedAt: Timestamp.fromMillis(old.toMillis() - 30 * 86400000), expiresAt: old,
    });
    const db = user('alice'); const batch = writeBatch(db);
    batch.update(doc(db, 'offers', 'expired-offer'), { status: 'expired', updatedAt: serverTimestamp() });
    batch.update(doc(db, 'marketProfiles', 'alice'), {
      activeOffers: 0, updatedAt: serverTimestamp(),
      lastMutation: { kind: 'offer', operation: 'deactivate', targetId: 'expired-offer', at: serverTimestamp() },
    });
    await assertSucceeds(batch.commit());
  });

  it('allows atomic soft removal of an active offer but denies a counter-free active removal', async () => {
    await seedProfile('alice', { activeOffers: 1 });
    await seedOffer('remove-offer', 'alice');
    const db = user('alice');
    await assertFails(updateDoc(doc(db, 'offers', 'remove-offer'), { status: 'removed', updatedAt: serverTimestamp() }));
    const batch = writeBatch(db);
    batch.update(doc(db, 'offers', 'remove-offer'), { status: 'removed', updatedAt: serverTimestamp() });
    batch.update(doc(db, 'marketProfiles', 'alice'), {
      activeOffers: 0, updatedAt: serverTimestamp(),
      lastMutation: { kind: 'offer', operation: 'deactivate', targetId: 'remove-offer', at: serverTimestamp() },
    });
    await assertSucceeds(batch.commit());
  });

  it('admin without a U2U profile suspends/hides with notice and log in one access-limit batch', async () => {
    await seed('admins', 'admin', {});
    await seedProfile('alice', { openTasks: 2, activeOffers: 1 });
    await seedTask('task-a', 'alice'); await seedTask('task-b', 'alice'); await seedOffer('offer-a', 'alice');
    const db = user('admin'); const batch = writeBatch(db);
    batch.update(doc(db, 'marketProfiles', 'alice'), {
      status: 'suspended', suspendedUntil: Timestamp.fromMillis(Date.now() + 7 * 86400000), warnings: 2,
      openTasks: 0, activeOffers: 0,
      lastMutation: { kind: 'user', operation: 'suspend', targetId: 'alice', at: serverTimestamp() },
      updatedAt: serverTimestamp(),
    });
    batch.update(doc(db, 'tasks', 'task-a'), {
      moderation: 'hidden', updatedAt: serverTimestamp(),
      lastAuditLogId: 'suspend-alice', lastAdminNoticeId: 'suspension-notice',
    });
    batch.update(doc(db, 'tasks', 'task-b'), {
      moderation: 'hidden', updatedAt: serverTimestamp(),
      lastAuditLogId: 'suspend-alice', lastAdminNoticeId: 'suspension-notice',
    });
    batch.update(doc(db, 'offers', 'offer-a'), {
      moderation: 'hidden', updatedAt: serverTimestamp(),
      lastAuditLogId: 'suspend-alice', lastAdminNoticeId: 'suspension-notice',
    });
    batch.set(doc(db, 'users/alice/notices', 'suspension-notice'), {
      type: 'removal', title: 'Market account suspended', body: 'Your Market account was suspended.', read: false,
      createdAt: serverTimestamp(), canAppeal: true, appealed: false,
    });
    batch.set(doc(db, 'adminLogs', 'suspend-alice'), {
      adminId: 'admin', action: 'suspend_user', targetType: 'user', targetId: 'alice', reason: 'Policy violation', note: '', createdAt: serverTimestamp(),
    });
    await assertSucceeds(batch.commit());
  });
});

describe('reports, ratings, support, notices, config and admin permissions', () => {
  it('report batch stays under Firestore access-call limits and increments reportCount exactly once', async () => {
    await seedProfile('alice'); await seedProfile('bob'); await seedTask('reported-task', 'alice');
    const db = user('bob'); const batch = writeBatch(db);
    batch.set(doc(db, 'reports', 'task_reported-task_bob'), {
      targetType: 'task', targetId: 'reported-task', reporterId: 'bob', reason: 'spam',
      createdAt: serverTimestamp(), status: 'open',
    });
    batch.update(doc(db, 'tasks', 'reported-task'), { reportCount: 1, updatedAt: serverTimestamp() });
    batch.update(doc(db, 'marketProfiles', 'bob'), {
      reportWindowCount: 1, reportWindowStartedAt: serverTimestamp(), updatedAt: serverTimestamp(),
      lastMutation: { kind: 'report', operation: 'create', targetType: 'task', targetId: 'reported-task', at: serverTimestamp() },
    });
    await assertSucceeds(batch.commit());
    await assertFails(setDoc(doc(db, 'reports', 'task_reported-task_bob'), {
      targetType: 'task', targetId: 'reported-task', reporterId: 'bob', reason: 'spam', createdAt: serverTimestamp(), status: 'open',
    }));
  });

  it('allows a rating only for a completed task and updates userStats in the same batch', async () => {
    await seedProfile('alice'); await seedProfile('bob');
    await seedTask('completed-task', 'alice', { status: 'completed', assignedTo: 'bob' });
    const db = user('alice'); const batch = writeBatch(db);
    batch.set(doc(db, 'ratings', 'completed-task_alice'), {
      taskId: 'completed-task', fromUid: 'alice', toUid: 'bob', stars: 5, tags: ['helpful'], comment: 'Great help.', createdAt: serverTimestamp(),
    });
    batch.set(doc(db, 'userStats', 'bob'), {
      ratingSum: 5, ratingCount: 1, completedCount: 1, updatedAt: serverTimestamp(),
      lastMutation: { kind: 'rating', operation: 'create', targetId: 'completed-task_alice', at: serverTimestamp() },
    });
    await assertSucceeds(batch.commit());
    await assertFails(setDoc(doc(db, 'ratings', 'completed-task_alice'), {
      taskId: 'completed-task', fromUid: 'alice', toUid: 'bob', stars: 5, tags: [], comment: '', createdAt: serverTimestamp(),
    }));
  });

  it('allows an owner to create a support ticket with the matching counter, append one user reply, and blocks other readers', async () => {
    await seedProfile('alice');
    const db = user('alice'); const batch = writeBatch(db);
    batch.set(doc(db, 'support', 'ticket-1'), {
      userId: 'alice', type: 'bug', subject: 'Market screen problem', body: 'The requests feed does not refresh correctly.',
      status: 'open', replies: [], createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    });
    batch.update(doc(db, 'marketProfiles', 'alice'), {
      openSupportTickets: 1, updatedAt: serverTimestamp(),
      lastMutation: { kind: 'support', operation: 'create', targetId: 'ticket-1', at: serverTimestamp() },
    });
    await assertSucceeds(batch.commit());
    await assertFails(getDoc(doc(user('bob'), 'support', 'ticket-1')));
    await assertSucceeds(updateDoc(doc(db, 'support', 'ticket-1'), {
      replies: [{ from: 'user', text: 'I can provide a screenshot if needed.', at: Timestamp.now() }], updatedAt: serverTimestamp(),
    }));
  });

  it('allows an admin to create a notice, but the owner can edit only read and appeal state', async () => {
    await seed('admins', 'admin', {});
    const adminDb = user('admin');
    await assertSucceeds(setDoc(doc(adminDb, 'users/alice/notices/n-admin'), {
      type: 'warning', title: 'Please review the posting rules', body: 'A moderator has issued a warning.',
      read: false, createdAt: serverTimestamp(), canAppeal: true, appealed: false,
    }));
    const aliceDb = user('alice');
    await assertSucceeds(updateDoc(doc(aliceDb, 'users/alice/notices/n-admin'), { read: true }));
    await assertFails(updateDoc(doc(aliceDb, 'users/alice/notices/n-admin'), { body: 'Tampered text' }));
  });

  it('lets signed-in users read config and requires a matching new audit log for admin edits', async () => {
    await seed('config', 'market', {
      announcement: { text: '', active: false, until: null }, blockedWords: [],
      rankWeights: { newness: 60, rating: 30, match: 10 },
      limits: { openTasks: 3, proposalsPerDay: 10, proposalsPerTask: 15, activeOffers: 5, reportsToHide: 3 },
    });
    await assertSucceeds(getDoc(doc(user('alice'), 'config/market')));
    await assertFails(updateDoc(doc(user('alice'), 'config/market'), { blockedWords: ['spamword'] }));
    await seed('admins', 'admin', {});
    await assertFails(updateDoc(doc(user('admin'), 'config/market'), {
      blockedWords: ['without-audit'], lastAuditLogId: 'missing-config-audit',
    }));
    await assertSucceeds(marketConfigAuditBatch('admin', 'config-blocked-words', { blockedWords: ['spamword'] }));
    await assertFails(updateDoc(doc(user('admin'), 'config/market'), { blockedWords: ['missing-pointer-update'] }));
    await assertSucceeds(marketConfigAuditBatch('admin', 'config-rank-weights', {
      rankWeights: { newness: 50, rating: 30, match: 20 },
    }));
    await assertFails(marketConfigAuditBatch('admin', 'config-negative-newness', {
      rankWeights: { newness: -10, rating: 90, match: 20 },
    }));
    await assertFails(marketConfigAuditBatch('admin', 'config-negative-rating', {
      rankWeights: { newness: 70, rating: -1, match: 31 },
    }));
    await assertFails(marketConfigAuditBatch('admin', 'config-negative-match', {
      rankWeights: { newness: 70, rating: 31, match: -1 },
    }));
    await assertFails(marketConfigAuditBatch('admin', 'config-fixed-limits', {
      limits: { openTasks: 4, proposalsPerDay: 10, proposalsPerTask: 15, activeOffers: 5, reportsToHide: 3 },
    }));
    await assertFails(marketConfigAuditBatch('admin', 'config-wrong-audit-target', {
      blockedWords: ['mismatch'],
      lastAuditLogId: 'not-the-created-log',
    }));
  });

  it('does not permit a regular user to list marketProfiles or inspect another user profile', async () => {
    await seedProfile('alice'); await seedProfile('bob');
    await assertFails(getDocs(collection(user('bob'), 'marketProfiles')));
    await assertFails(getDoc(doc(user('bob'), 'marketProfiles', 'alice')));
    await assertSucceeds(getDoc(doc(user('alice'), 'marketProfiles', 'alice')));
  });
});

describe('Market chat positive path and admin exclusion from Market chats', () => {
  it('creates a Market task chat and allows only participants to send/read messages without a friend request', async () => {
    await seedProfile('alice'); await seedProfile('bob');
    await seedTask('assigned-task', 'alice', { status: 'assigned', assignedTo: 'bob' });
    const participants = ['alice', 'bob'];
    await assertSucceeds(setDoc(doc(user('bob'), 'marketConversations', 'task_assigned-task_alice_bob'), {
      participants, kind: 'task', listingId: 'assigned-task', taskId: 'assigned-task', listingOwnerUid: 'alice',
      createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
    await assertSucceeds(setDoc(doc(user('bob'), 'marketConversations/task_assigned-task_alice_bob/messages/m1'), {
      senderUid: 'bob', recipientUid: 'alice', senderName: displayNameFor('bob'),
      text: 'I will send the notes shortly.', createdAt: serverTimestamp(),
    }));
    await assertSucceeds(getDoc(doc(user('alice'), 'marketConversations/task_assigned-task_alice_bob/messages/m1')));
    await assertFails(getDoc(doc(user('carol'), 'marketConversations/task_assigned-task_alice_bob/messages/m1')));
    const conversationRef = doc(user('bob'), 'marketConversations/task_assigned-task_alice_bob');
    await assertSucceeds(updateDoc(conversationRef, {
      lastMessage: { text: 'I will send the notes shortly.', senderUid: 'bob', at: serverTimestamp() },
      updatedAt: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(conversationRef, { lastReadAt: { bob: serverTimestamp() }, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(conversationRef, { lastReadAt: { bob: serverTimestamp(), alice: serverTimestamp() }, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(conversationRef, {
      lastMessage: { text: 'This message claims to be from Alice', senderUid: 'alice', at: serverTimestamp() },
      updatedAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(conversationRef, {
      lastMessage: { text: 'x'.repeat(101), senderUid: 'bob', at: serverTimestamp() },
      updatedAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(conversationRef, {
      lastMessage: { text: 'Stale preview timestamp', senderUid: 'bob', at: Timestamp.fromMillis(Date.now() - 60000) },
      updatedAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(conversationRef, { listingId: 'another-listing', updatedAt: serverTimestamp() }));
    await assertFails(setDoc(doc(user('bob'), 'marketConversations', 'task_assigned-task_bob_alice'), {
      participants: ['bob', 'alice'], kind: 'task', listingId: 'assigned-task', taskId: 'assigned-task', listingOwnerUid: 'alice',
      createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(user('bob'), 'marketConversations', 'task_different-id_alice_bob'), {
      participants, kind: 'task', listingId: 'assigned-task', taskId: 'assigned-task', listingOwnerUid: 'alice',
      createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
  });

  it('keeps the admin account outside existing friend chats and Nearby transfers when it is not a participant', async () => {
    await seed('admins', 'admin', {});
    await seed('profiles', 'alice', { uid: 'alice', publicId: 'UNI-ABCDEFG', displayName: 'Alice', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await seed('profiles', 'bob', { uid: 'bob', publicId: 'UNI-BCDEFGH', displayName: 'Bob', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await seed('friendRequests', 'alice_bob', {
      senderUid: 'alice', receiverUid: 'bob', senderName: 'Alice', senderPublicId: 'UNI-ABCDEFG',
      receiverName: 'Bob', receiverPublicId: 'UNI-BCDEFGH', status: 'accepted', createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
    });
    await seed('chatConversations', 'alice_bob', { participantUids: ['alice', 'bob'], createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await seed('chatConversations/alice_bob/transferOffers', 'transfer-1', {
      transferId: 'transfer-1', conversationId: 'alice_bob', senderUid: 'alice', receiverUid: 'bob',
      fileName: 'notes.pdf', sizeBytes: 100, mimeType: 'application/pdf', sha256: 'a'.repeat(64), chunkSize: 16384,
      senderPublicKey: 'A'.repeat(44), status: 'offered', createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
    });
    await assertFails(getDoc(doc(user('admin'), 'chatConversations/alice_bob')));
    await assertFails(getDoc(doc(user('admin'), 'chatConversations/alice_bob/transferOffers/transfer-1')));
    await assertSucceeds(getDoc(doc(user('alice'), 'chatConversations/alice_bob')));
    await assertSucceeds(getDoc(doc(user('bob'), 'chatConversations/alice_bob/transferOffers/transfer-1')));
  });
});

describe('additional allowed flows and daily-limit denial coverage', () => {
  it('allows active offer creation only with the matching activeOffers increment', async () => {
    await seedProfile('alice');
    const db = user('alice'); const batch = writeBatch(db);
    batch.set(doc(db, 'offers', 'new-offer'), offerSeed('new-offer', 'alice'));
    batch.update(doc(db, 'marketProfiles', 'alice'), {
      activeOffers: 1, updatedAt: serverTimestamp(),
      lastMutation: { kind: 'offer', operation: 'create', targetId: 'new-offer', at: serverTimestamp() },
    });
    await assertSucceeds(batch.commit());
  });

  it('accept transaction plus same-transaction task chat stays under Firestore access-call limits', async () => {
    await seedProfile('alice', { openTasks: 1 }); await seedProfile('bob');
    await seedTask('accept-task', 'alice', { proposalCount: 1 });
    await seed('tasks/accept-task/proposals', 'bob', {
      proposerId: 'bob', message: 'I can help organise these notes and explain difficult topics.', price: 0, deliveryDays: 2,
      status: 'sent', proposerName: displayNameFor('bob'), proposerInitials: 'BO', createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
    });
    const db = user('alice');
    const taskRef = doc(db, 'tasks', 'accept-task');
    const proposalRef = doc(db, 'tasks', 'accept-task', 'proposals', 'bob');
    const profileRef = doc(db, 'marketProfiles', 'alice');
    const conversationRef = doc(db, 'marketConversations', 'task_accept-task_alice_bob');
    await assertSucceeds(runTransaction(db, async (transaction) => {
      // Transactions must complete all reads before writes; this also checks the poster's existing proposal access.
      await transaction.get(taskRef);
      await transaction.get(proposalRef);
      transaction.update(taskRef, { status: 'assigned', assignedTo: 'bob', updatedAt: serverTimestamp() });
      transaction.update(proposalRef, { status: 'accepted', updatedAt: serverTimestamp() });
      transaction.update(profileRef, {
        openTasks: 0, updatedAt: serverTimestamp(),
        lastMutation: { kind: 'task', operation: 'close', targetId: 'accept-task', at: serverTimestamp() },
      });
      transaction.set(conversationRef, {
        participants: ['alice', 'bob'], kind: 'task', listingId: 'accept-task', taskId: 'accept-task', listingOwnerUid: 'alice',
        createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
      });
    }));
  });

  it('denies task acceptance when the same transaction omits the new Market conversation', async () => {
    await seedProfile('alice', { openTasks: 1 }); await seedProfile('bob');
    await seedTask('accept-without-chat', 'alice', { proposalCount: 1 });
    await seed('tasks/accept-without-chat/proposals', 'bob', {
      proposerId: 'bob', message: 'I can help organise these notes and explain difficult topics.', price: 0, deliveryDays: 2,
      status: 'sent', proposerName: displayNameFor('bob'), proposerInitials: 'BO', createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
    });
    const db = user('alice');
    const taskRef = doc(db, 'tasks', 'accept-without-chat');
    const proposalRef = doc(db, 'tasks', 'accept-without-chat', 'proposals', 'bob');
    const profileRef = doc(db, 'marketProfiles', 'alice');
    await assertFails(runTransaction(db, async (transaction) => {
      await transaction.get(taskRef);
      await transaction.get(proposalRef);
      transaction.update(taskRef, { status: 'assigned', assignedTo: 'bob', updatedAt: serverTimestamp() });
      transaction.update(proposalRef, { status: 'accepted', updatedAt: serverTimestamp() });
      transaction.update(profileRef, {
        openTasks: 0, updatedAt: serverTimestamp(),
        lastMutation: { kind: 'task', operation: 'close', targetId: 'accept-without-chat', at: serverTimestamp() },
      });
      // Deliberately omit marketConversations/{id}; acceptance must be denied by Rules.
    }));
  });

  it('denies report eleven inside the same rolling 24-hour window', async () => {
    await seedProfile('alice');
    const reportStart = Timestamp.fromMillis(Date.now() - 60 * 60_000);
    await seedProfile('bob', { reportWindowCount: 10, reportWindowStartedAt: reportStart });
    await seedTask('report-limit-task', 'alice');
    const db = user('bob'); const batch = writeBatch(db);
    batch.set(doc(db, 'reports', 'task_report-limit-task_bob'), {
      targetType: 'task', targetId: 'report-limit-task', reporterId: 'bob', reason: 'spam', createdAt: serverTimestamp(), status: 'open',
    });
    batch.update(doc(db, 'tasks', 'report-limit-task'), { reportCount: 1, updatedAt: serverTimestamp() });
    batch.update(doc(db, 'marketProfiles', 'bob'), {
      reportWindowCount: 11, reportWindowStartedAt: reportStart, updatedAt: serverTimestamp(),
      lastMutation: { kind: 'report', operation: 'create', targetType: 'task', targetId: 'report-limit-task', at: serverTimestamp() },
    });
    await assertFails(batch.commit());
  });

  it('allows an admin to pin a task but never gives a student that permission', async () => {
    await seed('admins', 'admin', {}); await seedProfile('alice'); await seedTask('pin-task', 'alice');
    await assertFails(updateDoc(doc(user('alice'), 'tasks', 'pin-task'), { pinned: true, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(user('admin'), 'tasks', 'pin-task'), { pinned: true, updatedAt: serverTimestamp() }));
    const incomplete = writeBatch(user('admin'));
    incomplete.update(doc(user('admin'), 'tasks', 'pin-task'), {
      pinned: true, updatedAt: serverTimestamp(),
      lastAuditLogId: 'pin-task-log-only', lastAdminNoticeId: 'pin-task-notice-missing',
    });
    incomplete.set(doc(user('admin'), 'adminLogs', 'pin-task-log-only'), {
      adminId: 'admin', action: 'moderate_task', targetType: 'task', targetId: 'pin-task',
      reason: 'Listing moderation test', note: '', createdAt: serverTimestamp(),
    });
    await assertFails(incomplete.commit());
    await assertSucceeds(adminListingMutationBatch({
      adminUid: 'admin', collectionName: 'tasks', listingId: 'pin-task', ownerUid: 'alice',
      listingPatch: { pinned: true, updatedAt: serverTimestamp() },
      logId: 'pin-task-log', noticeId: 'pin-task-notice',
    }).commit());
    await seedOffer('pin-offer', 'alice');
    await assertFails(updateDoc(doc(user('admin'), 'offers', 'pin-offer'), { pinned: true, updatedAt: serverTimestamp() }));
    await assertSucceeds(adminListingMutationBatch({
      adminUid: 'admin', collectionName: 'offers', listingId: 'pin-offer', ownerUid: 'alice',
      listingPatch: { pinned: true, updatedAt: serverTimestamp() },
      logId: 'pin-offer-log', noticeId: 'pin-offer-notice',
    }).commit());
    await assertFails(adminListingMutationBatch({
      adminUid: 'admin', collectionName: 'offers', listingId: 'pin-offer', ownerUid: 'alice',
      listingPatch: { pinned: false, updatedAt: serverTimestamp() },
      logId: 'wrong-offer-log', noticeId: 'wrong-offer-notice',
      logTargetType: 'task',
    }).commit());
  });

  it('allows admins to update report status while reporters cannot overwrite their own reports', async () => {
    await seed('admins', 'admin', {});
    await seedProfile('bob');
    await seed('reports', 'task_some-task_bob', { targetType: 'task', targetId: 'some-task', reporterId: 'bob', reason: 'spam', createdAt: Timestamp.now(), status: 'open' });
    await assertFails(updateDoc(doc(user('bob'), 'reports', 'task_some-task_bob'), { status: 'dismissed' }));
    await assertSucceeds(updateDoc(doc(user('admin'), 'reports', 'task_some-task_bob'), { status: 'dismissed' }));
  });
});

describe('review policy regressions: task completion, profile prerequisite, and ratings', () => {
  it('allows the poster but denies the assigned helper from completing a task', async () => {
    await seedProfile('alice'); await seedProfile('bob');
    await seedTask('poster-completes', 'alice', { status: 'assigned', assignedTo: 'bob' });
    await seedTask('helper-cannot-complete', 'alice', { status: 'assigned', assignedTo: 'bob' });
    await assertSucceeds(updateDoc(doc(user('alice'), 'tasks', 'poster-completes'), { status: 'completed', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(user('bob'), 'tasks', 'helper-cannot-complete'), { status: 'completed', updatedAt: serverTimestamp() }));
  });

  it('denies student Market writes when profiles/{uid} does not exist', async () => {
    const carolDb = user('carol');
    await assertFails(setDoc(doc(carolDb, 'marketProfiles', 'carol'), profileSeed('carol')));
    await seed('marketProfiles', 'carol', profileSeed('carol'));
    await seedProfile('alice');
    await seedTask('profile-prerequisite-task', 'alice');

    const taskBatch = writeBatch(carolDb);
    taskBatch.set(doc(carolDb, 'tasks/no-u2u-profile-task'), taskSeed('no-u2u-profile-task', 'carol'));
    taskBatch.update(doc(carolDb, 'marketProfiles/carol'), {
      openTasks: 1, updatedAt: serverTimestamp(),
      lastMutation: { kind: 'task', operation: 'create', targetId: 'no-u2u-profile-task', at: serverTimestamp() },
    });
    await assertFails(taskBatch.commit());

    const offerBatch = writeBatch(carolDb);
    offerBatch.set(doc(carolDb, 'offers/no-u2u-profile-offer'), offerSeed('no-u2u-profile-offer', 'carol'));
    offerBatch.update(doc(carolDb, 'marketProfiles/carol'), {
      activeOffers: 1, updatedAt: serverTimestamp(),
      lastMutation: { kind: 'offer', operation: 'create', targetId: 'no-u2u-profile-offer', at: serverTimestamp() },
    });
    await assertFails(offerBatch.commit());

    const proposalBatch = writeBatch(carolDb);
    proposalBatch.set(doc(carolDb, 'tasks/profile-prerequisite-task/proposals/carol'), {
      proposerId: 'carol', message: 'I can help organise and explain these notes clearly.', price: 0, deliveryDays: 2,
      status: 'sent', proposerName: displayNameFor('carol'), proposerInitials: 'CA',
      createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    });
    proposalBatch.update(doc(carolDb, 'tasks/profile-prerequisite-task'), { proposalCount: 1, updatedAt: serverTimestamp() });
    proposalBatch.update(doc(carolDb, 'marketProfiles/carol'), {
      proposalWindowCount: 1, proposalWindowStartedAt: serverTimestamp(), updatedAt: serverTimestamp(),
      lastMutation: { kind: 'proposal', operation: 'create', targetId: 'profile-prerequisite-task', at: serverTimestamp() },
    });
    await assertFails(proposalBatch.commit());

    await seedTask('profile-chat', 'alice', { status: 'assigned', assignedTo: 'carol' });
    await seed('marketConversations', 'task_profile-chat_alice_carol', {
      participants: ['alice', 'carol'], kind: 'task', listingId: 'profile-chat', taskId: 'profile-chat',
      listingOwnerUid: 'alice', createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
    });
    await assertFails(setDoc(doc(carolDb, 'marketConversations/task_profile-chat_alice_carol/messages/m1'), {
      senderUid: 'carol', recipientUid: 'alice', senderName: 'Carol',
      text: 'Hello from a user without a U2U profile.', createdAt: serverTimestamp(),
    }));
  });

  it('keeps both participants able to rate a completed task with stats in each same batch', async () => {
    await seedProfile('alice'); await seedProfile('bob');
    await seedTask('rating-both', 'alice', { status: 'completed', assignedTo: 'bob' });
    const posterDb = user('alice');
    const posterBatch = writeBatch(posterDb);
    posterBatch.set(doc(posterDb, 'ratings/rating-both_alice'), {
      taskId: 'rating-both', fromUid: 'alice', toUid: 'bob', stars: 5, tags: ['helpful'],
      comment: 'Clear and useful.', createdAt: serverTimestamp(),
    });
    posterBatch.set(doc(posterDb, 'userStats/bob'), {
      ratingSum: 5, ratingCount: 1, completedCount: 1, updatedAt: serverTimestamp(),
      lastMutation: { kind: 'rating', operation: 'create', targetId: 'rating-both_alice', at: serverTimestamp() },
    });
    await assertSucceeds(posterBatch.commit());

    const helperDb = user('bob');
    const helperBatch = writeBatch(helperDb);
    helperBatch.set(doc(helperDb, 'ratings/rating-both_bob'), {
      taskId: 'rating-both', fromUid: 'bob', toUid: 'alice', stars: 4, tags: ['clear'],
      comment: 'Helpful collaboration.', createdAt: serverTimestamp(),
    });
    helperBatch.set(doc(helperDb, 'userStats/alice'), {
      ratingSum: 4, ratingCount: 1, completedCount: 1, updatedAt: serverTimestamp(),
      lastMutation: { kind: 'rating', operation: 'create', targetId: 'rating-both_bob', at: serverTimestamp() },
    });
    await assertSucceeds(helperBatch.commit());
  });
});

describe('admin operations do not require U2U profile documents', () => {
  it('admin without profiles/{uid} and marketProfiles/{uid} can hide/restore and pin/unpin task and offer listings with linked logs and notices', async () => {
    await seed('admins', 'admin', {});
    await seedProfile('alice', { openTasks: 1, activeOffers: 1 });
    await seedTask('admin-task', 'alice');
    await seedOffer('admin-offer', 'alice');

    await assertSucceeds(adminListingMutationBatch({
      adminUid: 'admin', collectionName: 'tasks', listingId: 'admin-task', ownerUid: 'alice',
      listingPatch: { moderation: 'hidden', updatedAt: serverTimestamp() },
      profilePatch: { openTasks: 0, updatedAt: serverTimestamp(),
        lastMutation: { kind: 'task', operation: 'moderate_deactivate', targetId: 'admin-task', at: serverTimestamp() } },
      logId: 'hide-task-log', noticeId: 'hide-task-notice', noticeType: 'removal', canAppeal: true,
    }).commit());

    await assertSucceeds(adminListingMutationBatch({
      adminUid: 'admin', collectionName: 'tasks', listingId: 'admin-task', ownerUid: 'alice',
      listingPatch: { moderation: 'ok', updatedAt: serverTimestamp() },
      profilePatch: { openTasks: 1, updatedAt: serverTimestamp(),
        lastMutation: { kind: 'task', operation: 'moderate_restore', targetId: 'admin-task', at: serverTimestamp() } },
      logId: 'restore-task-log', noticeId: 'restore-task-notice',
    }).commit());

    await assertSucceeds(adminListingMutationBatch({
      adminUid: 'admin', collectionName: 'tasks', listingId: 'admin-task', ownerUid: 'alice',
      listingPatch: { pinned: true, updatedAt: serverTimestamp() },
      logId: 'pin-task-admin-log', noticeId: 'pin-task-admin-notice',
    }).commit());
    await assertSucceeds(adminListingMutationBatch({
      adminUid: 'admin', collectionName: 'tasks', listingId: 'admin-task', ownerUid: 'alice',
      listingPatch: { pinned: false, updatedAt: serverTimestamp() },
      logId: 'unpin-task-admin-log', noticeId: 'unpin-task-admin-notice',
    }).commit());

    await assertSucceeds(adminListingMutationBatch({
      adminUid: 'admin', collectionName: 'offers', listingId: 'admin-offer', ownerUid: 'alice',
      listingPatch: { moderation: 'hidden', updatedAt: serverTimestamp() },
      profilePatch: { activeOffers: 0, updatedAt: serverTimestamp(),
        lastMutation: { kind: 'offer', operation: 'moderate_deactivate', targetId: 'admin-offer', at: serverTimestamp() } },
      logId: 'hide-offer-log', noticeId: 'hide-offer-notice', noticeType: 'removal', canAppeal: true,
    }).commit());

    await assertSucceeds(adminListingMutationBatch({
      adminUid: 'admin', collectionName: 'offers', listingId: 'admin-offer', ownerUid: 'alice',
      listingPatch: { moderation: 'ok', updatedAt: serverTimestamp() },
      profilePatch: { activeOffers: 1, updatedAt: serverTimestamp(),
        lastMutation: { kind: 'offer', operation: 'moderate_restore', targetId: 'admin-offer', at: serverTimestamp() } },
      logId: 'restore-offer-log', noticeId: 'restore-offer-notice',
    }).commit());

    await assertSucceeds(adminListingMutationBatch({
      adminUid: 'admin', collectionName: 'offers', listingId: 'admin-offer', ownerUid: 'alice',
      listingPatch: { pinned: true, updatedAt: serverTimestamp() },
      logId: 'pin-offer-admin-log', noticeId: 'pin-offer-admin-notice',
    }).commit());
    await assertSucceeds(adminListingMutationBatch({
      adminUid: 'admin', collectionName: 'offers', listingId: 'admin-offer', ownerUid: 'alice',
      listingPatch: { pinned: false, updatedAt: serverTimestamp() },
      logId: 'unpin-offer-admin-log', noticeId: 'unpin-offer-admin-notice',
    }).commit());
  });

  it('admin without profiles/{uid} can create notices and adminLogs, reply to support, and edit config/market', async () => {
    await seed('admins', 'admin', {});
    await seed('support', 'ticket-admin-profile-free', {
      userId: 'alice', type: 'bug', subject: 'Market contact form issue', body: 'The Market contact form does not open correctly.',
      status: 'open', replies: [], createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
    });
    await seed('config', 'market', {
      announcement: { text: '', active: false, until: null }, blockedWords: [],
      rankWeights: { newness: 60, rating: 30, match: 10 },
      limits: { openTasks: 3, proposalsPerDay: 10, proposalsPerTask: 15, activeOffers: 5, reportsToHide: 3 },
    });
    const db = user('admin');
    await assertSucceeds(setDoc(doc(db, 'users/alice/notices/admin-profile-free'), {
      type: 'warning', title: 'Market reminder', body: 'Please review the Market community guidelines.',
      read: false, createdAt: serverTimestamp(), canAppeal: true, appealed: false,
    }));
    await assertSucceeds(setDoc(doc(db, 'adminLogs/admin-profile-free'), {
      adminId: 'admin', action: 'moderate', targetType: 'task', targetId: 'admin-task',
      reason: 'Test action', note: '', createdAt: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(db, 'support/ticket-admin-profile-free'), {
      status: 'replied', replies: [{ from: 'admin', text: 'We have reviewed your report.', at: Timestamp.now() }], updatedAt: serverTimestamp(),
    }));
    await assertSucceeds(marketConfigAuditBatch('admin', 'config-admin-profile-free', {
      announcement: { text: 'Market maintenance note', active: true, until: null },
      blockedWords: ['blocked-word'], rankWeights: { newness: 40, rating: 40, match: 20 },
    }));
  });
});
