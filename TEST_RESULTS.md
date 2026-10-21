# Test and validation results

Generated from the commands actually run in this package workspace. No repository, GitHub workflow run, or Firebase deployment was performed.

## Results at a glance

| Check | Result | Evidence |
|---|---|---|
| Static package/rules preflight | **PASS — 23/23** | `artifacts/test-static-output.txt` and `TEST_RESULTS_STATIC_CHECK.txt` |
| JavaScript parse: Emulator test file | **PASS** | `artifacts/js-syntax-output.txt` |
| JavaScript parse: static validator | **PASS** | `artifacts/js-syntax-output.txt` |
| npm dependency install | **BLOCKED — exit 1** | `artifacts/npm-install-output.txt`; DNS `EAI_AGAIN` for `registry.npmjs.org` |
| Firestore Emulator suite | **NOT RUN — blocked, exit 127** | `artifacts/test-emulator-output.txt`; `firebase: not found` because dependencies could not install |
| Firebase Rules compilation | **NOT RUN** | Requires Firebase CLI / emulator toolchain |
| GitHub Actions workflow | **NOT RUN** | Workflow is included as a package file only; no repository changes were allowed/made |
| Deploy | **NOT performed** | No Firebase deploy command was run |

The Emulator status is not a rules/test failure result: the test process did not start because the Firebase CLI and rule-unit-test dependencies were unavailable. Static checks are not substitutes for compilation or actual rule evaluation. The access-call assertions below therefore remain unverified until the Emulator suite passes.

## Exact command output: static preflight

Command: `npm run test:static`

```text
U2U Market static preflight (not Firebase Rules Emulator tests)
PASS  firestore.indexes.json parses and declares exactly 46 composite indexes
PASS  every composite index is mapped to a query and the map documents mandatory list constraints
PASS  Offers feed composites use renewedAt, not createdAt
PASS  task/offers composite indexes do not add expiresAt inequality indexes
PASS  feed, owner, admin, report, support and chat query shapes have composite index coverage
PASS  rollback branch snapshot is byte-identical to the selected Git branch blob
PASS  Market profile is separate from existing users/{uid} profile fields
PASS  first Market profile creation requires verified U2U user and fixed safe defaults
PASS  posting requires verified email, U2U profile, and server-time suspension expiry
PASS  rolling window reset/increment uses request.time and exact count changes
PASS  task assignment and proposal acceptance are linked in one atomic state transition
PASS  owner expiry/removal lifecycle transitions require linked counter decrements
PASS  reportCount exact +1 requires the same-batch deterministic report
PASS  admin pin/moderation permissions are absent from non-admin owner update branch
PASS  Market conversations require participants, obey blocks, and exclude admins
PASS  pre-existing chat and Nearby rule blocks remain byte-identical to baseline
PASS  Applied proposals collection-group query is self-scoped and indexed
PASS  task/offer content edits reuse creation validation only when owner can post
PASS  Market conversation deterministic IDs and metadata mutation validation exist
PASS  requested emulator case categories are represented in the test suite
PASS  major spec collections each have matching Market rules
PASS  Market fragment delimiters are balanced
PASS  complete merged rules delimiters are balanced and function names are unique

23 passed, 0 failed
```

## Dependency install — real failure output

Command: `npm install --no-audit --no-fund --fetch-retries=0 --fetch-timeout=10000`

```text
npm error code EAI_AGAIN
npm error syscall getaddrinfo
npm error errno EAI_AGAIN
npm error request to https://registry.npmjs.org/@firebase%2frules-unit-testing failed, reason: getaddrinfo EAI_AGAIN registry.npmjs.org
```

Exit code: `1`. The full first-attempt console output is in `artifacts/npm-install-output.txt`. A second explicit retry failed identically; see `artifacts/npm-install-retry.txt`.

## Emulator suite — real local command output

Command: `npm run test:emulator`

```text
> test:emulator
> firebase emulators:exec --project u2u-market-rules-review --only firestore "node --test tests/firestore.rules.test.mjs"

sh: 1: firebase: not found
```

Exit code: `127`. The full output is in `artifacts/test-emulator-output.txt`. There are 60 Emulator test cases defined in `tests/firestore.rules.test.mjs`; none has a local pass/fail assertion result because the test runner did not launch.

## JavaScript parse checks

Commands:

