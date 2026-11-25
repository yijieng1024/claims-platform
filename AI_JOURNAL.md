# AI Working Journal

Running log of how I used AI on this assessment: what I asked, what I accepted, what I challenged, and what I overrode.

**Tools**
- Claude (chat) — breaking down the brief, design discussion, writing implementation briefs
- Claude Code CLI — implementing each brief in the repo

**Labels**
- **ACCEPTED** — took the AI's suggestion as is
- **CHALLENGED** — questioned it / asked why before deciding
- **OVERRODE** — rejected the AI's suggestion and did something else
- **CORRECTED** — the AI got a fact wrong and I fixed it

---

## 2026-11-24 — Planning & design (Claude chat)

**16:00 — Asked Claude to break down the brief.**
ACCEPTED: the brief rewards decisions + explanation more than volume; start backend-first.

**16:07 — CORRECTED: backend language.**
Claude suggested C#/.NET based on outdated info about my own project. My current project's backend is already Java + Spring Boot, so I chose Spring Boot.
Reason: Java with Spring Boot is a mainstream backend stack in the finance and insurance sector, and it's the stack I'm currently using in my own project.

**16:18 — ACCEPTED: 2-service split + Kafka for side effects.**
REST for anything the user waits on; Kafka for things that can lag (dashboard numbers, notifications).

**16:27 — OVERRODE: authentication.**
Claude suggested skipping real auth (seeded users + a header) to save time. I decided to build real auth.
Reason: Authentication is fundamental to the platform: it enforces role-based access (e.g. a claimant can only see their own claims) and is needed for security compliance.

**16:27 — CHALLENGED: "skip file upload".**
Asked why. Claude's reason: costs a lot of time, adds little to the core of the brief. Then re-read the brief: "provide additional information when asked" means documents in insurance, so I decided to build a simple version **with UI** (Claude's compromise had no UI).

**16:27 — CHALLENGED: outbox pattern.**
Claude first suggested only mentioning it in the docs. I asked for an explanation of the dual-write problem, confirmed my understanding (a waiting-list table written in the same transaction, sent later by a background job), and decided to build it, including an idempotent consumer.

**16:59 — Decided: separate auth-service** (Claude gave options A: inside claims-service / B: separate). Chose B.
Reason: both claims and reporting need identity; one service issues tokens, others only verify.

**17:00 — ACCEPTED (after reading the design doc):**
- one Postgres schema per service (real data boundary)
- RS256 JWT with JWKS (only auth-service can sign)
- one Kafka topic keyed by claim id (per-claim ordering)
- events carry a full claim snapshot; dashboards query a summary table instead of keeping counters

**17:07 — Exposure across currencies.**
ACCEPTED per-currency exposure. Asked whether live FX conversion was possible; Claude suggested a free daily-rate API. I decided **not** to build it — not real-time, adds an external dependency, and per-currency already answers the question correctly.

**17:09 — Officers & markets.** Each officer belongs to one market; team = officers in the same market.

**17:12 — CHALLENGED: exposure for claims without a reserve.**
Didn't understand the question at first; asked for an example. Decided to show "reserved exposure" and "not yet reserved (count + claimed total)" separately. Learned the difference between claimed amount, reserve and settled amount.

**17:12 — CORRECTED: markets.** Claude picked MY, SG, TH, ID, PH, HK. HK isn't Southeast Asia — changed to VN.

