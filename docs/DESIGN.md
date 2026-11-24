# Claims Platform — Architecture & Data Model (v0.7)

Status: **v0.7 - decisions agreed** (see section 14).

---

## 1. What we are building

One Angular app used by three roles:

| Role | Can do |
|---|---|
| **Claimant** (existing policyholder) | Register (only with a valid policy number + matching email) / log in, see their policies, report an incident against one of their policies (open a claim), track status and history, answer information requests (text + file upload), receive decisions as in-app notifications |
| **Officer** (claims staff) | See the queue of unassigned claims, pick up a claim, request more information, assess (set reserve), settle or reject, see team workload and performance |
| **Manager** | Everything read-only an officer sees, plus outstanding claims overview and total liability exposure |

---

## 2. Services

Three Spring Boot services in one Maven multi-module repo, plus the Angular frontend.

| Service | Owns | Why it is separate |
|---|---|---|
| **auth-service** | Users, passwords, login, issuing JWTs | Both other services need to know "who is this and what role". One place issues identity; everyone else only verifies it. |
| **claims-service** | Policies (simulated reference data), claims, status history, information requests, documents, outbox | This is where the business work happens. All writes to a claim go through here, so all claim rules live in one place. |
| **reporting-service** | Read model for dashboards, notifications | Dashboards and notifications are *side effects* of claim changes. They can be a few seconds behind without hurting anyone, and they should never slow down or break a claimant submitting a claim. |

**No API gateway.** In local dev, the Angular dev server proxies `/api/auth/**`, `/api/claims/**`, `/api/reports/**`, `/api/notifications/**` to the right service. This avoids CORS and avoids running one more service that adds nothing to the assessment. In production a gateway would sit here.

### Database

One PostgreSQL instance, **one schema per service** (`auth`, `claims`, `reporting`). Each service only connects to its own schema. No cross-schema joins or foreign keys.

Why: it gives a real data boundary (reporting can only learn about claims through events) while still being one container to run locally. Splitting into separate database instances later is a config change, not a code change.

Migrations: Flyway, one migration folder per service.

---

## 3. Sync vs async — what uses REST and what uses Kafka

| Interaction | Mechanism | Why |
|---|---|---|
| Frontend → any service | REST | The user is waiting for an answer ("was my claim created?", "was my login correct?"). |
| Officer changes a claim | REST to claims-service | The officer must know immediately if the action was rejected (e.g. invalid status change, someone else already picked it up). |
| claims-service → reporting-service | **Kafka** (via outbox) | Reporting does not need to answer the officer. If reporting is down, claims must still work; it catches up when it comes back. |
| claims / reporting → auth-service | REST, **on first need** (fetch public key, cached; re-fetched if an unknown key id appears) | Token verification happens locally in each service, so auth-service being slow does not slow every request, and claims/reporting can start before auth-service. |
| auth-service → claims-service (register) | REST, internal endpoint | Registration must know **immediately** whether the policy number + email are real. |

### How the "async gap" is handled in the UI

- After any action, the UI re-reads the claim from **claims-service** → always up to date.
- Dashboards come from **reporting-service** → may be a few seconds behind. Each dashboard response includes `asOf` = the latest `occurredAt` of the events reflected in the numbers (when the newest included claim change happened, not when reporting processed it), and the UI shows "Includes changes up to …". If reporting falls behind, this time stays old instead of falsely looking fresh.
- Notifications: the UI polls `GET /api/notifications` every ~15 seconds. (Server-Sent Events / WebSocket would be the upgrade; out of scope.)

---

## 4. Authentication

- auth-service signs JWTs with an **RSA private key (RS256)**. It exposes the matching public key at `GET /.well-known/jwks.json`.
- claims-service and reporting-service use Spring Security's **OAuth2 Resource Server** support, pointed at that JWKS URL. They can verify tokens but cannot create them.
  - Why RS256 instead of a shared secret (HS256): with a shared secret, every service that can verify a token can also forge one. With RS256, only auth-service holds the private key.
