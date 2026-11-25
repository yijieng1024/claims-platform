# Brief 08 — Duplicate claims and per-policy cover limit, then re-run final checks

## Context
`docs/DESIGN.md` is now **v1.0** — read §5 (rules for opening a claim, and the sum insured rule) and Q30–Q31 in section 14.

Two business-rule gaps I found after the platform was built:
1. A claimant can open a **second claim for the same incident** on the same policy, even after the first was settled (duplicate claim — a common fraud pattern).
2. **Sum insured is only checked per claim.** Several claims on one policy could together pay out more than the policy's maximum.

After fixing them, re-run brief 07's final checks, because this changes code after the clean-start test.

Same rules: small meaningful commits, no real company branding, don't edit `AI_JOURNAL.md`, `docs/DESIGN.md`, `docs/FRONTEND-DESIGN.md` or `docs/ai-briefs/`, explain every new library, **report straight away**, don't silently change the design — ask. **Don't push, don't rewrite history.**

## Part A — No duplicate claims (Q30)
- Rule: a policy can't have two claims with the same `incident_date` unless the earlier claim is `REJECTED` (a rejected claim may be re-submitted).
- Service check on `POST /api/claims` → **409** ProblemDetail, e.g. "A claim for this incident already exists: CLM-2026-000012." The claimant owns the policy, so naming their own claim number is fine. Include the existing claim's id in the ProblemDetail (extra property) so the UI can link to it.
- **Database guarantee:** Flyway migration adding a **partial unique index** on `claims (policy_id, incident_date) WHERE status <> 'REJECTED'`. Translate a unique-violation from a race into the same 409 — never a 500.
  - Before adding the index, check my local data for existing duplicates (earlier manual tests may have created some). If the migration would fail on existing data, tell me what you found and propose how to handle it — don't silently delete claims.
- Frontend (new claim form): show the 409 message inline with a link to the existing claim.
- Tests:
  - second claim same policy + date while the first is open → 409; while the first is `SETTLED` → 409; after the first is `REJECTED` → 201
  - same policy, different date → 201; different policy, same date → 201
  - two simultaneous submissions → exactly one 201, one 409

## Part B — Sum insured is per policy (Q31)
- **Remaining cover** = policy `sum_insured` − total `settled_amount` of the policy's **other** `SETTLED` claims. Reserves of other open claims are not subtracted.
- Assess: `0 < reserve ≤ remaining cover`. Settle: `0 ≤ settled ≤ remaining cover`. Violations → 400 ProblemDetail stating the remaining cover with currency.
- Assess and settle **lock the policy row** (`SELECT … FOR UPDATE`) while computing and checking, so two officers settling two claims on the same policy at once can't exceed the total.
- Add `remainingCover` to the claim detail response (required field; regenerate the OpenAPI JSON and the frontend client).
- Frontend (staff claim detail): show "Remaining cover" next to "Sum insured"; the assess and settle dialogs check against remaining cover (client-side hint; backend still enforces) and show it in the hint text.
- Tests:
  - policy 85,000: claim A settled 60,000 → claim B reserve 30,000 → 400; 25,000 → OK; settle 25,000 → OK; then claim C reserve 1 → 400 (0 remaining)
  - concurrent settles on two claims of the same policy that together exceed the cover → one succeeds, the other 400
  - `remainingCover` in the detail response is correct before and after a settlement

## Part C — Docs

### C1. Rules docs
- README demo script: if it would now hit the duplicate rule (e.g. re-using the same policy and date in two demo steps), adjust it.
- `docs/DECISIONS.md`: add two entries — duplicate claims, per-policy cover — with why and trade-off (e.g. two genuine incidents on the same day would be blocked; per-policy aggregate is simpler than real products that may have per-section limits).

- `docs/DECISIONS.md` → **"What I'd do next"**: add a short **"Regulatory and domain extensions"** subsection. Context: Bank Negara Malaysia's *Policy Document on Claims Settlement Practices* (PD CSP, issued July 2024) sets minimum standards for handling general insurance and general takaful claims, including turnaround times for motor and non-motor claims. Describe these as next steps, **without quoting specific day counts, thresholds or amounts** (I haven't verified them against the source):
  - SLA timers per claim type (motor vs non-motor) with overdue alerts
  - **Authority matrix** — settlements above a limit need a manager's approval. Explain why it's not built: in the current design the manager is a single, read-only, all-markets role; an authority matrix needs one approving manager per market, limits per currency (no currency conversion by design), and a new "pending approval" status in the state machine — a redesign, not an add-on
  - Straight-through processing for small, low-risk claims
  - Motor betterment calculation by vehicle age (needs vehicle age on the policy)
  - Fraud red-flag rules (e.g. very late notification) routing claims to investigation
  - Rejection letters that cite the policy clause and the customer's complaint / appeal route

### C2. Domain next steps in `docs/DECISIONS.md`
In "What I'd do next", add a short **"Closer to real claims operations"** subsection. In Malaysia, general insurance claims handling is governed by Bank Negara Malaysia's *Policy Document on Claims Settlement Practices* (PD CSP, issued July 2024). Mention it by name, but **don't state specific day counts, percentages or money thresholds** — I couldn't verify them all. Bullets (one line each, what it is + why it isn't built):
- SLA tracking with overdue alerts, separate for motor and non-motor (PD CSP sets turnaround times)
- Authority matrix: settlements above a limit need approval. Not built because today **one manager covers all six markets**; doing it properly needs per-market approvers and an approval status — a redesign, not a feature
- Straight-through processing for small, low-risk claims
- Motor betterment deduction by vehicle age when new franchise parts are used
- Fraud red flags (e.g. late notification, repeated claims) routing claims to an investigation queue
- Registered loss adjuster assignment for complex or high-value claims

Also add **"One manager role covers all markets"** to Known limitations.

### C3. README — port clash
In Quick start (not only dev mode), say which host ports the stack uses (4200, 8081–8083, 5433, 9092) and that anything else using them must be stopped first.

## Part D — Re-run final checks (from brief 07, Part C)
1. `./mvnw clean verify` and `npm test` green; production frontend build clean.
2. Branding and secrets scans again (report every hit).
3. **Clean-start test again** from a fresh clone: `docker compose down -v`, `docker compose up --build`, run the README demo script through the UI, plus: try a duplicate claim (blocked) and try settling above remaining cover (blocked).

## Report back
- Test counts
- Commit list
- Whether existing local data had duplicates and what you proposed/did
- Clean-start result and anything a reviewer might trip over
