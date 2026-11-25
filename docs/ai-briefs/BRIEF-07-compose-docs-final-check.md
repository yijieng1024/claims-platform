# Brief 07 — One-command start, documentation, final checks

## Context
Last brief. The app is feature-complete (briefs 1–6c). This brief makes it easy for a reviewer to start, read and judge.

**The reviewer only has Docker.** They will clone the public GitHub repo and run one command. They must not need Java, Maven or Node installed.

Read `docs/DESIGN.md`, `docs/FRONTEND-DESIGN.md` (updated: Timeline no longer scrolls sideways; Settle uses a green button) and `frontend/README.md`.

Same rules: small meaningful commits, no real company branding, don't edit `AI_JOURNAL.md`, `docs/DESIGN.md` or `docs/FRONTEND-DESIGN.md`, explain every new library, **report straight away**, don't silently change the design — ask.

The repo is already public on GitHub. **Don't push, don't rewrite history** (no rebase / amend of pushed commits). I push myself.

## Part 0 — Small fix
- **Settle dialog**: Settle button uses `--positive` (green, white text). The "can't be undone" note becomes normal text with an info icon (not amber, not green). Reject stays red.

## Part A — Everything in docker compose

### A1. Dockerfiles
- One **multi-stage Dockerfile per service** (auth, claims, reporting): build stage uses the Maven wrapper with JDK 25 to build the jar (build the needed modules incl. `libs/*`), runtime stage is a slim JRE 25 image running as a **non-root user**.
- Make the Maven build cache-friendly (copy POMs and download dependencies before copying sources) so rebuilds aren't painfully slow. Skip tests in the image build (tests run in CI / locally, not on the reviewer's machine).
- **Frontend**: multi-stage — Node builds the production bundle; **nginx** serves it and reverse-proxies `/api/auth`, `/api/claims`, `/api/policies`, `/api/reports`, `/api/notifications` to the services, exactly like `proxy.conf.json` does in dev. So production also has one origin and no CORS. SPA fallback to `index.html` for client routes.
- `.dockerignore` files so `node_modules`, `target`, `data/` etc. aren't sent to the build.

### A2. Compose
- Add `auth-service`, `claims-service`, `reporting-service`, `frontend` to `docker-compose.yml` alongside postgres, kafka, kafka-init.
- **Frontend on `http://localhost:4200`.** Keep services on 8081/8082/8083 on the host too, so Swagger UIs stay reachable.
- Service config via environment variables: DB URL/user per service, Kafka `kafka:29092`, JWKS URL `http://auth-service:8081/.well-known/jwks.json`, claims URL for auth-service's verify call.
- **Watch out — token issuer:** tokens carry `iss = http://localhost:8081`. If the `iss` check uses Spring's `issuer-uri`, Spring tries to fetch discovery metadata **from that URL**, which inside a container is the container itself, not auth-service. Make sure services verify signatures via the **JWKS URL** and check `iss` as a **fixed expected string**, with no network call to the issuer. Tell me what you changed.
- **Volumes**: auth-service RSA key (so tokens survive a restart), uploaded documents, Postgres, Kafka.
- Startup order with healthchecks: postgres and kafka healthy, `kafka-init` completed before claims/reporting start. Services need a health endpoint for compose healthchecks — use whatever is lightest (e.g. add Spring Boot Actuator health only; explain if you add it).
- `docker compose up --build` from a clean clone must bring up **everything**, ready to use, with no manual steps.
- Keep the dev workflow working (`npm start` + `mvnw spring-boot:run` against compose infra). Document both in the README. If running services both in compose and from Maven would clash on ports, document how to start only the infra (e.g. a compose profile or `docker compose up -d postgres kafka kafka-init`).

## Part B — Documentation