```text
node --check tests/firestore.rules.test.mjs   EXIT_CODE=0
node --check tests/static-validate.mjs         EXIT_CODE=0
```

See `artifacts/js-syntax-output.txt`.

## High-cost access-call tests included but not yet verified

The Emulator suite contains explicit successful assertions for:

1. Task create plus profile counter mutation.
2. Proposal create plus task/profile count mutations.
3. Accept transaction updating the task and proposal, updating the poster counter, and creating the task conversation in the same transaction.
4. Report batch, deterministic report document and counters.
5. Admin suspend/hide batch covering a profile, two tasks, one offer, a suspension notice and an immutable admin log.

If these operations pass in the Firestore Emulator, that is evidence the tested requests stayed within the security-rules document-access limits; the Emulator test assertion does not print an exact per-request count. Because the suite could not run in this environment, **do not treat the access-call limits as confirmed yet**. Do not increase access-call limits; reduce rule reads if any test fails after a working Emulator run.

## GitHub Actions status

`.github/workflows/firestore-rules-test.yml` is included inside this package. It selects Node 20 and Java 21, captures npm install and test logs, runs `npm run test:static` and `npm run test:emulator`, and uploads `artifacts/**` even on failure. It was not executed by GitHub because the file has not been added to a repository or branch; this package-only workflow choice avoids repository changes. When copied to an approved non-main review branch, use the resulting uploaded artifacts as the CI record and require both suites to pass before approval.


## First real GitHub Actions run (commit 9023d4a2377e89486a5b2f1b85ee3d3d9ae5e22a)

Workflow: [Firestore Rules Review Tests, run 39531687165](https://github.com/Webster-02/Superior-Student/actions/runs/39531687165)

- npm install: exit 0
- `npm run test:static`: exit 0 (29 checks)
- `npm run test:emulator`: exit 1
- Emulator summary: 65 tests, 56 passed, 9 failed, 0 skipped.
- Artifacts: [firestore-rules-test-results.zip](https://github.com/Webster-02/Superior-Student/actions/runs/39531687165/artifacts/12315966279), SHA-256 `59d4fef2f0eec5b342caea66c0f83c2fc5f1893c8f5e2b782efc1da12451c318`.

Failures found and fixes prepared:
1. Two owner-denial test fixtures attempted to write values that already existed; fixtures now request actual changes.
2. The Nearby fixture supplied `senderPublicKey: 'key'`, below the baseline rule's minimum length; it now supplies a 64-character public key.
3. Rules evaluation exhausted the maximum-expression budget on task/profile counter and Market conversation paths, and unassigned tasks triggered an unsafe read of the optional `assignedTo` field. Candidate fixes split the counter mutation dispatcher by mutation kind, cache repeated profile/counter reads, remove redundant create-time revalidation already covered by content validators, use null-safe `assignedTo` comparisons, and avoid repeating whole immutable-parent validation on each Market conversation update.

**The second run is pending. Do not treat the Emulator suite or document access-call limits as verified until the subsequent run demonstrates it.**


## Second real GitHub Actions run — PASS

Workflow: [Firestore Rules Review Tests, run 39532613999](https://github.com/Webster-02/Superior-Student/actions/runs/39532613999)  
Commit: `4d85fe3874b9a989df534923b9968a8744a7680f`

| Check | Real result |
|---|---|
| Dependency install | exit 0 |
| `npm run test:static` | exit 0 — 31 passed, 0 failed |
| `npm run test:emulator` | exit 0 — 65 passed, 0 failed, 0 skipped |
| Workflow job | success |

The test run took `12048.769181 ms` for the Node test runner. The Firestore Emulator accepted the current rules and the tests exercised the task-create, proposal-create, accept transaction with same-transaction chat, report batch, and admin suspend/hide + notice/log operations. Since the Emulator enforced the Rules during these operations and all corresponding assertions passed, the suite did not report a permission denial or access-call-limit violation for those tested flows.

Artifacts: [firestore-rules-test-results.zip](https://github.com/Webster-02/Superior-Student/actions/runs/39532613999/artifacts/12316171000)  
Artifact SHA-256: `8abd993c8462fb90ca9311aee14b66fbb1fd26a051950a7d7367a676e143467e`.

This run installed with `npm install` because the review commit did not yet include a lockfile. The lockfile generated by that successful run is now being committed, and the next workflow run should exercise `npm ci`.
