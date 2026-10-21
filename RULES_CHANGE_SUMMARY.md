# Rules change summary

The U2U Market Firestore candidate is on `review/market-rules`. It has not been merged to `main` or deployed to Firebase.

Compared with `feature/pachattar-design-foundation-phase-1`, `firestore.rules` has **1,287 added lines and 0 deleted lines**. The prefix before the Market insertion and the suffix from `match /profiles/{uid}` onward are byte-identical; `activeChatConversationMember()` is also unchanged. The assigned-helper task-completion alternative is absent and the Market `assigned -> completed` rule explicitly checks the poster UID.

## Final CI

[GitHub Actions run 39533202614](https://github.com/Webster-02/Superior-Student/actions/runs/39533202614) succeeded on commit `d5490826a6ee55b5d5861382068fb9571e769227`:
- `npm ci`: exit 0.
- Static preflight: **31/31 passed**.
- Firestore Emulator: **65/65 passed**, zero failed or skipped.
- [Uploaded test artifact](https://github.com/Webster-02/Superior-Student/actions/runs/39533202614/artifacts/12315439964), SHA-256 `f459f2730e9bb09e3ae311859c33ce90b17a5477bc01135ef9fcf0429a635060`.

The latest raw Emulator log still contains **39 evaluator diagnostics** referencing the 1,000-expression ceiling, although the test suite finished successfully. These are distinct from document access-call-limit errors; review the affected rule expressions before production approval.

The original `MARKET_SPEC_ADDENDUM_RULES_PHASE.md` remains unavailable, so it has not been fabricated. The supplied explicit requirements have been added to the final spec, but addendum reconciliation remains open. See `TEST_RESULTS.md` and `DEPLOY_CHECKLIST.md` for caveats and the production gate.