- Token claims: `sub` (user id), `role`, `name`, `email`, `market` (officers only — the claim is absent for other roles), `iss` (`http://localhost:8081`, checked by the other services). Expiry: 1 hour.
- If a token can't be checked because auth-service is unreachable (key not yet fetched), services return **503** as ProblemDetail (not 500): the service isn't broken, a dependency is unavailable.
- Passwords stored as BCrypt hashes.
- **Claimants can only register if they are an existing policyholder**: the register request includes a `policyNumber`; auth-service asks claims-service whether that policy exists, is not cancelled, and its holder email matches the registering email. Wrong policy number and wrong email return the **same** error, so nobody can probe which policy numbers exist. One account per email; an account can see every policy whose holder email matches.
- auth-service calls the internal verify endpoint with a **short-lived service token** it signs itself (role `SERVICE`, 1 minute). claims-service accepts `ROLE_SERVICE` only on `/internal/**`. No extra shared secret is needed.
- Officers and managers are **seeded** by a Flyway migration (in a real company staff accounts are created by an admin, not self-registered).
- No refresh token (documented as out of scope; user logs in again after 1 hour).

---

## 5. Claim lifecycle (state machine)

```
                 pick up              request info
SUBMITTED ──────────────► UNDER_REVIEW ──────────────► INFO_REQUESTED
                           │      ▲                      ▲     │
                           │      └── claimant responds ─┼─────┘
                           │                             │
                           │ assess (set reserve)        │ request info
                           ▼                             │
                        ASSESSED ────────────────────────┘
                           │
                           ├──── settle ─────► SETTLED   (closed)
                           │
   reject (from UNDER_REVIEW or ASSESSED) ───► REJECTED  (closed)
```

Rules for opening a claim:
- The claimant picks one of **their own** policies (holder email = their email).
- The policy must not be cancelled, and the **incident date must fall between the policy start and end date**.
- The incident date cannot be in the future.
- Claim `type`, `market` and `currency` are **copied from the policy**, not chosen by the claimant.

Rules after that:
- Only the **assigned officer** can act on a claim after pick-up.
- **Check order: permission first, then business rules.** Not your market → 404; not the assigned officer → 403; only then the state machine (409). Someone without rights gets no feedback about the claim's state. Managers are read-only.
- Only the **claim owner** can respond to an information request.
- `ASSESSED → INFO_REQUESTED` is allowed: if the officer needs more information after assessing, they request it directly. When the claimant responds the claim goes back to `UNDER_REVIEW` and the officer assesses again (reserve can be updated). This avoids a separate "reopen" action.
- `SETTLED` and `REJECTED` are terminal.
- `reserve_amount` and `settled_amount` cannot exceed the policy's `sum_insured` (the maximum payable).
- Officers can only see and act on claims in **their own market** (queue, assigned list and claim detail). Managers see all markets.
- Every transition writes a row to `claim_status_history` and an event to the outbox, **in the same transaction**.
- The transition rules live in one class (`ClaimStateMachine`) with unit tests for every allowed and every forbidden transition.
- Invalid transition → HTTP **409 Conflict**.
- Two officers picking up the same claim at once → optimistic locking (`version` column) → the second one gets **409**.

---

## 6. Data model

### 6.1 `auth` schema

**users**
| Column | Type | Notes |
|---|---|---|
| id | uuid PK | |
| email | varchar unique | login name |
| password_hash | varchar | BCrypt |
| full_name | varchar | |
| role | varchar | `CLAIMANT` / `OFFICER` / `MANAGER` |
| market | varchar null | officers only, e.g. `MY` (managers see all markets; see Q1) |
| created_at | timestamptz | |

### 6.2 `claims` schema

