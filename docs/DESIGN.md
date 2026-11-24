# Claims Platform — Architecture & Data Model (v0.2)

Status: **v0.2 - decisions agreed** (see section 14).

---

## 1. What we are building

One Angular app used by three roles:

| Role | Can do |
|---|---|
| **Claimant** | Register / log in, report an incident (open a claim), track status and history, answer information requests (text + file upload), receive decisions as in-app notifications |
| **Officer** (claims staff) | See the queue of unassigned claims, pick up a claim, request more information, assess (set reserve), settle or reject, see team workload and performance |
| **Manager** | Everything read-only an officer sees, plus outstanding claims overview and total liability exposure |

---

## 2. Services

Three Spring Boot services in one Maven multi-module repo, plus the Angular frontend.

| Service | Owns | Why it is separate |
|---|---|---|
| **auth-service** | Users, passwords, login, issuing JWTs | Both other services need to know "who is this and what role". One place issues identity; everyone else only verifies it. |
| **claims-service** | Claims, status history, information requests, documents, outbox | This is where the business work happens. All writes to a claim go through here, so all claim rules live in one place. |
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
| claims / reporting → auth-service | REST, **once at startup** (fetch public key) | Token verification happens locally in each service, so auth-service being slow does not slow every request. |

### How the "async gap" is handled in the UI

- After any action, the UI re-reads the claim from **claims-service** → always up to date.
- Dashboards come from **reporting-service** → may be a few seconds behind. Each dashboard response includes `asOf` (time of the last processed event) and the UI shows "Last updated …".
- Notifications: the UI polls `GET /api/notifications` every ~15 seconds. (Server-Sent Events / WebSocket would be the upgrade; out of scope.)

---

## 4. Authentication

- auth-service signs JWTs with an **RSA private key (RS256)**. It exposes the matching public key at `GET /.well-known/jwks.json`.
- claims-service and reporting-service use Spring Security's **OAuth2 Resource Server** support, pointed at that JWKS URL. They can verify tokens but cannot create them.
  - Why RS256 instead of a shared secret (HS256): with a shared secret, every service that can verify a token can also forge one. With RS256, only auth-service holds the private key.
- Token claims: `sub` (user id), `role`, `name`, `market` (staff only). Expiry: 1 hour.
- Passwords stored as BCrypt hashes.
- Claimants self-register. Officers and managers are **seeded** by a Flyway migration (in a real company staff accounts are created by an admin, not self-registered).
- No refresh token (documented as out of scope; user logs in again after 1 hour).

---

## 5. Claim lifecycle (state machine)

```
                 pick up              request info
SUBMITTED ──────────────► UNDER_REVIEW ──────────────► INFO_REQUESTED
                              ▲   │                         │
                              │   │  claimant responds      │
                              │   └─────────◄───────────────┘
                              │
                              │ assess (set reserve)
                              ▼
                           ASSESSED ───── settle ─────► SETTLED   (closed)
                              │
           reject (from UNDER_REVIEW or ASSESSED) ─────► REJECTED (closed)
```

Rules:
- Only the **assigned officer** can act on a claim after pick-up. Managers are read-only.
- Only the **claim owner** can respond to an information request.
- `ASSESSED → UNDER_REVIEW` is allowed if the officer needs more info after assessing (they can then request info again). Reserve can be updated while the claim is open.
- `SETTLED` and `REJECTED` are terminal.
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

