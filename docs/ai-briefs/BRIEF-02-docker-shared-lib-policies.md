# Brief 02 — Docker check, shared module, policies & policyholder-only registration

## Context
`docs/DESIGN.md` has been updated to **v0.4** — read it again before starting. Main changes since brief 01:
- **Policy** is now a core entity (design §5, §6.2, §8, Q9). Claimants must be existing policyholders to register; claims are opened against a policy.
- Your brief 01 points 1–8 are accepted (Q10, Q11): public key on first need, `iss`, no `market` claim for non-officers, 503, port 5433, **PostgreSQL 18**, shared module.

Updated plan:
1. ~~Skeleton + infra + auth-service~~ ✅
2. **Docker check + shared module + policies + policyholder-only registration** ← this brief
3. Claims core (claims, state machine, history, information requests, outbox write)
4. Outbox publisher + reporting-service
5. Document upload (backend)
6. Angular frontend
7. README, DECISIONS.md, compose for all services, clean-start check

Same project rules as brief 01: small meaningful commits, no real company branding, don't edit `AI_JOURNAL.md`, comment new dependencies, **report straight away**, and **don't silently change the design** — ask.

Java 25 is now first on my PATH, so you shouldn't need to set `JAVA_HOME`. Tell me if `java -version` still shows 8.

## Steps

### 1. Docker check (do this first)
Docker Desktop is now installed.
- Switch the Postgres image to **`postgres:18-alpine`**.
- `docker compose up -d` → confirm postgres + kafka healthy, `kafka-init` exited 0, topic `claims.events` has 3 partitions.
- `./mvnw verify` → the 6 Testcontainers tests must **run and pass** (0 skipped). Fix anything that fails.
- If something in compose was wrong, fix it in its own commit and tell me what was wrong.

### 2. Shared module `libs/web-common`
Move the code that is identical across services into one module (design §12):
- JWT resource-server config, `role` claim → `ROLE_*` mapping, `iss` check
- ProblemDetail error handling (advice + error controller), including the 503 for key fetch failure
- Make it auto-configured (Spring Boot auto-configuration) so each service just adds the dependency. Each service still declares its own URL rules.
- auth-service keeps its own config where it differs (it issues tokens rather than only verifying), but should reuse the ProblemDetail handling.
- Existing tests must still pass. Pure refactor commit — no behaviour change.

### 3. Policies in claims-service (design §6.2)
- Flyway migration: `policies` table in schema `claims`.
- Seed data (Flyway), all fake, `@example.com` emails:
  - The **2 existing demo claimants** each get policies matching their email: at least one active MOTOR, one active PROPERTY, one **expired** (end date in the past), in **different markets**.
  - **3–4 extra policies with no account yet**, in different markets, so a reviewer can try registering. One of them `CANCELLED` (registration must fail).
  - Sensible `sum_insured`, `insured_item` (fake vehicle plate / fake address), start/end dates.
  - List all seeded policies (number, holder email, type, market, status, dates) in the README.
- `GET /api/policies/my` — `ROLE_CLAIMANT`; returns policies where `holder_email` = the token's `email` claim (case-insensitive). Include a computed `inForceToday` boolean.

### 4. Internal verify endpoint (claims-service)
- `POST /internal/policies/verify` body `{ policyNumber, email }` → `{ match: true|false, holderName? }`.
- `match` is true only if: policy exists **and** status is not `CANCELLED` **and** holder email matches (case-insensitive). An expired policy still matches (a former customer may still need to claim for an incident during cover).
- Secured: only `ROLE_SERVICE` may call `/internal/**`; and `ROLE_SERVICE` may call **nothing else**.

### 5. auth-service: service token + registration check
- Add `email` to the user JWT.
- auth-service can mint a **service token** for itself: same RS256 key, `sub` = `auth-service`, `role` = `SERVICE`, expiry 1 minute. Never returned to any client.
- `POST /api/auth/register` body becomes `{ name, email, password, policyNumber }`:
  1. Validate input (as before).
  2. Call claims-service `/internal/policies/verify` with the service token (claims URL configurable).
  3. `match=false` → **400** ProblemDetail, one generic message for every failure reason (e.g. "We couldn't verify these policy details."). Don't reveal whether the number or the email was wrong, or whether the policy is cancelled.
  4. claims-service unreachable or erroring → **503** ProblemDetail. No account is created.
  5. Duplicate email → 409 (as before). Check this **before** calling claims so we don't leak policy info for an already-registered email.
- Use Spring's `RestClient` with sensible connect/read timeouts (a few seconds).
- The 2 seeded demo claimants stay seeded (they don't go through register).

### 6. Tests
- claims-service:
  - `/api/policies/my` returns only the caller's policies
  - verify: correct number+email → match; wrong email → no match; cancelled → no match; expired → match
  - `/internal/**` with a CLAIMANT token → 403; `/api/policies/my` with a SERVICE token → 403
- auth-service (mock the claims call, e.g. with a stubbed HTTP server — tell me what you used and why):
  - register with valid policy → 201 and can log in
  - policy check fails → 400 with the generic message
  - claims unreachable → 503, no user row created
  - duplicate email → 409 without calling claims
- All Testcontainers tests run (0 skipped).

## Done when
- `docker compose up -d` + `./mvnw verify` → all green, 0 skipped.
- Manually: register a new claimant with one of the unregistered seeded policies through auth-service Swagger; log in; call `/api/policies/my` on claims-service and see that policy.
- Registering with the cancelled policy, or a correct number with the wrong email, gives the same 400 message.

## Report back
- What (if anything) was wrong with compose once Docker ran it
- Test count: run / passed / skipped
- Commit list
- Anything you had to change or question in the design
- Exact manual test steps with the seeded policy numbers to use