**policies** — simulated reference data. In a real company this comes from the policy administration system; here it is seeded by Flyway and read-only.
| Column | Type | Notes |
|---|---|---|
| id | uuid PK | |
| policy_number | varchar unique | e.g. `POL-MY-MTR-0001` |
| holder_name | varchar | |
| holder_email | varchar | lowercase; used for registration check and to find "my policies" |
| product_type | varchar | `MOTOR` / `PROPERTY` |
| market | varchar | |
| currency | varchar(3) | |
| insured_item | varchar | e.g. vehicle registration or property address (fake) |
| sum_insured | numeric(15,2) | maximum payable |
| start_date / end_date | date | cover period |
| status | varchar | `ACTIVE` / `CANCELLED` (an expired policy is just one whose `end_date` has passed) |

Seed dates are **relative to the day the database is first migrated** (e.g. start = today − 6 months), so the demo data does not expire. "Today" is the database date in UTC, not each market's local date — a known limitation (off by one day only around midnight).


**claims**
| Column | Type | Notes |
|---|---|---|
| id | uuid PK | |
| claim_number | varchar unique | human-readable, e.g. `CLM-2026-000123` |
| claimant_id | uuid | user id from the JWT (no FK — different schema) |
| claimant_name | varchar | copied at creation so we don't call auth-service to display it |
| policy_id | uuid FK → policies | |
| policy_number | varchar | copied for display |
| type | varchar | `MOTOR` / `PROPERTY` — copied from policy |
| market | varchar | one of `MY, SG, TH, ID, PH, VN` — copied from policy |
| currency | varchar(3) | copied from policy, e.g. `MYR` |
| incident_date | date | |
| incident_location | varchar | |
| description | text | |
| claimed_amount | numeric(15,2) | what the claimant says it costs |
| status | varchar | see state machine |
| assigned_officer_id | uuid null | |
| assigned_officer_name | varchar null | |
| assigned_at | timestamptz null | when an officer picked it up; part of the event snapshot |
| reserve_amount | numeric(15,2) null | officer's estimate of what will be paid — drives exposure |
| settled_amount | numeric(15,2) null | final paid amount |
| rejection_reason | text null | |
| created_at / updated_at / closed_at | timestamptz | |
| version | bigint | optimistic locking |

**claim_status_history**
| Column | Type |
|---|---|
| id | uuid PK |
| claim_id | uuid FK → claims |
| from_status | varchar null |
| to_status | varchar |
| changed_by_id / changed_by_name / changed_by_role | |
| note | text null |
| changed_at | timestamptz |

**information_requests**
| Column | Type | Notes |
|---|---|---|
| id | uuid PK | |
| claim_id | uuid FK | |
| requested_by_id | uuid | officer |
| question | text | |
| status | varchar | `OPEN` / `RESPONDED` |
| response_text | text null | |
| created_at / responded_at | timestamptz | |

Only one `OPEN` request per claim at a time (simpler UI, matches the `INFO_REQUESTED` status).

**claim_documents**
| Column | Type | Notes |
|---|---|---|
| id | uuid PK | |
| claim_id | uuid FK | |
| information_request_id | uuid null FK | set when uploaded as part of a response |
| uploaded_by_id | uuid | |
| original_filename | varchar | shown to users only |
| stored_filename | varchar | random UUID — the original name is never used as a path (prevents path traversal) |
| content_type | varchar | |
| size_bytes | bigint | |
| uploaded_at | timestamptz | |

**outbox_events**
| Column | Type | Notes |
|---|---|---|
| id | uuid PK | the event id; consumers use it to detect duplicates |
| aggregate_id | uuid | claim id; also the Kafka message key |
| event_type | varchar | e.g. `ClaimAssessed` |
| payload | jsonb | full event body |
| status | varchar | `PENDING` / `SENT` |
| attempts | int | |
| created_at / sent_at | timestamptz | |

### 6.3 `reporting` schema

