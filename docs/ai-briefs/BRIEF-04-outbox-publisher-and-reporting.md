# Brief 04 — Brief 03 follow-ups, outbox publisher, reporting-service

## Context
`docs/DESIGN.md` is now **v0.6** — read it again. New: Q17–Q20 (section 14), the explicit snapshot field list and the personal-data / dead-letter notes in §7.

Plan:
1. ~~Skeleton + infra + auth-service~~ ✅
2. ~~Docker check + shared module + policies + registration~~ ✅
3. ~~Claims core~~ ✅
4. **Follow-ups + outbox publisher + reporting-service** ← this brief
5. Document upload (backend)
6. Angular frontend
7. README, DECISIONS.md, compose for all services, clean-start check

Same rules: small meaningful commits, no real company branding, don't edit `AI_JOURNAL.md` or `docs/DESIGN.md`, comment new dependencies (and say in the report what each new library is for), **report straight away**, don't silently change the design — ask.

## Part A — Follow-ups from brief 03 (one commit each)

1. **Permission before state (Q17).** For officer actions: claim not in officer's market → 404; officer not assigned → 403; only then the state machine (409). Claimant actions: not the owner → 404, then state. Update the tests that relied on the old order and add one: a non-assigned officer trying to settle an `UNDER_REVIEW` claim gets **403**.
2. **`assigned_at` (Q19).** New migration adding `claims.assigned_at`; set on pick-up; included in the snapshot and in the claim detail DTO.
3. **Smaller event snapshot (Q18).** The `claim` snapshot contains exactly the fields listed in design §7 — no description, incident location or claimant name. Update the `claims-events` round-trip test. Configure consumers to ignore unknown JSON fields (old `PENDING` rows in my local DB have the bigger shape — they must still be consumable).

## Part B — Outbox publisher (claims-service)

Design §7 "Outbox flow".
- Add `spring-kafka`. Producer: `acks=all`, idempotent producer on, key = `aggregate_id` (claim id), value = the stored JSON payload, header `eventType`.
- A `@Scheduled` publisher every ~1 s:
  - In one transaction: select up to 50 `PENDING` rows ordered by `created_at` with `FOR UPDATE SKIP LOCKED`; send each and **wait for Kafka's acknowledgement** (with a timeout); mark `SENT` + `sent_at`.
  - On a send failure: increment `attempts` on that row, keep it `PENDING`, and **stop the batch** — don't send later rows past a failed one, so per-claim order is never broken. Log a warning (include claim id and attempts).
  - Rows that were sent before the failure in the same batch stay `SENT`.
- Publisher can be switched off by a property (useful in some tests).
- Expose the count of `PENDING` rows in the Actuator health or a log line every minute — whichever is simpler; tell me which.

## Part C — reporting-service

### C1. Schema (Flyway, schema `reporting`)
`claim_summary`, `notifications`, `processed_events` as design §6.3. `claim_summary` includes `assigned_at` and `claim_version`.

### C2. Consumer
- `spring-kafka` consumer, group id `reporting-service`, topic `claims.events`.
- For each record, **in one DB transaction** (design §7 "Consumer"):
  1. `event_id` already in `processed_events` → skip.
  2. Snapshot `version` ≤ stored `claim_version` → skip (still record the event id).
  3. Upsert `claim_summary` from the snapshot.
  4. Insert a notification if the event type needs one (below).
  5. Insert `event_id` into `processed_events`.
- Commit the Kafka offset **only after** the DB transaction commits.
- Error handling: retry a few times with backoff, then publish to **`claims.events.DLT`**. Add that topic (3 partitions) to the `kafka-init` step in compose.

Notifications:
| Event | Recipient | Message (example) |
|---|---|---|
| `ClaimAssigned` | claimant | "Your claim CLM-… is now being reviewed by an officer." |
| `InformationRequested` | claimant | "More information is needed for claim CLM-…" |
| `InformationProvided` | assigned officer | "The claimant has responded on claim CLM-…" |
| `ClaimSettled` | claimant | "Claim CLM-… has been settled: MYR 3,800.00" |
| `ClaimRejected` | claimant | "Claim CLM-… has been rejected." |

### C3. Report endpoints
All read from `claim_summary` with SQL — no stored counters (design §6.3). Every response includes `asOf` = latest `updated_at` in `claim_summary` (null if empty).

"Open" = status not `SETTLED` and not `REJECTED`.

| Endpoint | Who | Returns |
|---|---|---|
| `GET /api/reports/outstanding` | officer (own market), manager (all) | open claim counts by status, and by market |
| `GET /api/reports/exposure` | manager | **per currency**: reserved exposure (sum of `reserve_amount` of open claims with a reserve), count of open claims with a reserve, count of open claims **without** a reserve, and their total `claimed_amount` (Q2, Q3) |
| `GET /api/reports/workload` | officer (own market), manager (all; optional `market` filter) | one row per officer: open claims assigned, by status |
| `GET /api/reports/performance` | officer (own market), manager (all; optional `market` filter) | one row per officer: closed, settled, rejected, average days **assigned → closed** (Q20), total settled amount per currency |
| `GET /api/notifications` | any logged-in user | my notifications, unread first, newest first, limit 50 |
| `POST /api/notifications/{id}/read` | owner | 404 if not theirs |

Workload/performance should list only officers who appear in `claim_summary` (reporting doesn't know about officers with no claims — that's fine; note it in the report).

Delete the temporary `GET /api/reports/whoami`.

### C4. Tests
- **End-to-end (Testcontainers Postgres + Kafka):** an action in claims-service → outbox → Kafka → reporting `claim_summary` updated and notification created. Use **Awaitility** (a small library for "wait until this becomes true" in async tests, instead of `Thread.sleep`). If running both services in one test is awkward, publish a real event to Kafka from the reporting test and test the publisher separately — tell me what you chose.
- Publisher: Kafka unavailable → rows stay `PENDING` with `attempts` > 0; once Kafka is back they're sent in order.
- Consumer idempotency: the same event delivered twice → one summary row, one notification.
- Older version after newer → ignored.
- Exposure: claims in two currencies, some without reserve, some closed → correct per-currency numbers; closed claims excluded.
- Performance: average days assigned → closed computed correctly.
- An event that always fails ends up on the DLT and the next event is still processed.
- Officer from MY can't see SG workload rows; claimant gets 403 on report endpoints.

## Done when
- `./mvnw clean verify` green, 0 skipped.
- Manually: run all three services; do the brief 03 flow (claim → assign → request info → respond → assess → settle). Every `outbox_events` row becomes `SENT`; reporting's `/api/reports/exposure` shows the reserve while the claim is open and drops it after settling; the claimant sees their notifications.
- Stop Kafka, do one action, see the row stay `PENDING`; start Kafka, see it become `SENT` and reporting update.

## Report back
- Test count run / passed / skipped
- Commit list
- New libraries and what each is for
- Any design changes or questions
- Manual test steps, including the Kafka stop/start check
