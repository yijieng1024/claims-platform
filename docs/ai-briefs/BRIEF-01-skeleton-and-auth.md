# Brief 01 — Repo skeleton, local infra, auth-service

## Context
We are building a claims platform for a take-home assessment. The full design is in `docs/DESIGN.md` (v0.2) — **read it first**. It is the source of truth; if something in this brief conflicts with it, stop and ask me.

This is brief 1 of ~6. Planned order:
1. **Skeleton + infra + auth-service** ← this brief
2. claims-service core (claims, state machine, history, information requests, outbox write)
3. Outbox publisher + reporting-service (consumer, summary, reports, notifications)
4. Document upload (backend)
5. Angular frontend
6. README, DECISIONS.md, test pass, clean-start check

Do **only** brief 1. Don't start claims or reporting business logic.

## Rules for the whole project
- **Small, meaningful commits** with clear messages (e.g. `feat(auth): add login endpoint issuing RS256 JWT`). The commit history is graded — never one giant commit. Commit after each logical step below.
- **No company names, logos or branding** of any real insurer anywhere (code, docs, seed data, UI text). The product name is just "Claims Platform".
- **Do not write in `AI_JOURNAL.md`** beyond creating it (step 1). I write the journal myself.
- When you add a library, add a one-line comment in the `pom.xml` (or a note in your report) saying what it is for.
- When done, **report straight away** — don't wait for CI.

## Tech versions
- Java 25 (LTS)
- Latest stable Spring Boot that supports Java 25 — check, don't guess. Tell me the version you picked.
- Maven multi-module
- PostgreSQL (latest stable image), Flyway
- Kafka: official `apache/kafka` image in **KRaft mode** (no ZooKeeper)
- springdoc-openapi version compatible with the chosen Spring Boot

## Steps

### 1. Repo skeleton
Create this layout (`docs/DESIGN.md` already exists — keep it):
```
/pom.xml                      # parent POM, Java 25, dependency management
/services/auth-service
/services/claims-service      # skeleton only in this brief
/services/reporting-service   # skeleton only in this brief
/libs/claims-events           # empty module for now (event classes come in brief 2)
/frontend/                    # empty placeholder (.gitkeep)
/docs/DESIGN.md
/AI_JOURNAL.md                # just a title line: "# AI Working Journal"
/README.md                    # short placeholder; full README is brief 6
/docker-compose.yml
/.gitignore
```

### 2. docker-compose (infra only for now)
- `postgres` with a volume, and an init script that creates the three schemas: `auth`, `claims`, `reporting`. One DB user per service, each only allowed to use its own schema (design §2).
- `kafka` (KRaft, single node), reachable from host and from containers. Topic auto-create **off**; create topic `claims.events` with **3 partitions** via an init step.
- Healthchecks on both.
- Services themselves are **not** in compose yet (we add them in brief 6). For now they run from the IDE / `mvn spring-boot:run` against the compose infra.
- Ports: auth-service `8081`, claims-service `8082`, reporting-service `8083`.

### 3. auth-service (complete in this brief)
Follow design §4 and §6.1.

- `users` table via Flyway in schema `auth`.
- Seed via Flyway (passwords BCrypt-hashed in the migration; put the plain passwords in README):
  - 1 manager (no market)
  - 2–3 officers per market for `MY, SG, TH, ID, PH, VN`
  - 2 demo claimants
  - Use obviously fake names and `@example.com` emails.
- RSA key pair:
  - For local dev, generate on first start if no key is configured, and **persist it to a file outside git** so tokens stay valid across restarts. Key path configurable by env var.
  - Never commit a private key.
- Endpoints:
  - `POST /api/auth/register` → creates a CLAIMANT only (role can't be chosen by the caller). Validate email format, password min length 8, unique email (409 if taken).
  - `POST /api/auth/login` → returns `{ accessToken, expiresIn, user: {id, name, email, role, market} }`. Wrong email or password → same 401 message for both (don't reveal which one was wrong).
  - `GET /api/auth/me` → current user from the token.
  - `GET /.well-known/jwks.json` → public key in JWKS format.
- JWT: RS256, 1 hour expiry, claims `sub`, `role`, `name`, `market` (null for non-officers), `iss`.
- Errors as `ProblemDetail` (RFC 7807) everywhere, including validation errors and 401/403.
- springdoc: Swagger UI at `/swagger-ui.html`, with a bearer-token "Authorize" button.

### 4. claims-service and reporting-service skeletons
Just enough to prove the auth wiring works:
- Each starts on its port, connects to Postgres with its own DB user and schema, runs an empty Flyway baseline.
- Each is an OAuth2 Resource Server using the auth-service JWKS URL (configurable).
- Map the `role` claim to Spring authorities (`ROLE_CLAIMANT`, `ROLE_OFFICER`, `ROLE_MANAGER`).
- One temporary endpoint each, `GET /api/claims/whoami` and `GET /api/reports/whoami`, returning the caller's id/role/market from the token. (We delete these in later briefs.)
- Starting claims/reporting while auth-service is down must not crash them; they fetch the key when the first request needs it.

### 5. Tests
- auth-service:
  - register → login → `/me` happy path
  - register with existing email → 409
  - login wrong password → 401, and same body as unknown email
  - register cannot create an OFFICER even if `role` is sent in the body
- Use **Testcontainers** for PostgreSQL (real DB in Docker during tests). Tell me if Docker isn't reachable from tests on my machine.
- claims-service: a test that a request with a token signed by a **different** key is rejected (401).

## Done when
- `docker compose up -d` starts Postgres + Kafka healthy, topic `claims.events` exists with 3 partitions.
- All three services start; I can log in as an officer in auth-service Swagger, copy the token, and call `/api/claims/whoami` and `/api/reports/whoami` successfully.
- `mvn verify` passes from the repo root.

## Report back
- Spring Boot and other key library versions picked, and why
- Commit list
- Anything in the design you think is wrong or that you had to change — **don't silently change the design**
- Exact commands I run to start everything