**claim_summary** — one row per claim, kept up to date from events
| Column | Notes |
|---|---|
| claim_id PK | |
| claim_number, type, market, currency, status | |
| claimant_id | for notifications |
| assigned_officer_id, assigned_officer_name | |
| claimed_amount, reserve_amount, settled_amount | |
| submitted_at, assigned_at, closed_at | |
| claim_version | last applied claim version — older events don't overwrite the summary |
| last_event_at | `occurredAt` of the event that last updated this row (used for `asOf`) |
| updated_at | |

**notifications**
| Column | Notes |
|---|---|
| id PK, user_id, claim_id, claim_number | |
| type | e.g. `INFO_REQUESTED`, `CLAIM_SETTLED` |
| message | |
| created_at, read_at null | |

**processed_events**
| Column | Notes |
|---|---|
| event_id PK | if it exists, the event was already handled → skip |
| processed_at | |

Dashboards are **plain SQL queries over `claim_summary`**, not pre-computed counters.
Why: counters like "officer X has 5 open claims" go wrong if an event is processed twice or out of order. A summary row per claim, overwritten with the latest snapshot, cannot drift. At this data size the queries are cheap.

---

## 7. Kafka events

**One topic: `claims.events`**, 3 partitions, **message key = claim id**.

Why one topic with claim id as key: Kafka only guarantees order inside one partition. Same key → same partition → all events for one claim arrive in order. Separate topics per event type would lose that ordering.

### Event types
| Event | Produced when | Reporting does |
|---|---|---|
| `ClaimSubmitted` | claimant opens a claim | insert summary row |
| `ClaimAssigned` | officer picks up | update summary; notify claimant |
| `InformationRequested` | officer asks for info | update summary; notify claimant |
| `InformationProvided` | claimant responds | update summary; notify officer |
| `ClaimAssessed` | reserve set/changed | update summary (exposure changes) |
| `ClaimSettled` | settled | update summary; notify claimant |
| `ClaimRejected` | rejected | update summary; notify claimant |

### Envelope
```json
{
  "eventId": "uuid",
  "eventType": "ClaimAssessed",
  "occurredAt": "2026-11-24T10:15:00Z",
  "actor": { "id": "uuid", "name": "Officer Lee", "role": "OFFICER" },
  "claim": {
    "id", "claimNumber", "version", "policyNumber", "type", "market", "currency",
    "status", "claimantId", "assignedOfficerId", "assignedOfficerName",
    "claimedAmount", "reserveAmount", "settledAmount",
    "createdAt", "assignedAt", "closedAt"
  }
}
```

Every event carries a **complete snapshot of every claim field reporting needs** (not just "status changed to X"). Why: reporting just overwrites its row with the snapshot, so it never has to rebuild state from a chain of small changes, and a missed or duplicated event cannot leave it in a half-updated state.

