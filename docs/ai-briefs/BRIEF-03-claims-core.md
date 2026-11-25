# Brief 03 — Claims core: claims, state machine, history, information requests, outbox write

## Context
`docs/DESIGN.md` is now **v0.5** and committed — read it again. New since v0.4: Q12–Q16 (section 14).

Plan:
1. ~~Skeleton + infra + auth-service~~ ✅
2. ~~Docker check + shared module + policies + registration~~ ✅
3. **Claims core** ← this brief
4. Outbox publisher + reporting-service
5. Document upload (backend)
6. Angular frontend
7. README, DECISIONS.md, compose for all services, clean-start check

Same rules: small meaningful commits, no real company branding, don't edit `AI_JOURNAL.md`, comment new dependencies, **report straight away**, don't silently change the design — ask.

**Not in this brief:** publishing outbox events to Kafka (brief 4), document upload (brief 5). Information request responses are **text only** for now.

## Part A — Follow-ups from brief 02 (do first, one commit each)

1. **Account name from the policy (Q12).** Register body becomes `{ email, password, policyNumber }`. Use `holderName` from the verify response as the account name. Drop `name` from the request; if a client still sends it, ignore it. Update tests and README.
2. **Relative seed dates (Q13).** Make the seeded policy dates relative to the migration date (e.g. `CURRENT_DATE - INTERVAL '6 months'`), keeping the same mix: in force, expired, cancelled. **Don't edit an already-applied migration** — add a new migration that updates the dates. Update the README table to describe dates relatively.
3. Delete the temporary `GET /api/claims/whoami` endpoint (keep reporting's until brief 4).

## Part B — Claims core

### B1. Event contract (`libs/claims-events`)
- Envelope and event types exactly as design §7: `eventId`, `eventType`, `occurredAt`, `actor {id, name, role}`, `claim` (full snapshot incl. `version`, `policyNumber`, amounts, status, assigned officer, timestamps).
- Event types: `ClaimSubmitted`, `ClaimAssigned`, `InformationRequested`, `InformationProvided`, `ClaimAssessed`, `ClaimSettled`, `ClaimRejected`.
- Plain Java records + JSON (Jackson). No Kafka dependency in this module.

### B2. Database (claims schema, Flyway)
Tables from design §6.2: `claims`, `claim_status_history`, `information_requests`, `outbox_events`.
- `claim_number`: `CLM-<year>-<6-digit sequence>` from a Postgres sequence.
- `claims.version` for optimistic locking.
- Useful indexes: claims by `(market, status)`, by `claimant_id`, by `assigned_officer_id`; outbox by `(status, created_at)`.
- Partial unique index so a claim can have only **one `OPEN` information request**.

### B3. State machine
- One class, `ClaimStateMachine`, holding the allowed transitions from design §5 (including `ASSESSED → INFO_REQUESTED`).
- Invalid transition → **409** ProblemDetail that names the current status and the attempted action.
- Unit tests: **every** allowed transition and **every** forbidden one (table-driven / parameterised).

### B4. Writes — one transaction each
Every action below, in **one** DB transaction: update the claim → insert `claim_status_history` (if status changed) → insert `outbox_events` row (`PENDING`, payload = full event).

| Action | Endpoint | Who | Rules |
|---|---|---|---|
| Open claim | `POST /api/claims` `{ policyId, incidentDate, incidentLocation, description, claimedAmount }` | claimant | Policy belongs to caller (holder email = token email); not cancelled; incident date within cover and not in the future; claimed amount > 0. Type, market, currency copied from policy. → `SUBMITTED`, event `ClaimSubmitted` |
| Pick up | `POST /api/claims/{id}/assign` | officer | Claim in officer's market; status `SUBMITTED`. Assigns to self. → `UNDER_REVIEW`, `ClaimAssigned` |
| Request info | `POST /api/claims/{id}/information-requests` `{ question }` | assigned officer | From `UNDER_REVIEW` or `ASSESSED`. → `INFO_REQUESTED`, `InformationRequested` |
| Respond | `POST /api/claims/{id}/information-requests/{reqId}/response` `{ responseText }` | claim owner | Request must be `OPEN`. → request `RESPONDED`, claim `UNDER_REVIEW`, `InformationProvided` |
| Assess | `POST /api/claims/{id}/assessment` `{ reserveAmount, note }` | assigned officer | From `UNDER_REVIEW` (or `ASSESSED` to update reserve). 0 < reserve ≤ policy `sum_insured`. → `ASSESSED`, `ClaimAssessed` |
| Settle | `POST /api/claims/{id}/settlement` `{ settledAmount, note }` | assigned officer | From `ASSESSED`. 0 ≤ amount ≤ `sum_insured`. → `SETTLED`, `closed_at` set, `ClaimSettled` |
| Reject | `POST /api/claims/{id}/rejection` `{ reason }` | assigned officer | From `UNDER_REVIEW` or `ASSESSED`; reason required. → `REJECTED`, `closed_at` set, `ClaimRejected` |

- Assessing again while already `ASSESSED` (reserve update) writes history with a note but no status change; still writes a `ClaimAssessed` event (exposure changed).
- Two officers picking up the same claim at the same time → one wins, the other gets **409** (optimistic lock). Translate the JPA exception into ProblemDetail; don't leak a 500.
- Officer acting on a claim not assigned to them → **403**. Claimant acting on someone else's claim → **404** (don't reveal it exists).

### B5. Reads
| Endpoint | Who | Notes |
|---|---|---|
| `GET /api/claims/my` | claimant | own claims, newest first |
| `GET /api/claims/queue` | officer | `SUBMITTED`, unassigned, officer's market, oldest first |
| `GET /api/claims/assigned` | officer | my open claims |
| `GET /api/claims` | manager | all markets; filters `status`, `market`; paginated |
| `GET /api/claims/{id}` | owner / officers of that market / manager | 404 for anyone else |
| `GET /api/claims/{id}/history` | same | oldest first |
| `GET /api/claims/{id}/information-requests` | same | |

List endpoints return a summary DTO (no long description); detail returns everything incl. policy number, `sumInsured`, and the open information request if any.

### B6. Tests (Testcontainers)
- Full happy path: open → assign → request info → respond → assess → settle; history has the right rows in order.
- Opening a claim: someone else's policy → 404; cancelled policy → 400; incident date outside cover → 400; future date → 400.
- **Every write also writes exactly one outbox row** with the right `eventType` and claim `version`.
- **Atomicity:** if the outbox insert fails (force it in the test), the claim change and history are rolled back too.
- Concurrent assign → exactly one success, one 409.
- Officer from another market → 404 on detail, can't assign.
- Reserve > sum insured → 400; settle from `UNDER_REVIEW` → 409.

## Done when
- `./mvnw clean verify` green, 0 skipped.
- Manually via Swagger: log in as the seeded MY claimant, open a claim on their MY motor policy; log in as a MY officer, see it in the queue, pick it up, request info; back as claimant, respond; officer assesses and settles. `outbox_events` has one `PENDING` row per step.

## Report back
- Test count run / passed / skipped
- Commit list
- Any design changes or questions
- Manual test steps with the seeded accounts and policy to use
