import fs from 'node:fs';
import assert from 'node:assert/strict';

const rules = fs.readFileSync('firestore.rules', 'utf8');
const base = fs.readFileSync('firestore.base.rules', 'utf8');
const indexes = JSON.parse(fs.readFileSync('firestore.indexes.json', 'utf8'));
const tests = fs.readFileSync('tests/firestore.rules.test.mjs', 'utf8');
const spec = fs.readFileSync('MARKET_FINAL_SPEC.md', 'utf8');
const indexMap = fs.readFileSync('INDEX_QUERY_MAP.md', 'utf8');
const checklist = fs.readFileSync('DEPLOY_CHECKLIST.md', 'utf8');
const workflow = fs.readFileSync('.github/workflows/firestore-rules-test.yml', 'utf8');

const checks = [
  ['Firestore Rules version 2', () => assert.match(rules, /rules_version\s*=\s*'2';/)],
  ['verified email is part of Market authorization', () => assert.match(rules, /function marketVerified\(\)[\s\S]{0,700}email_verified/)],
  ['legacy U2U chat helper is preserved', () => { assert.ok(base.includes('function activeChatConversationMember')); assert.ok(rules.includes('function activeChatConversationMember')); }],
  ['profile creation keeps the exact required key shape while allowing only safe defaults', () => {
    assert.match(rules, /function marketValidProfileCreate\(uid, data\)[\s\S]{0,700}keys\.size\(\) == 17/);
    assert.ok(rules.includes("keys.hasAll(['suspendedUntil', 'proposalWindowStartedAt', 'reportWindowStartedAt', 'lastMutation'])"));
  }],
  ['task create validates payload before linked profile reads', () => assert.match(rules, /allow create: if marketVerified\(\)[\s\S]{0,160}marketValidTaskCreate\(taskId, request\.resource\.data\)[\s\S]{0,160}marketUserCanPost\(request\.auth\.uid\)[\s\S]{0,160}marketValidTaskCreateLinkedState\(taskId, request\.resource\.data\)/)],
  ['offer create validates payload before linked profile reads', () => assert.match(rules, /allow create: if marketVerified\(\)[\s\S]{0,160}marketValidOfferCreate\(offerId, request\.resource\.data\)[\s\S]{0,160}marketUserCanPost\(request\.auth\.uid\)[\s\S]{0,160}marketValidOfferCreateLinkedState\(offerId, request\.resource\.data\)/)],
  ['assigned task completion is explicitly poster-only', () => assert.match(rules, /Completing an assigned task is poster-only[\s\S]{0,260}request\.auth\.uid == (?:resource\.data|before)\.posterId/)],
  ['assigned-helper completion override is absent', () => assert.doesNotMatch(rules, /The assigned helper may mark completion/)],
  ['poster completion allow and helper deny test exists', () => assert.ok(tests.includes('allows the poster but denies the assigned helper from completing a task'))],
  ['student Market writes without U2U profile are denied in tests', () => assert.ok(tests.includes('denies student Market writes when profiles/{uid} does not exist'))],
  ['admin operations without a U2U profile are tested', () => assert.ok(tests.includes('admin without profiles/{uid} and marketProfiles/{uid} can hide/restore'))],
  ['admin suspension batch deliberately omits admin U2U profile', () => assert.ok(tests.includes('admin without a U2U profile suspends/hides with notice and log in one access-limit batch'))],
  ['proposal documents require proposerId equal to caller/document id and preflight task state', () => assert.ok(rules.includes('proposerId == userId') && rules.includes('proposerId == request.auth.uid') && rules.includes('marketProposalCreatePreflight(taskId, userId, request.resource.data)') && rules.includes('marketValidProposalCreateLinkedState(taskId, data, taskBefore)'))],
  ['Applied collection-group reads are self-scoped', () => assert.match(rules, /match \/\{path=\*\*\}\/proposals\/\{proposalId\}[\s\S]{0,260}resource\.data\.proposerId == request\.auth\.uid/)],
  ['proposal collection-group composite index exists', () => assert.ok(indexes.indexes.some(i => i.collectionGroup === 'proposals' && i.queryScope === 'COLLECTION_GROUP' && i.fields?.some(f => f.fieldPath === 'proposerId' && f.order === 'ASCENDING') && i.fields?.some(f => f.fieldPath === 'updatedAt' && f.order === 'DESCENDING')))],
  ['deterministic report IDs and caller ownership are validated', () => assert.match(rules, /function marketValidReportCreate\(reportId, data\)[\s\S]{0,1200}reporterId == uid[\s\S]{0,300}reportId == targetType \+ '_'/)],
  ['deterministic appeal ticket IDs are validated', () => assert.ok(rules.includes("'appeal_' + request.resource.data.relatedId + '_' + request.auth.uid"))],
  ['rating writes validate paired stats mutations', () => assert.ok(rules.includes("lastMutation.kind == 'rating'"))],
  ['both participants can rate a completed task in the Emulator suite', () => assert.ok(tests.includes('keeps both participants able to rate a completed task with stats in each same batch'))],
  ['Market conversation IDs encode sorted participants', () => assert.ok(rules.includes("conversationId == data.kind + '_' + data.listingId + '_' + data.participants[0] + '_' + data.participants[1]"))],
  ['Market conversations exclude admins', () => assert.ok(rules.includes('&& !marketIsAdmin()'))],
  ['Market message senderName is bound to U2U profile displayName', () => assert.ok(rules.includes('request.resource.data.senderName == get(/databases/$(database)/documents/profiles/$(request.auth.uid)).data.displayName'))],
  ['task create access-call regression is present', () => assert.ok(tests.includes('task-create batch stays under Firestore access-call limits'))],
  ['proposal create access-call regression is present', () => assert.ok(tests.includes('proposal-create batch stays under Firestore access-call limits'))],
  ['accept transaction access-call regression is present', () => assert.ok(tests.includes('accept transaction plus same-transaction task chat stays under Firestore access-call limits'))],
  ['report batch access-call regression is present', () => assert.ok(tests.includes('report batch stays under Firestore access-call limits'))],
  ['admin suspend/hide batch access-call regression is present', () => assert.ok(tests.includes('admin without a U2U profile suspends/hides with notice and log in one access-limit batch'))],
  ['Market limits are read-only in the app spec', () => assert.match(spec, /limits[\s\S]{0,250}read.only/i)],
  ['rank weights have non-negative rule checks', () => assert.match(rules, /rankWeights\.newness is number && request\.resource\.data\.rankWeights\.newness >= 0[\s\S]*rankWeights\.rating is number && request\.resource\.data\.rankWeights\.rating >= 0[\s\S]*rankWeights\.match is number && request\.resource\.data\.rankWeights\.match >= 0/)],
  ['index map lists proposerId collection-group scope', () => assert.ok(indexMap.includes('proposerId') && indexMap.includes('COLLECTION_GROUP'))],
  ['deploy checklist mandates production export and rollback', () => assert.ok(checklist.toLowerCase().includes('production') && checklist.toLowerCase().includes('rollback'))],
  ['workflow fails when Emulator output has expression-ceiling diagnostics', () => assert.match(workflow, /grep -Fq "Unable to evaluate the expression as the maximum of 1000 expressions to evaluate has been reached"/)],
  ['Market update rules guard null resource before update validators', () => {
    assert.ok(rules.includes('allow update: if resource != null && marketSignedIn() && marketProfileUpdateAllowed(uid);'));
    assert.ok(rules.includes('allow update: if resource != null && marketVerified() && marketTaskUpdateAllowed(taskId);'));
    assert.ok(rules.includes('allow update: if resource != null && marketVerified() && marketOfferUpdateAllowed(offerId);'));
  }],
  ['task and offer report counters require a report document before linked reads', () => {
    assert.ok(rules.includes('marketTaskReportExistsAfter(taskId)'));
    assert.ok(rules.includes('marketOfferReportExistsAfter(offerId)'));
  }],
  ['workflow specifies Node.js 20 and Java 21', () => { assert.match(workflow, /node-version:\s*['"]?20/); assert.match(workflow, /java-version:\s*['"]?21/); }],
];
let passed = 0;
let failed = 0;
for (const [name, run] of checks) {
  try { run(); passed++; console.log('PASS ' + name); }
  catch (error) { failed++; console.error('FAIL ' + name + ': ' + error.message); }
}
console.log('\n' + passed + ' passed, ' + failed + ' failed');
if (failed) process.exitCode = 1;
