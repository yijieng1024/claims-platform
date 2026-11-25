# Brief 06c — Contract fix, officer & manager screens, notifications

## Context
Read again: `docs/DESIGN.md` **v0.9** (new: Q27–Q29) and the new **`docs/FRONTEND-DESIGN.md`** — the visual design system for the frontend. Also `frontend/README.md` from brief 6a.

Plan:
1–5. Backend ✅
6a. Angular foundation + claimant ✅
6b. Restyle to the design system ✅
6c. **Contract fix + officer + manager + notifications** ← this brief
7. README, DECISIONS.md, compose for all services, clean-start check

Same rules: small meaningful commits, no real company branding, don't edit `AI_JOURNAL.md`, `docs/DESIGN.md` or `docs/FRONTEND-DESIGN.md`, explain every new library, **report straight away**, don't silently change the design — ask. I'm still new to Angular: keep it conventional and comment the *why*.

Java: the `java` on my PATH should now be JDK 25. If it isn't, tell me — don't work around it.

## Part 0 — Follow-ups from 6b (one commit)
- `docs/FRONTEND-DESIGN.md` was updated: `--positive` is now **`#17754a`** (contrast fix for the Settled chip), `--brand-light` is now documented, and your accepted deviations are recorded. Update `_tokens.scss` to match.

## Part A — Required fields in the contract (Q28)
- In the backend **response** types of all three services, mark fields that are always present as required in the OpenAPI schema. Fields that can legitimately be null (e.g. `reserveAmount` before assessment, `assignedOfficerId` before pick-up, `closedAt`, `rejectionReason`) stay optional/nullable.
- Use the cleanest springdoc-supported way for our types (e.g. `@Schema(requiredMode = REQUIRED)` or an equivalent global rule) — tell me which and why.
- `npm run api:export` + `npm run api:generate`, commit the new `openapi/*.json` and generated client.
- Fix the frontend code that used `!` / `??` workarounds for fields that are now required. The diff of `openapi/*.json` should make the change obvious in review.
- Backend tests still green.

All new screens follow `docs/FRONTEND-DESIGN.md` and the tokens/theme set up in brief 6b.

## Part C — Officer screens
Routes under `/officer`, guarded for `OFFICER`. After login an officer lands on the **Queue**.

1. **Queue** (`GET /api/claims/queue`): table — claim number, policy, type, claimed amount, incident date, submitted, waiting time. Oldest first. "Pick up" button per row → assigns, then opens the claim.
2. **My claims** (`GET /api/claims/assigned`): table with status chip; click → detail.
3. **Claim detail (staff view)** — one component shared by officer and manager (e.g. `features/staff/claim-detail`):
   - Header: claim number, status chip, policy number, type, market, **sum insured**, claimed / reserve / settled amounts, claimant name, assigned officer.
   - Incident: date, location, description.
   - The **horizontal Timeline** from the claimant claim detail (commits c874027, ebba50c), placed right under the claim number and status — **reuse the same component** (move it to `shared/` if it isn't there yet), don't build a second one. Then information requests with responses; documents list with download.
   - **Actions** — shown only to the **assigned officer**, and only those the state machine allows for the current status (design §5):
     - Pick up (if `SUBMITTED` and unassigned; any officer of the market)
     - Request information → dialog with question
     - Assess → dialog with reserve amount (show sum insured; client check ≤ sum insured) + note
     - Settle → dialog with settled amount (≤ sum insured) + note
     - Reject → dialog with reason, styled destructive, with confirmation
   - After any action, re-read the claim (design §3). Backend errors (403/409) shown via the existing ErrorService.
   - The UI hiding buttons is convenience only; the backend enforces everything — add that comment.
4. **Team** (own market): workload table (`/api/reports/workload`) and performance table (`/api/reports/performance`) — closed, settled, rejected, average days assigned → closed, settled totals per currency. Show `asOf` as "Includes changes up to …".

## Part D — Manager screens
Routes under `/manager`, guarded for `MANAGER`. After login a manager lands on the **Dashboard**.

1. **Dashboard** (cards and tables, no charts — Q8):
   - **Exposure**: six KPI cards, one per currency in fixed order (MYR, SGD, THB, IDR, PHP, VND): reserved exposure (big number), "N claims reserved", and "M not yet reserved · claimed X" (Q3).
   - **Outstanding**: open claims by status, and by market (two small tables).
   - **Team workload**: table with a market filter (segmented control / select).
   - "Includes changes up to …" from `asOf`.
2. **All claims** (`GET /api/claims`): paginated table with status and market filters → staff claim detail (read-only: no action buttons for managers).
3. **Team performance**: performance table with market filter.

Remove the `/staff` placeholder page and update `homeUrl()` for officer and manager.

## Part E — Notifications (all roles)
- Bell icon in the top nav with an unread count badge.
- Poll `GET /api/notifications` every **15 s** only while logged in; **stop polling on logout** (design §3). Use something that's easy to read (e.g. an RxJS `timer` + `switchMap` tied to the login signal) and comment why.
- Clicking the bell opens a glass panel: list newest first, unread visually marked. Clicking a notification marks it read (`POST /api/notifications/{id}/read`) and navigates to the claim — claimant → `/claims/:id`, officer/manager → their staff claim detail.
- "Mark all as read" is not needed.

## Part F — Tests and docs
- Tests: action buttons per status and assignment (e.g. Settle visible only when `ASSESSED` and assigned to me; nothing for a manager); notification polling stops after logout; officer can't open a manager route.
- `ng build` (production) with no errors.
- Update `frontend/README.md`: new folders/routes, the design tokens and where they live, how notifications polling works.

## Done when
Everything below done **only through the app** (no Swagger):
- Claimant reports an incident with a photo.
- MY officer sees it in the Queue, picks it up, requests information. Claimant gets a notification, answers with a PDF. Officer gets a notification, assesses (reserve), then settles.
- Manager dashboard shows the reserve in MYR exposure while open and drops it after settling; the claim appears in All claims; team workload/performance reflect it.
- An SG officer doesn't see the MY claim anywhere.
- The whole app follows `docs/FRONTEND-DESIGN.md`.

## Report back
- Backend `mvnw clean verify` result and frontend test count
- Commit list
- How required fields were marked (Part A) and what changed in the frontend
- Anything from `FRONTEND-DESIGN.md` you couldn't follow exactly
- New libraries and what each is for
- Screenshots or a short description of each screen
- Manual test steps
