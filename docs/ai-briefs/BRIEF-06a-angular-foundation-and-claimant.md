# Brief 06a — Angular foundation + claimant screens

## Context
`docs/DESIGN.md` is **v0.8** — read §1, §3, §8, §10 and section 14 before starting. The backend is complete (briefs 1–5). This brief starts the frontend.

**I'm new to Angular.** I have to explain this frontend in an interview, so:
- Keep the structure conventional and simple. Prefer Angular's standard, current way of doing things over clever patterns.
- Every non-obvious choice gets a one-line comment saying *why*.
- At the end, write `frontend/README.md` explaining the structure in plain English (see "Report back").

Plan:
1–5. Backend ✅
6a. **Angular foundation + claimant screens** ← this brief
6b. Officer screens, manager dashboard, notifications
7. README, DECISIONS.md, compose for all services, clean-start check

Same rules: small meaningful commits, no real company branding (product name "Claims Platform", neutral colours, no insurer logos), don't edit `AI_JOURNAL.md` or `docs/DESIGN.md`, explain every new library, **report straight away**, don't silently change the design — ask.

## Versions
- Latest stable **Angular** and **Angular Material** — check, don't guess. Tell me the versions and the Node version you used.
- Standalone components (no NgModules), Angular's built-in control flow (`@if`, `@for`), signals where they make state simpler. Use Angular's default test runner for the version you pick.

## Part A — Foundation

### A1. Project
- Angular app in `/frontend`, SCSS, Angular Material with one prebuilt neutral theme.
- Folder structure:
  ```
  src/app/
    core/        # auth service, HTTP interceptor, route guards, error handling — app-wide singletons
    shared/      # small reusable UI pieces: status chip, money display, page layout
    api/         # GENERATED code only — never edited by hand
    features/
      auth/      # login, register
      claimant/  # claimant pages
      officer/   # (brief 6b)
      manager/   # (brief 6b)
  ```

### A2. Dev proxy
`proxy.conf.json` used by `npm start` (design §2 — no gateway, no CORS):
- `/api/auth` → `http://localhost:8081`
- `/api/claims`, `/api/policies` → `http://localhost:8082`
- `/api/reports`, `/api/notifications` → `http://localhost:8083`

### A3. Generated API client (Q5)
- Use **openapi-generator** (`@openapitools/openapi-generator-cli`, generator `typescript-angular`) to generate one client per service into `src/app/api/{auth,claims,reporting}`.
- Export each service's OpenAPI spec (`/v3/api-docs`) to `frontend/openapi/{auth,claims,reporting}.json` and **commit those JSON files**. Generation reads the committed files, so building the frontend never needs the backend running, and a contract change shows up as a diff in review.
- `npm run api:export` (fetches specs from running services) and `npm run api:generate` (regenerates clients from the JSON files). Document both in `frontend/README.md`.
- If a backend endpoint's OpenAPI description is missing something the client needs (e.g. multipart upload, binary download, response types), fix the **backend annotation** in a separate commit rather than hand-editing generated code. Tell me what you changed.

### A4. Auth
- `AuthService` holds the current user and token; exposes the user as a signal.
- **Token storage: `sessionStorage`** — survives a page refresh, cleared when the tab closes. Add a comment noting the trade-off: any script on the page can read it (XSS risk); an httpOnly cookie would be the production choice (out of scope).
- On load, if the stored token is expired, discard it.
- **HTTP interceptor**: adds `Authorization: Bearer …` to `/api/**` requests; on **401** → log out and go to login with a "Session expired" message.
- **Route guards** by role. After login, redirect by role: claimant → My claims; officer → Queue (6b); manager → Dashboard (6b). Until 6b exists, officer/manager land on a simple placeholder page.
- Top bar: product name, user's name and role, logout. Navigation links depend on role.

### A5. Errors
- One place turns backend **ProblemDetail** responses into user messages: field validation errors shown under form fields; other errors as a Material snackbar using the ProblemDetail `detail`.
- 503 → "A service is temporarily unavailable, please try again."

## Part B — Claimant screens

### B1. Login & Register
- Login: email + password.
- Register: email, password, **policy number** (design §4 — no name field). On success, go to login with a success message. Show the backend's generic policy error as is.

### B2. My claims
- Table: claim number, policy number, type, status (chip with colour per status), claimed amount (with currency), submitted date. Newest first. Click → detail.
- Empty state with a "Report an incident" button.
- Button "Report an incident" → New claim.

### B3. New claim
- Policy picker from `GET /api/policies/my`: show policy number, type, insured item, cover dates, and whether in force today. Hide cancelled policies (backend won't return them as usable anyway — check).
- Incident date picker: limited to the selected policy's cover period and not after today.
- Location, description, claimed amount (currency shown from the policy).
- Optional: attach files at creation — after the claim is created, upload them via the documents endpoint, then go to the claim detail. If an upload fails, the claim still exists: show which file failed and let the user retry from the detail page.

### B4. Claim detail (claimant view)
- Header: claim number, status chip, policy, type, market, amounts that are set (claimed / settled), rejection reason if rejected.
- **Status history** timeline (from `/history`), oldest first, with who and note.
- **Open information request** (if any): the officer's question, a text box for the response, and a file picker. On submit: **upload the selected files first** (linked to the request), then send the text response. If an upload fails, don't send the response; show the error.
- **Documents** list: filename, uploaded date, linked request (if any), download button. Download via `HttpClient` as a blob (a plain link wouldn't send the token), then save with the original filename.
- "Upload more documents" while the claim isn't closed.
- Client-side checks before upload: max 10 MB, JPEG/PNG/PDF only (the backend still checks; this just gives faster feedback).

### B5. Formatting
- Money: amount + currency code with correct decimals for that currency (e.g. IDR and VND usually without decimals) — use Angular's currency formatting.
- Dates: local date format.

## Part C — Tests
Keep it light — the backend carries most of the tests:
- Interceptor adds the header; a 401 logs out.
- Role guard blocks a claimant from an officer route.
- `ng build` (production) passes with no errors.
Tell me the test count.

## Done when
- With all three services running and `npm start`: register a new claimant with an unregistered seeded policy, log in, report an incident with an attached photo, see it in My claims. In Swagger, as a MY officer, assign the claim and request info. Back in the app, the claimant sees the question, answers with a PDF, and sees both documents and the full history.
- Officer/manager logins land on the placeholder page without errors.

## Report back
- Angular / Material / Node versions
- Commit list, test count, `ng build` result
- New libraries and what each is for
- Any backend changes (e.g. OpenAPI annotations) and why
- **`frontend/README.md`** content summary. That README must explain, in plain English for someone new to Angular: what each folder is for; how a request travels from a component → generated client → interceptor → proxy → backend; how login state and guards work; how to regenerate the API client.
- Manual test steps