**claims**
| Column | Type | Notes |
|---|---|---|
| id | uuid PK | |
| claim_number | varchar unique | human-readable, e.g. `CLM-2026-000123` |
| claimant_id | uuid | user id from the JWT (no FK — different schema) |
| claimant_name | varchar | copied at creation so we don't call auth-service to display it |
| type | varchar | `MOTOR` / `PROPERTY` |
| market | varchar | one of `MY, SG, TH, ID, PH, VN` |
| currency | varchar(3) | derived from market, e.g. `MYR` |
| incident_date | date | |
| incident_location | varchar | |
| description | text | |
| claimed_amount | numeric(15,2) | what the claimant says it costs |
| status | varchar | see state machine |
| assigned_officer_id | uuid null | |
| assigned_officer_name | varchar null | |
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
| claim_version | last applied claim version — older events are ignored |
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
  "claim": { "...full snapshot of the claim at this version, incl. version..." }
}
```

Every event carries a **full snapshot** of the claim (not just "status changed to X"). Why: reporting just overwrites its row with the snapshot, so it never has to rebuild state from a chain of small changes, and a missed or duplicated event cannot leave it in a half-updated state.

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

---

## 8. REST API

Errors use **Problem Details** (RFC 7807 — a standard JSON error format, built into Spring as `ProblemDetail`) so the frontend handles every error the same way.

API docs: springdoc-openapi → Swagger UI on each service at `/swagger-ui.html`. The backend owns the contract; the frontend follows it.

### auth-service
| Method | Path | Who |
|---|---|---|
| POST | `/api/auth/register` | public (creates a CLAIMANT) |
| POST | `/api/auth/login` | public → returns JWT |
| GET | `/api/auth/me` | any logged-in user |
| GET | `/.well-known/jwks.json` | public (services fetch the key) |

### claims-service
Actions are separate endpoints (not one generic "PATCH status"), because each transition needs different data and different permission checks.

| Method | Path | Who |
|---|---|---|
| POST | `/api/claims` | claimant |
| GET | `/api/claims/my` | claimant |
| GET | `/api/claims/queue` | officer — unassigned `SUBMITTED` claims in the officer's market |
| GET | `/api/claims/assigned` | officer — my open claims |
| GET | `/api/claims` | manager — all claims, filter by status/market |
| GET | `/api/claims/{id}` | owner, any officer, manager |
| GET | `/api/claims/{id}/history` | same as above |
| POST | `/api/claims/{id}/assign` | officer (assigns to self) |
| POST | `/api/claims/{id}/information-requests` | assigned officer |
| GET | `/api/claims/{id}/information-requests` | owner, officer, manager |
| POST | `/api/claims/{id}/information-requests/{reqId}/response` | owner |
| POST | `/api/claims/{id}/documents` (multipart) | owner (optionally linked to a request) |
| GET | `/api/claims/{id}/documents` | owner, officer, manager |
| GET | `/api/claims/{id}/documents/{docId}/content` | owner, officer, manager |
| POST | `/api/claims/{id}/assessment` | assigned officer — `{ reserveAmount, note }` |
| POST | `/api/claims/{id}/reopen-review` | assigned officer — ASSESSED → UNDER_REVIEW |
| POST | `/api/claims/{id}/settlement` | assigned officer — `{ settledAmount, note }` |
| POST | `/api/claims/{id}/rejection` | assigned officer — `{ reason }` |

### reporting-service
| Method | Path | Who |
|---|---|---|
| GET | `/api/reports/outstanding` | officer, manager — open claims by status and market |
| GET | `/api/reports/exposure` | manager — per currency: reserved exposure + not-yet-reserved count and claimed total (Q2, Q3) |
| GET | `/api/reports/workload` | officer, manager — open claims per officer |
| GET | `/api/reports/performance` | officer, manager — per officer: closed count, settled vs rejected, average days to close |
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
  - Manager: Outstanding & exposure dashboard · All claims list · Team performance
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

`docker compose up` starts: PostgreSQL, Kafka (**KRaft mode** — Kafka's built-in coordination, so no separate ZooKeeper container), the three services, and the frontend. Seeded accounts are listed in the README.

Repo layout:
```
/services/auth-service
/services/claims-service
/services/reporting-service
/libs/claims-events        # shared event classes (the Kafka contract)
/frontend                  # Angular
/docs/DESIGN.md, /docs/DECISIONS.md
/AI_JOURNAL.md
/docker-compose.yml
/README.md
```

`libs/claims-events` is shared so producer and consumer can't drift apart on the event shape. Trade-off: it couples the two services' builds; in separate repos this would be a versioned schema (e.g. a schema registry) instead.

---

## 13. Out of scope (documented, not built)

- Refresh tokens, password reset, admin UI for staff accounts
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
| Q6 | Markets | **MY, SG, TH, ID, PH, VN** (MYR, SGD, THB, IDR, PHP, VND) | Six Southeast Asian markets; illustrative only. |

### The three amounts on a claim
| Amount | Set by | When | Meaning |
|---|---|---|---|
| `claimed_amount` | Claimant | On submit | What the claimant says it costs |
| `reserve_amount` | Officer | While open (can change) | Company's current estimate of what it will pay - **this drives exposure** |
| `settled_amount` | Officer | On settle | What was actually paid; the claim then leaves exposure |