**17:12 — ACCEPTED:** Angular Material (I'm new to Angular), generated TypeScript client from OpenAPI (backend owns the contract).

**17:18 — Decided:** I write this journal myself; Claude Code is told not to edit it.

**17:20 — CHALLENGED: one repo or several?** Asked whether the brief requires separate repos. It asks for one "Git repository" and one GitHub link, so I went with a monorepo (one clone, one `docker compose up`, one commit story). This differs from my usual one-repo-per-part habit.

**17:35 — Scope review.**
Went through the feature list with Claude and decided what to keep and what to simplify:
- Kept: auth-service, outbox, file upload with UI, notifications UI, generated TypeScript client
- Simplified: team performance and manager screen are plain tables/cards, no charts — the numbers matter more than the visuals
- OVERRODE Claude's suggestion to hand-write TypeScript types: kept the generated client (Claude agreed the speed is about the same once set up, and it enforces the contract)
- ACCEPTED Claude's simpler state machine: `ASSESSED → INFO_REQUESTED` instead of a separate "reopen review" action — same outcome, one less endpoint

---

## 2026-11-24 — Brief 01: skeleton + infra + auth-service (Claude Code)

**17:45 — Review of Claude Code's report.**
- Asked Claude Code to: build repo skeleton, docker-compose (Postgres + Kafka), full auth-service, skeleton claims/reporting that verify JWTs.
- It did: all of the above, 11 commits. Spring Boot 4.2.0, Java 25.
- Problem: Docker wasn't installed on my machine yet, so the 6 Testcontainers tests were **skipped** (`mvn verify` "passed" only because skipped ≠ failed) and docker-compose was never run. Not accepted as done — Docker check moved to the start of brief 02.
- Good: Claude Code tested "does the key survive a restart" on its own and found a real bug (auth-service crashed on second start); fixed with a regression test.
- I checked: Didn't verify manually at this point — relied on Claude Code's report. Since Docker wasn't installed, I treated it as not done and moved verification to the start of brief 02.
- ACCEPTED its design questions 1–7: fetch public key on first request instead of at startup; `iss` claim; no `market` claim for non-officers; 503 when auth-service is unreachable; Postgres on port 5433.
- OVERRODE: Postgres 19 → **18**, because Flyway is only tested up to 18.
- Decided (Claude gave a/b): shared `libs/web-common` module for the duplicated security + error code, so it can't be fixed in one service and forgotten in another.
- Fixed my PATH (Java 8 was before Java 25).

**17:48 — CHALLENGED: registration.**
Pointed out that letting anyone register makes no sense — only existing customers should. This revealed that **Policy** was a missing core entity in the design. Decided (Claude gave A/B/C):
- policies table in claims-service, seeded as simulated data (real companies have a separate policy admin system)
- register requires policy number + matching email; auth-service checks with claims-service over REST
- claims are opened against a policy; type, market and currency come from the policy; incident date must be within cover
- design doc updated to v0.4

---

## 2026-11-24 — Brief 02: Docker check, shared module, policies & registration (Claude Code)

**18:12 — Review of Claude Code's report.**
- Asked Claude Code to: verify Docker/compose and run the skipped tests first, move duplicated security/error code into `libs/web-common`, add seeded policies, and allow registration only for existing policyholders.
- It did: all of the above, 6 commits. **20 tests run / 20 passed / 0 skipped.**
- Running the tests for real exposed a bug that had been hidden while they were skipped: SQL found no tables in tests because the DB connection didn't pin the service's schema. Fixed in its own commit. This confirmed that treating "skipped" as "not done" in brief 01 was right.
- Mistake on my side: I sent the brief before replacing `docs/DESIGN.md` with v0.4. Claude Code noticed, did the parts not affected by the change first, then picked up v0.4.
- I checked (manually, Swagger): registered a new claimant with an unregistered seeded policy → 201; logged in and saw the policy in `GET /api/policies/my`; a cancelled policy and a correct policy number with the wrong email both returned the same 400 message.
- Decided on its open points:
  - Account name now comes from the **policy holder name**, not typed by the user — the insurer's record is authoritative
  - Seeded policy dates made **relative** to the migration date, so demo data never expires (via a new migration, not by editing an applied one)
  - ACCEPTED: "today" uses the DB date in UTC — documented as a known limitation
- Still to fix on my machine: an Oracle `java8path` entry is ahead of JDK 25 on PATH.

---

## 2026-11-24 — Brief 03: claims core (Claude Code)

**18:18 — Decisions while writing the brief.**
- ACCEPTED: officers can only see and act on claims in **their own market**, including claim detail (not just the queue) — follows from "each officer belongs to one market".
- ACCEPTED: **reserve ≤ policy sum insured**, since sum insured is the maximum payable.
- ACCEPTED: a claimant opening someone else's claim gets **404, not 403**, so the API doesn't reveal that the claim exists. An officer acting on a claim assigned to someone else in their market gets 403.

**18:51 — Review of Claude Code's report.**
- Asked Claude Code to: build the claims core — claims, state machine, status history, information requests, and writing an outbox row in the same transaction as every change. Plus brief 02 follow-ups (account name from policy, relative seed dates).
- It did: 8 commits. **68 tests run / 68 passed / 0 skipped**, including all 9 allowed and all 27 forbidden state transitions, and a test that forces the outbox insert to fail and checks the whole change is rolled back.
- I checked (manually, Swagger + SQL): as claimant opened a claim on POL-MY-MTR-0001; as a MY officer picked it up and requested info; responded as claimant; assessed and settled as officer. Queried `claims.outbox_events`: 6 `PENDING` rows for my claim, in order, versions 0–5.
- CHALLENGED: check order. A non-assigned officer trying to settle an `UNDER_REVIEW` claim got 409 (wrong status) instead of 403 — this leaks the claim's status to someone without rights. Changed to permission first, then state (fixed in brief 04).
- ACCEPTED (raised by Claude Code): remove incident description, location and claimant name from Kafka events — reporting doesn't need them, so personal data stays off Kafka (data minimisation).
- CHALLENGED: Claude Code planned to take `assigned_at` from the time of the `ClaimAssigned` event. Asked why the snapshot principle matters; decided to add an `assigned_at` column so every event's snapshot carries it.
- ACCEPTED (Claude's proposal, I confirmed): team performance measures average days from **assigned to closed**, so queue waiting time isn't counted against the officer.

---

## 2026-11-24 — Brief 04: outbox publisher + reporting-service (Claude Code)

**22:08 — Review of Claude Code's report.**
- Asked Claude Code to: fix check order (permission before state), add `assigned_at`, shrink the event snapshot, build the outbox publisher, and build reporting-service (idempotent consumer, notifications, dead-letter topic, report endpoints).
- It did: 7 commits. **80 tests run / 80 passed / 0 skipped.** The publisher test pauses the Kafka container to simulate an outage and checks later events are never sent past a failed one.
- I checked (manually, after brief 05): stopped Kafka with `docker compose stop kafka` and opened a claim — **the request still succeeded**. The outbox row stayed `PENDING` with `attempts` increasing, and the manager's exposure didn't change yet. Started Kafka again: the row became `SENT` and exposure updated with the new claim.
- Decided on its questions:
  - `asOf` = time of the newest claim change included, not processing time — so dashboards never look fresh when reporting is behind
  - CHALLENGED → fixed: Claude Code found that out-of-order events (possible with several publisher instances) would drop a notification. Kept the ordering issue as a documented known limitation (we run one instance) but made notifications independent of the version check, so none are lost
  - Exposure always shows all six currencies, zeros included — stable dashboard, "0" is explicit

---

## 2026-11-24 — Brief 05: follow-ups + document upload (Claude Code)

**22:30 — Review of Claude Code's report.**
- Asked Claude Code to: apply the brief 04 decisions, build document upload/list/download, and fix my Java PATH.
- It did: 6 commits, no new libraries. **88 tests run / 88 passed / 0 skipped.**
- Upload checks file extension, declared type **and the file's first bytes**, so a renamed `.exe` is rejected; files are stored under random UUID names to prevent path traversal; 10 MB limit returns 413.
- ACCEPTED its decisions: 413 comes before the permission check (the size limit is enforced while the request is read, before the claim is known — reveals nothing); claim row is locked during upload so two uploads can't both pass the 20-file limit; a file missing from disk returns 410; orphan files after a crash documented as a known limitation instead of building a cleanup job.
- PATH: Claude Code had no admin rights and didn't try a workaround — gave me the steps; I removed the `java8path` entry myself.
- I checked (manually, Swagger): uploaded a PDF linked to an open information request and a JPG without one → both 201; a text file renamed to .pdf → 415; MY officer listed and downloaded both files (same content as uploaded); SG officer got 404 on the same claim.

---

## 2026-11-24 — Brief 06a: Angular foundation + claimant screens (Claude Code)

**22:41 — Decisions while writing the brief.**
- Split the frontend into two briefs (foundation + claimant first, then officer + manager + notifications) because I'm new to Angular and want to understand the foundation before building on it.
- ACCEPTED: token stored in `sessionStorage` — survives a refresh, cleared when the tab closes. Trade-off: readable by any script on the page (XSS risk); an httpOnly cookie would be the production choice, kept out of scope.
- Asked Claude Code to write `frontend/README.md` explaining the structure in plain English, so I can learn and defend it.
- OpenAPI specs are committed as JSON and the client is generated from them, so the frontend builds without the backend running and contract changes show up as diffs.

**23:12 — Review of Claude Code's report.**
- It did: Angular 22 + Angular Material, generated API clients from committed OpenAPI specs, auth (sessionStorage, interceptor, role guards), claimant screens. 11 commits, 7 frontend tests, production build clean.
- CHALLENGED: it changed backend OpenAPI config but didn't re-run the backend tests. Not accepted as done — made it run `mvnw clean verify` first in the next brief (88/88 passed).
- ACCEPTED: IDR shown without decimals (common practice).
- CHALLENGED: every generated TypeScript field was optional because the backend didn't mark required fields — this weakens "the contract is enforced by the build". Decided to fix it in the backend and regenerate (brief 06c).
- Asked Claude to walk me through the frontend architecture using the actual code (bootstrap, folders, routes + guards, request path through interceptor and proxy, AuthService, generated client).

---

## 2026-11-24 — Brief 06b: restyle (Claude Code)

**23:22 — OVERRODE: the default Angular Material look.**
Didn't like how the frontend looked. Decided to reuse the design language from my own earlier project (PickWise): glassmorphism, navy brand, calm neutral canvas. Since PickWise is React + Tailwind, chose to **re-skin Angular Material** rather than switch to Tailwind (keeps the working components); no dark mode. Claude adapted the rules into `docs/FRONTEND-DESIGN.md`.

**23:40 — Review of Claude Code's report.**
- It did: tokens file, custom Material theme from the navy, glass top nav with a new logo, flat cards, status chips with icon + text. No new libraries (removed the Roboto font and prebuilt theme). Backend 88/88, frontend 7/7, build clean.
- ACCEPTED deviations caused by Material limits (hover overlay instead of opacity, 48px fields, larger bundle warning limit).
- Decided: darkened the "positive" green (`#1a7f4f` → `#17754a`) because Settled chip text was 4.38:1, below the 4.5:1 contrast minimum. Claude Code flagged it instead of changing it silently.
- Removed a per-machine Angular CLI analytics ID from `angular.json` (shouldn't be in the repo).

**23:45 — Directed a UX change myself: Timeline.**
The status history sat low on the claim page as a vertical list. Asked Claude Code to move it right under the claim number and status, rename it Timeline, lay it out horizontally, and show the upcoming steps too, so a claimant sees at a glance where their claim is and what's left. Later asked for it to fit the card width instead of scrolling sideways. Claude Code hadn't checked it in a browser — I verified it myself.

**23:57 — Directed form UX fixes.**
Asked for inline login errors instead of a popup, red required asterisks, and a show/hide password toggle. Both fields turn red on a failed login because the backend deliberately doesn't reveal which one was wrong. Claude Code verified in the browser and found + fixed a bug (editing only the password left the email red and the button disabled).

---

## 2026-11-25 — Brief 06c: contract fix, officer & manager screens, notifications (Claude Code)

**12:06 — Review of Claude Code's report.**
- It did: required fields in the contract, officer screens (queue, my claims, claim detail with state-machine actions, team), manager screens (dashboard, all claims, team performance), notification bell. 11 commits, no new libraries. **Backend 89/89, frontend 22/22.**
- Required fields: rule is "every field is required unless marked `@Nullable`" — only ~20 fields needed marking instead of ~100, readable from the record, and guarded by a new contract test. Removed all the `!` / `??` workarounds in the frontend.
- It found and fixed two bugs while testing: a double click could open two action dialogs; a 404 claim left the progress bar spinning.
- ACCEPTED: the Reject reason dialog is itself the confirmation step.
- OVERRODE: Settle should use the green "positive" colour, not amber — it's the good outcome. Kept the "can't be undone" note in neutral text so green doesn't read as "success".
- Decided not to rewrite history for a commit that accidentally included an unrelated file deletion — the next commit message explains it, and the repo was already pushed.
- I checked: played through the whole app myself — claimant, MY officer, manager, SG officer — no problems.

**12:12 — Directed a feature myself: image preview.**
Officers reviewing claims had to download every photo to look at it. Asked Claude Code to add an eye button for JPEG/PNG documents that opens the image in a dialog. It loads the image through the app's HTTP client so the token is sent (a plain `<img>` link would be refused by the backend) and frees the memory on close.

---

## 2026-11-25 — Brief 07: one-command start, docs, final checks (Claude Code)

**12:45 — Review of Claude Code's report.**
- It did: multi-stage Dockerfiles (reviewer needs only Docker), nginx serving the frontend and proxying `/api` (one origin, no CORS in production either), compose with healthchecks and volumes, README rewrite, `DECISIONS.md`. **Backend 89/89, frontend 23/23.**
- Clean-start test from a fresh clone: about 3 minutes to build and become healthy; the whole demo script ran in the browser, including the Kafka stop/start check.
- CHALLENGED by evidence: the brief (written by Claude) warned that Spring's `issuer-uri` would try to call `localhost:8081` inside a container. Claude Code read Spring Boot 4.2's decoder source and showed that with a JWKS URL set, `issuer-uri` only adds a fixed-string `iss` check — no config change was needed. Accepted, because it was verified against the source, not assumed.
- ACCEPTED: Spring Boot Actuator added only for compose healthchecks; only `/actuator/health` is exposed, with no details.
- Found during the demo and fixed: clicking a notification for the claim already open did nothing.
- Branding scan clean; I also searched the repo and commit history for the assessing company's name myself.
- Found I'd saved my journal to `docs/AI_JOURNAL.md` by mistake while the root file was empty — moved it to the root.

**12:28 — CHALLENGED: can a settled claim be claimed again?**
Asked whether a claimant could claim the same incident twice. Yes — nothing prevented it (a duplicate claim, a common fraud pattern). Also found that sum insured was only checked per claim, so several claims could pay out more than the policy's maximum. Decided: block a second claim on the same policy + incident date unless the earlier was rejected (service check + partial unique index for races), and treat sum insured as a per-policy total with "remaining cover" shown to officers (policy row locked during assess/settle). → Brief 08.

**12:35 — Researched Malaysian claims regulation.**
Brought research on BNM's Policy Document on Claims Settlement Practices (general insurance — matches our motor/property scope). Asked Claude to verify it: the policy document and the motor betterment scale were confirmed from public sources; several specific numbers couldn't be verified, so they're not stated as facts in the repo.
Claude suggested an authority matrix (settlement above a limit needs approval). **OVERRODE:** with one manager covering all six markets, a real authority matrix means per-market approvers and a new approval status — a redesign, not a feature. Kept it, along with SLA tracking, straight-through processing, betterment calculation and fraud red flags, as documented next steps.

---

## Template for each brief

**HH:MM — Brief NN: (title)**
- Asked Claude Code to: …
- It did: …
- I checked: … (ran tests? read the code? tried it in Swagger?)
- ACCEPTED / CHALLENGED / OVERRODE: … because …