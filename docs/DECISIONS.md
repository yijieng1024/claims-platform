# Key decisions

The decisions that shape the system, each with why and what it costs. The full list of resolved
questions (Q1–Q31) is in [DESIGN.md §14](DESIGN.md#14-decisions-resolved-open-questions).

---

### 1. Three services: auth, claims, reporting
- **Decision:** *auth* owns accounts and token signing; *claims* owns policies, claims, the state
  machine, information requests and documents; *reporting* owns the dashboard read model and notifications.
- **Why:** they change for different reasons and fail independently. Dashboards being slow or down
  must never stop an officer from working on a claim.
- **Trade-off:** more moving parts than one application (three deployments, a message broker, shared
  libraries for security and the event contract). For this scope a modular monolith would also have
  worked; three services show the boundaries and the asynchronous path explicitly.

### 2. REST for what users wait on, Kafka for reporting
- **Decision:** browser → services, officer actions and auth → claims (registration check) are
  synchronous REST. claims → reporting is asynchronous through Kafka.
- **Why:** an officer must learn immediately if an action was refused ("already picked up", "invalid
  status"). Reporting answers nobody, so it can lag a few seconds and catch up after an outage.
- **Trade-off:** dashboards are eventually consistent. The UI shows "Includes changes up to …" (the
  time of the newest change included, not the processing time), and re-reads the claim from
  claims-service after every action so the officer always sees the real state.

### 3. Transactional outbox + idempotent consumer
- **Decision:** claims-service writes the change, its history row and the event to an outbox table in
  one transaction; a publisher sends pending rows to Kafka (key = claim id). reporting stores processed
  event ids and applies a snapshot only if its claim version is newer.
- **Why:** no change without an event and no event without a change, even if Kafka is down at the
  moment of the change. Kafka delivers at least once, so duplicates and reordering must be harmless.
- **Trade-off:** an extra table and a polling publisher (about a second of delay). Notifications are
  created per event regardless of version, so none are lost when events arrive out of order (Q22).
  Events that can't be processed go to a dead-letter topic.

### 4. One database, one schema and one user per service
- **Decision:** a single PostgreSQL instance; each service has its own schema owned by its own
  database user without access to the other schemas. Flyway migrations per service.
- **Why:** the isolation of "database per service" (no cross-service joins, no shared tables) without
  running three databases locally.
- **Trade-off:** one instance is a shared failure point and shares resources; splitting into separate
  databases later is a configuration change, not a code change.

### 5. RS256 tokens, JWKS, and a self-signed service token
- **Decision:** auth-service signs JWTs (RS256) and publishes the public key at
  `/.well-known/jwks.json`. claims and reporting verify tokens locally, fetching the key on first use.
  `iss` is checked as a fixed string. For the registration check, auth-service signs a one-minute token
  with role `SERVICE`, accepted only on `/internal/**`.
- **Why:** with a shared secret (HS256) every service that can verify a token could also forge one.
  With RS256 only auth-service holds the private key, and it isn't called on every request.
- **Trade-off:** key rotation is manual (an unknown key id triggers a re-fetch, but there is no
  rotation schedule). If auth-service is unreachable before a service has the key, requests get 503.

### 6. Only policyholders can register; Policy is a real entity
- **Decision:** registration needs a policy number whose holder email matches the registering email;
  claims are opened against one of the claimant's policies, which supplies type, market, currency and
  sum insured, and must cover the incident date.
- **Why:** a non-customer registering makes no sense, and a claim needs something to claim against.
  Wrong number, wrong email and cancelled policy give the same error, so nobody can probe which
  policy numbers exist.
- **Trade-off:** policies are seeded reference data; a real system would integrate with a policy
  administration system.

### 7. Exposure per currency, reserved and not-yet-reserved kept apart
- **Decision:** the manager dashboard shows one card per currency (all six, zeros included):
  reserved exposure = sum of reserves of open claims, plus the count and claimed total of open claims
  without a reserve. No currency conversion.
- **Why:** adding MYR to SGD is meaningless without FX rates, and live rates add an external
  dependency. Claimed amounts are the claimant's number and often high; mixing them into exposure
  would make it unreliable.
- **Trade-off:** no single company-wide total. A converted total with a dated rate is future work.

### 8. Permission before state: 404, then 403, then 409
- **Decision:** for every claim action: not your market (or not your claim) → 404; not the assigned
  officer → 403; only then the state machine → 409. Managers are read-only. The rules live in one
  `ClaimStateMachine` class with a test for every allowed and forbidden transition.
- **Why:** someone without rights shouldn't learn whether a claim exists or what status it has.
- **Trade-off:** less helpful errors for callers who guess wrong; acceptable for staff tools.

### 9. Personal data stays off Kafka
- **Decision:** event snapshots carry ids, statuses, amounts, dates and officer names, but not the
  incident description, location or claimant name.
- **Why:** data minimisation: reporting doesn't need them, and data in a log is hard to delete.
- **Trade-off:** reporting can't show those fields; it doesn't need to.

### 10. Frontend types generated from committed OpenAPI specs, with required fields
- **Decision:** each service's OpenAPI spec is exported to `frontend/openapi/*.json` (committed) and
  the TypeScript client is generated from it. Every response field is required unless the Java field
  is `@Nullable` (one rule in `libs/web-common`).
- **Why:** the backend owns the contract; if it changes, the diff is visible in review and the
  frontend build breaks instead of drifting. Required fields mean no `!` workarounds for values the
  backend always sends.
- **Trade-off:** regenerating needs the services running (export) and Java (generator); the generated
  code is committed, so building the frontend needs neither.

### 11. Angular Material, re-skinned to our own design system
- **Decision:** Angular Material components with a custom theme and tokens (`docs/FRONTEND-DESIGN.md`):
  navy brand, flat cards, glass only for floating elements, light mode only. Dashboards are cards and
  tables, no charts.
- **Why:** ready-made accessible tables, forms, dialogs and date pickers, so time goes into features;
  the stock Material look wasn't good enough.
- **Trade-off:** a few overrides need selectors into Material internals (kept in one file), and some
  details follow Material's limits (e.g. 48px fields instead of 46px).

### 12. One-command start with Docker Compose
- **Decision:** `docker compose up --build` builds every image (multi-stage, Maven and Node inside
  Docker) and starts infrastructure, services and an nginx frontend that proxies `/api` by path.
- **Why:** a reviewer needs nothing but Docker. nginx keeps one origin, as the dev proxy does, so
  there is no CORS configuration anywhere.
- **Trade-off:** the first build downloads all dependencies and takes several minutes.

### 13. One claim per policy and incident date (Q30)
- **Decision:** a policy can't have two claims with the same incident date unless the earlier one was
  `REJECTED` (a rejected claim may be re-submitted). The second submission gets a 409 naming the
  existing claim, and the form links to it. A partial unique index on
  `claims (policy_id, incident_date) WHERE status <> 'REJECTED'` backs up the check, so two
  simultaneous submissions still produce one claim; the loser gets the same 409, never a 500.
- **Why:** a second claim for the same incident (even after the first was settled) is a common
  duplicate-payment and fraud pattern, and the database is the only place a race can be stopped.
- **Trade-off:** two genuine, separate incidents on the same day on one policy are blocked; the
  claimant would have to contact the insurer. Matching on date alone also misses a duplicate entered
  with a slightly different date; that belongs to fraud rules (see next steps).

### 14. Sum insured: per policy for property, per claim for motor (Q31)
- **Decision:** for **PROPERTY** policies the sum insured is shared by all claims on the policy:
  reserve and settlement must fit the *remaining cover* (sum insured minus the policy's other
  `SETTLED` claims; other open claims' reserves are not subtracted). For **MOTOR** policies each
  claim may be reserved and settled up to the full sum insured, whatever earlier claims paid. Assess
  and settle lock the policy row (`SELECT … FOR UPDATE`) for both types while checking, so two officers
  settling two claims on one policy at once can't exceed the cover together. The claim detail shows
  the remaining cover (for motor, the full sum insured).
- **Why:** without a per-policy limit, several property claims could together pay more than the
  policy's maximum. In motor insurance (e.g. in Malaysia) repeat claims are mainly penalised through
  the No Claim Discount at renewal rather than by reducing the sum insured, and NCD is out of scope.
- **Trade-off:** one aggregate per policy is simpler than real products, which may have per-section
  limits (building vs contents), sub-limits and reinstatement of cover. Not subtracting open reserves
  means two open property claims can each be reserved up to the remaining cover; only settlement is
  hard-capped. The policy row lock serialises assess/settle on one policy, which is negligible at this
  scale.

---

## Known limitations

- **One outbox publisher instance.** Strict per-claim ordering relies on a single publisher; several
  instances would need partition-aware locking (Q23).
- **Orphan upload files.** A crash between writing a file to disk and committing its row leaves a file
  without a row; there is no cleanup job.
- **"Today" is the database date in UTC.** Policy cover checks can be off by a day around midnight in
  the markets' time zones (Q14).
- **Local-disk storage** for documents (a Docker volume); production would use object storage with
  virus scanning.
- **Token in sessionStorage.** It survives a refresh, but any script on the page could read it if there
  were an XSS bug. An httpOnly cookie set by the server is the production choice.
- **Polling, not push.** Notifications are re-read every 15 seconds; Server-Sent Events or WebSockets
  would replace this.
- **No refresh tokens or password reset.** Users log in again after an hour; no admin UI for staff
  accounts (they are seeded).
- **No rate limiting** on login and registration.
- **Policies are seeded reference data**, not integrated with a policy administration system.
- **No currency conversion**; exposure is per currency only.
- **One manager role covers all markets.** A single read-only manager sees all six markets; there are
  no per-market managers or approvers.
- **One claim per policy per incident date** (decision 13): two genuine incidents on the same day
  can't both be claimed online.

## What I'd do next

- Push notifications (Server-Sent Events) instead of polling.
- Object storage for documents, with virus scanning and an orphan-file cleanup job.
- Refresh tokens in httpOnly cookies, password reset, rate limiting on login/register.
- A CI pipeline: both test suites, the compose build and a smoke test of the demo flow.
- Distributed tracing and metrics across the three services and Kafka.

### Regulatory and domain extensions (closer to real claims operations)

In Malaysia, general insurance and general takaful claims handling is governed by Bank Negara
Malaysia's *Policy Document on Claims Settlement Practices* (PD CSP, issued July 2024), which sets
minimum standards including turnaround times for motor and non-motor claims. Steps towards it, and
towards how claims departments actually work:

- **SLA timers per claim type** (motor vs non-motor) with overdue alerts, since PD CSP sets separate
  turnaround times. Not built: needs the regulatory clock rules (when it starts, pauses while waiting
  for the claimant) modelled per market.
- **Authority matrix:** settlements above a limit need a manager's approval. Not built because today
  one read-only manager covers all six markets; doing it properly needs an approving manager per
  market, limits per currency (there is no currency conversion by design) and a new "pending approval"
  status in the state machine: a redesign, not an add-on.
- **Straight-through processing** for small, low-risk claims (auto-assess and settle). Not built: needs
  the risk rules below first, or it pays out without checks.
- **Motor betterment deduction** by vehicle age when new franchise parts replace old ones. Not built:
  needs the vehicle's age on the policy, which the seeded policies don't carry.
- **No Claim Discount (NCD) impact:** a settled motor claim affects the policyholder's NCD at renewal,
  which is how repeat motor claims are penalised (decision 14). Not built: renewals and premiums are
  outside this platform.
- **Reinstatement of property sum insured** after a claim (often for an additional premium), so a
  property policy's remaining cover can be restored. Not built: needs premium handling, which is
  outside this platform.
- **Fraud red flags** (e.g. very late notification, repeated claims, near-duplicate incident dates)
  routing claims to an investigation queue. Not built: needs a rules engine and an investigator role.
- **Registered loss adjuster assignment** for complex or high-value claims. Not built: needs an
  external party with its own access and a hand-off in the workflow.
- **Rejection letters** that cite the policy clause and the customer's complaint and appeal route. Not
  built: needs policy wordings and per-market letter templates.