### Outbox flow
1. Inside the same DB transaction as the claim change: insert into `outbox_events` with status `PENDING`.
2. A `@Scheduled` job in claims-service runs every ~1 second: read `PENDING` rows (oldest first, small batch, `FOR UPDATE SKIP LOCKED` so two instances don't grab the same rows), publish to Kafka, wait for Kafka to confirm, mark `SENT`.
3. If Kafka is down, rows stay `PENDING` and `attempts` goes up; they are sent once Kafka is back.
4. A crash between "Kafka confirmed" and "marked SENT" means the event is sent again → this is **at-least-once delivery**.

### Consumer (reporting-service)
In one transaction:
1. If `event_id` is in `processed_events` → skip.
2. If `claim.version` ≤ stored `claim_version` → skip (older than what we have).
3. Upsert `claim_summary`, insert notification if needed, insert into `processed_events`.

This makes the consumer **idempotent**: handling the same event twice gives the same result as once.

The version check only decides whether the **summary** is overwritten. **Notifications are per event**: an event older than the stored version still creates its notification (duplicates are still stopped by `processed_events`). So even if events arrive out of order, nobody misses a notification.

**Known limitation — multiple publisher instances:** with one claims-service instance, events for a claim are published in order. With several instances, `SKIP LOCKED` lets two instances publish two events of the same claim concurrently, so they can arrive out of order. The summary stays correct (version check) and notifications are not lost (above), but strict ordering would need per-claim locking in the publisher. Out of scope: we run one instance.

**Personal data is kept off Kafka (data minimisation).** The snapshot leaves out the incident description, incident location and claimant name — reporting doesn't need them, and Kafka messages are retained and readable by any service with access to the topic. Consumers ignore unknown JSON fields so the event shape can grow without breaking them.

**Poison messages:** an event that keeps failing is retried a few times, then sent to `claims.events.DLT` (dead-letter topic) so one bad message can't block the partition.

---

## 8. REST API

Errors use **Problem Details** (RFC 7807 — a standard JSON error format, built into Spring as `ProblemDetail`) so the frontend handles every error the same way.

API docs: springdoc-openapi → Swagger UI on each service at `/swagger-ui.html`. The backend owns the contract; the frontend follows it.

### auth-service
| Method | Path | Who |
|---|---|---|
| POST | `/api/auth/register` | public — `{ email, password, policyNumber }`; creates a CLAIMANT only if the policy check passes; the account name is taken from the policy holder name |
| POST | `/api/auth/login` | public → returns JWT |
| GET | `/api/auth/me` | any logged-in user |
| GET | `/.well-known/jwks.json` | public (services fetch the key) |

### claims-service
Actions are separate endpoints (not one generic "PATCH status"), because each transition needs different data and different permission checks.

| Method | Path | Who |
|---|---|---|
| GET | `/api/policies/my` | claimant — policies whose holder email matches theirs |
| POST | `/internal/policies/verify` | `ROLE_SERVICE` only — `{ policyNumber, email }` → match yes/no |
| POST | `/api/claims` | claimant — `{ policyId, incidentDate, incidentLocation, description, claimedAmount }` |
| GET | `/api/claims/my` | claimant |
| GET | `/api/claims/queue` | officer — unassigned `SUBMITTED` claims in the officer's market |
| GET | `/api/claims/assigned` | officer — my open claims |
| GET | `/api/claims` | manager — all claims, filter by status/market |
| GET | `/api/claims/{id}` | owner, officers of the claim's market, manager |
| GET | `/api/claims/{id}/history` | same as above |
| POST | `/api/claims/{id}/assign` | officer (assigns to self) |
| POST | `/api/claims/{id}/information-requests` | assigned officer |
| GET | `/api/claims/{id}/information-requests` | owner, officer, manager |
| POST | `/api/claims/{id}/information-requests/{reqId}/response` | owner |
| POST | `/api/claims/{id}/documents` (multipart) | owner (optionally linked to a request) |
| GET | `/api/claims/{id}/documents` | owner, officer, manager |
| GET | `/api/claims/{id}/documents/{docId}/content` | owner, officer, manager |
| POST | `/api/claims/{id}/assessment` | assigned officer — `{ reserveAmount, note }` |
| POST | `/api/claims/{id}/settlement` | assigned officer — `{ settledAmount, note }` |
| POST | `/api/claims/{id}/rejection` | assigned officer — `{ reason }` |

### reporting-service
| Method | Path | Who |
|---|---|---|
| GET | `/api/reports/outstanding` | officer (own market), manager (all) — open claims by status and market |
| GET | `/api/reports/exposure` | manager — **all six currencies, always** (zeros when nothing is open): reserved exposure + not-yet-reserved count and claimed total (Q2, Q3) |
| GET | `/api/reports/workload` | officer (own market), manager (all, filter by market) — open claims per officer |
| GET | `/api/reports/performance` | officer (own market), manager (all, filter by market) — one row per officer: closed count, settled count, rejected count, average days from assigned to closed |
| GET | `/api/notifications` | any logged-in user — my notifications |
| POST | `/api/notifications/{id}/read` | owner |

---

## 9. File upload (simple version, with UI)

- Stored on local disk under `./data/uploads/{claimId}/{storedFilename}` (Docker volume).
- Max 10 MB per file; allowed: JPEG, PNG, PDF (checked by content type and extension).
- Download checks the same permission as reading the claim.
- Production would replace local disk with object storage (S3) — documented as out of scope.

---

## 10. Frontend (Angular)

- Feature folders: `auth/`, `claimant/`, `officer/`, `manager/`, `shared/`.
- An **HTTP interceptor** attaches the JWT to every request; a **route guard** blocks pages by role.
- Screens:
  - Login / Register
  - Claimant: My claims · New claim · Claim detail (history, answer info request, upload files) · Notifications
  - Officer: Queue · My claims · Claim detail (actions: pick up, request info, assess, settle, reject; view/download documents) · Team workload & performance
  - Manager: **one simple dashboard page** — exposure cards per currency (reserved + not-yet-reserved), outstanding claims table by status and market, team workload table — plus All claims list and Team performance
- Dashboards are deliberately simple: Angular Material cards and tables, **no charts**.
- Team performance is a single table (one row per officer: closed, settled, rejected, average days to close).
- UI: Angular Material. API client: generated from each service's OpenAPI spec with openapi-generator (Q4, Q5).

---

## 11. Testing

- **Unit:** `ClaimStateMachine` — every allowed and forbidden transition.
- **Integration (Testcontainers** — starts real PostgreSQL / Kafka in Docker during the test run, so tests hit the same tech as production instead of fakes):
  - claimant cannot read another claimant's claim (403/404)
  - claim change and outbox row are saved together (and neither is saved if the change fails)
  - consumer handles the same event twice without double-counting
  - two officers assigning the same claim → one gets 409

---

## 12. Running locally

`docker compose up` starts: PostgreSQL 18 (host port **5433**, because 5432 is often taken by a local install), Kafka (**KRaft mode** — Kafka's built-in coordination, so no separate ZooKeeper container), the three services, and the frontend. Seeded accounts are listed in the README.

Repo layout:
```
/services/auth-service
/services/claims-service
/services/reporting-service
/libs/claims-events        # shared event classes (the Kafka contract)
/libs/web-common           # shared resource-server security config + ProblemDetail error handling
/infra/postgres            # DB init script (schemas + one DB user per service)
/frontend                  # Angular
/docs/DESIGN.md, /docs/DECISIONS.md
/AI_JOURNAL.md
/docker-compose.yml
/README.md
```

`libs/web-common` holds code that must be identical in every service (JWT resource-server setup, role mapping, ProblemDetail errors, 503 on key fetch failure). One copy means a fix can't be applied to one service and forgotten in another. Trade-off: services share a build dependency — acceptable in a monorepo.

`libs/claims-events` is shared so producer and consumer can't drift apart on the event shape. Trade-off: it couples the two services' builds; in separate repos this would be a versioned schema (e.g. a schema registry) instead.

---

## 13. Out of scope (documented, not built)

- Refresh tokens, password reset, admin UI for staff accounts
- Real integration with a policy administration system (policies are seeded reference data); policy changes / endorsements
- Rate limiting on register/login
- Object storage (S3), virus scanning of uploads
- Email / SMS notifications (in-app only)
- Real-time push (polling instead)
- Claim withdrawal by claimant, reassigning a claim between officers
- API gateway, service discovery, distributed tracing
- Currency conversion / live FX rates (see Q2)

---

## 14. Decisions (resolved open questions)

| # | Question | Decision | Why |
|---|---|---|---|
| Q1 | Officers and markets | Each officer belongs to **one market**. Their queue shows only that market's claims. "Team" = officers in the same market. Seed **2-3 officers per market** and **1 manager** who sees all markets. | Matches the six-market setup and gives "team workload" a clear meaning. |
| Q2 | Exposure across currencies | Exposure is shown **per currency**. **No currency conversion.** | Adding MYR and SGD is wrong; live FX would add an external dependency, need internet on the panel's machine, and still only give daily (not real-time) rates. Listed as future work. |
| Q3 | Claims without a reserve | Shown **separately**: "Exposure (reserved)" = sum of reserves of open claims; "Not yet reserved" = count + total claimed amount. Claimed amount is never added into exposure. | Claimed amounts are the claimant's number and are often high; mixing them in makes exposure unreliable. |
| Q4 | Angular UI library | **Angular Material** | Ready-made tables, forms and dialogs; time goes into features, not CSS. |
| Q5 | Frontend API types | **Generated from OpenAPI** with openapi-generator | Backend owns the contract; if the backend changes, the frontend build breaks instead of silently drifting. |
| Q7 | More info needed after assessing | Allow `ASSESSED → INFO_REQUESTED` directly; no separate reopen action | One less endpoint and button, same outcome. |
| Q8 | Dashboard depth | Manager dashboard and team performance are simple tables/cards, no charts | Kept inside the 5-hour cap; the numbers matter more than the visuals. |
| Q9 | Who can register? | Only existing policyholders: policy number + matching email, checked by auth-service calling claims-service. Claims are opened against a policy, which supplies type, market and currency and must cover the incident date. | A non-customer registering makes no sense, and a claim without a policy has nothing to claim against. Policy was a missing core entity. |
| Q10 | Duplicate security / error code across services | Shared `libs/web-common` module | Code that must be identical lives once. |
| Q11 | Small changes from brief 01 | Public key fetched on first need; `iss` claim added and checked; `market` claim omitted for non-officers; 503 when auth-service unreachable; Postgres on host port 5433; **PostgreSQL 18** (Flyway is tested up to 18) | See sections 3, 4, 12. |
| Q12 | Account name on register | Taken from the policy holder name; not typed by the user | The insurer's record is authoritative; officers see the same name as on the policy. |
| Q13 | Seed policy dates | Relative to first migration date | Demo data never expires. |
| Q14 | "Today" for policy cover | Database date (UTC) | Known limitation; only matters around midnight. |
| Q15 | Officer visibility | Own market only, including claim detail | Follows from Q1. |
| Q16 | Reserve limit | Reserve ≤ sum insured | Sum insured is the maximum payable. |
| Q17 | Check order for actions | Permission (404/403) before state (409) | Someone without rights shouldn't learn the claim's status. Raised from Claude Code's brief 03 report. |
| Q18 | Personal data in events | Event snapshot excludes description, incident location and claimant name | Data minimisation — reporting doesn't need them. Raised by Claude Code. |
| Q19 | Where `assigned_at` comes from | New `claims.assigned_at` column, carried in every snapshot | Keeps "reporting can rebuild from any single event's snapshot" true. |
| Q20 | Performance metric | Average days from **assigned to closed** per officer | Measures the officer's handling time, not time the claim waited in the queue. |
| Q21 | `asOf` on dashboards | Latest event `occurredAt` reflected, not processing time | Doesn't look fresh when reporting is behind. |
| Q22 | Notifications vs version check | Notifications created per event regardless of version; version check only guards the summary | No lost notifications if events arrive out of order. |
| Q23 | Multi-instance ordering | Documented known limitation; one instance in scope | Raised by Claude Code. |
| Q24 | Currencies with no open claims | Always return all six currencies, zeros included | Stable dashboard; "0" is explicit rather than missing. |
| Q6 | Markets | **MY, SG, TH, ID, PH, VN** (MYR, SGD, THB, IDR, PHP, VND) | Six Southeast Asian markets; illustrative only. |

### The three amounts on a claim
| Amount | Set by | When | Meaning |
|---|---|---|---|
| `claimed_amount` | Claimant | On submit | What the claimant says it costs |
| `reserve_amount` | Officer | While open (can change) | Company's current estimate of what it will pay - **this drives exposure** |
| `settled_amount` | Officer | On settle | What was actually paid; the claim then leaves exposure |