### B1. Root `README.md` (rewrite)
For a reviewer reading it for the first time. Plain English, scannable.
1. **What it is** — two or three sentences: claimants report and track claims, officers process them, managers see workload and liability exposure.
2. **Quick start** — prerequisites (Docker only), `docker compose up --build`, URL `http://localhost:4200`, roughly how long the first build takes on your machine.
3. **Demo accounts** — manager, one officer per market (pattern), claimants, and the unregistered policies for trying registration.
4. **5-minute demo script** — the end-to-end flow through the UI (claimant reports with photo → MY officer picks up, requests info → claimant answers with PDF → officer assesses, settles → manager dashboard exposure changes; SG officer can't see it). Plus the Kafka stop/start check (`docker compose stop kafka` … `start kafka`).
5. **Architecture** — a **Mermaid diagram** (GitHub renders it): browser → nginx → three services → Postgres schemas; claims → outbox → Kafka → reporting. One short paragraph each on: why three services, REST vs Kafka, outbox + idempotent consumer, schema per service, RS256/JWKS auth.
6. **Repo layout** — one line per folder.
7. **Running tests** — backend `./mvnw clean verify` (needs Docker for Testcontainers), frontend `npm test`; current counts.
8. **Development mode** — infra in compose, services from Maven, frontend `npm start`.
9. **Documentation map** — links to `docs/DESIGN.md`, `docs/DECISIONS.md`, `docs/FRONTEND-DESIGN.md`, `frontend/README.md`, `AI_JOURNAL.md`, `docs/ai-briefs/`.
10. **Known limitations & what I'd do next** — short list (see B2).

Don't paste the whole design doc into the README — link to it.

### B2. `docs/DECISIONS.md`
A short, readable record of the **key** decisions, ADR-lite: for each — *Decision*, *Why*, *Trade-off / alternative*. Around 10–12 entries, not all 29 questions (those stay in DESIGN §14). Cover at least: three services and their boundaries; REST vs Kafka; outbox + idempotent consumer; schema per service; RS256 + JWKS + service token; policyholder-only registration and Policy as an entity; per-currency exposure with reserved / not-yet-reserved split; permission-before-state checks (404 vs 403 vs 409); personal data kept off Kafka; generated client from committed OpenAPI with required fields; Angular Material re-skinned to the design system.

Then a **Known limitations** section, collected from the design doc: one publisher instance for strict ordering; orphan upload files after a crash; "today" in UTC; local-disk storage; sessionStorage token (XSS trade-off vs httpOnly cookie); polling instead of push; no refresh tokens / password reset; no rate limiting; policies are seeded reference data; no currency conversion.

And **What I'd do next** (a few bullets).

### B3. `docs/ai-briefs/`
I'll add my brief files (01–07) myself. Just link the folder from the README; don't create or edit files in it.

## Part C — Final checks
1. **Branding scan**: search the whole repo (code, docs, seed data, UI text, comments, images, commit-tracked files) for any real insurer's name or branding, case-insensitive — including the company that set this assessment. Report every hit; don't change `AI_JOURNAL.md` or files under `docs/ai-briefs/` — just tell me if they contain anything.
2. **Secrets scan**: no private keys, real credentials or tokens committed. Dev passwords for local Postgres and seeded demo accounts are fine — make sure they're documented as demo-only.
3. **Leftovers**: remove temporary endpoints, stray debug code, unused files, TODOs that no longer apply; make sure `.gitignore` covers `data/`, keys, `node_modules`, `target`, IDE files.
4. **Clean-start test**: clone the repo into a **fresh temporary folder** (from the local repo is fine), `docker compose down -v` anything running, then `docker compose up --build` from the clone. Run the README's 5-minute demo script through the UI in a browser. Time how long the first build took.
5. **Tests**: `./mvnw clean verify` and `npm test` both green; production frontend build clean.

## Done when
- From a fresh clone, `docker compose up --build` → open `http://localhost:4200` → the whole demo script works, with nothing else installed except Docker.
- README, DECISIONS.md complete; branding and secrets scans clean (or every hit reported).

## Report back
- Commit list
- First-build time and image sizes
- What you changed for the issuer / JWKS config
- Branding and secrets scan results (every hit)
- Test counts
- Anything you couldn't do, or think a reviewer will trip over
