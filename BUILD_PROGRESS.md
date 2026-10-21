# U2U Market Step 1 Build Progress

**Branch:** `feature/market-step1`
**Base:** `review/market-rules` at `aab0795a1d67b0e8d99f52bf3e815abcd0aa85b3`
**Build status:** Not started. No UI or application code changed.
**Safety:** Do not modify `firestore.rules`, `tests/`, `firestore.indexes.json`, existing U2U chat/friend/Nearby logic, or existing view IDs. No merge or deployment.

## Baseline gate

- Verified rules baseline commit: `6249b60165cbe96d2aa2102e22d22ac99469c1c1`.
- Latest confirmation run #22 on restore commit `2a136f1cc8478a27b0ef101a7a3c0ec8bde86ad9`: static 37 passed / 0 failed; Emulator 65 passed / 0 failed; expression-ceiling diagnostics 0; overall PASS.
- At branch creation, protected files `firestore.rules`, `.github/workflows/firestore-rules-test.yml`, and `tests/static-validate.mjs` are byte-identical to the verified baseline; `firestore.indexes.json` also remains unchanged. `PROGRESS.md` contains review history only.

## Required sources read in order

1. `MARKET_SPEC_ADDENDUM_RULES_PHASE.md`
2. `firestore.rules`
3. `firestore.indexes.json`
4. `INDEX_QUERY_MAP.md`
5. `MARKET_FINAL_SPEC.md`

Addendum governs conflicts with `MARKET_FINAL_SPEC.md`; Firestore rules determine allowed data shapes and authorization. Query builders must preserve the required rule constraints and use the index map.

## Parts and checkpoints

- **Part 0 — gate and docs-only wording:** complete. Protected-file baseline gate passed; addendum wording fixes committed in `fa0a96e84bca50a48abdb7b8772fa12fa3ebf957`. Clarified task/offer counter lifecycle, verified-email vs self-service profile edits (including `blockedUids`), admin audit/notice responsibility, required task conversation batching, and current green CI verification. No build implementation, rules, tests, indexes, UI, or deployment changes.
- **Part 1 — typed models and converters:** not started.
- **Part 2 — MarketRepository:** not started.
- **Part 3 — query builders:** not started.
- **Part 4 — config and persistent 6-hour cache:** not started.
- **Part 5 — pure ranking scorer and unit tests:** not started.
- **Part 6 — Firestore error mapping:** not started.

Update this file after each completed part/commit with commit SHA, files changed, checks run and result, and the exact next step. Stop after each requested small step and wait for the user's “continue” before beginning the next build part.
