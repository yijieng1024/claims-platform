# Claims Platform — frontend

Angular 22 single-page app for claimants, officers and managers. Angular Material for UI components.
Versions: Angular 22.3, Angular Material 22.3, TypeScript 6.0, built and tested with Node 26.4.
(Angular 22 needs Node `^22.22.3 || ^24.15.0 || >=26`.)

## Run it

```bash
npm install
npm start          # http://localhost:4200, with the dev proxy (see below)
npm test           # unit tests (Vitest, Angular's default test runner)
npm run build      # production build into dist/
```

The three backend services must be running for the app to work (see the root README). Building and
testing do **not** need them.

**In Docker** (`docker compose up --build` from the repo root), `Dockerfile` builds the same production
bundle with Node and serves it with **nginx** on port 4200. `nginx.conf` does in production what
`proxy.conf.json` does in development: it forwards `/api/auth`, `/api/claims`, `/api/policies`,
`/api/reports` and `/api/notifications` to the services, so the browser still talks to one origin
(no CORS). Any other path that isn't a file returns `index.html`, so Angular's router can handle URLs
like `/claims/123` on a page reload.

## Folder structure

```
src/
  main.ts               starts the app: bootstrapApplication(App, appConfig)
  index.html
  styles.scss           global styles: loads the files below, page layout, helper classes, motion
  styles/               the design system (see "Styling" below)
    _tokens.scss        every colour, radius and shadow as a CSS variable
    _theme-colors.scss  GENERATED Material palette from the brand navy
    _theme.scss         the Material theme + supported overrides (shape, colours, sizes)
    _material-internals.scss  the few rules that must reach into Material internals
  app/
    app.ts / app.html   the shell: floating top nav + <router-outlet> (where the current page appears)
    app.config.ts       app-wide setup: router, HttpClient + interceptor, API base path, date adapter
    app.routes.ts       URL → page, and which role may open it
    core/               app-wide singletons: AuthService, HTTP interceptor, route guards, ErrorService,
                        NotificationService (polling)
    shared/             small reusable pieces: logo, status chip, money pipe, file picker, date helpers,
                        timeline, notification bell, market filter, "Includes changes up to …" line
    api/                GENERATED API clients — never edit by hand (see "API client" below)
      auth/ claims/ reporting/
    features/           one folder per area of the app; each page is one component
      auth/             login, register
      claimant/         my claims, new claim, claim detail
      officer/          queue, my (assigned) claims, team
      manager/          dashboard, all claims, team performance
      staff/            shared by officer and manager: staff claim detail, its action dialog and
                        action rules (staff-actions.ts), workload and performance tables
```

Each page is a **standalone component**: a TypeScript class (state + actions), an HTML template and
optionally a stylesheet. A component lists what it uses in `imports: [...]` (no NgModules).

Page state is kept in **signals** (`signal()`, `computed()`): when a signal changes, Angular updates
the parts of the page that read it. The app runs without zone.js (Angular's current default), so
signals are how Angular knows something changed.

Forms use **reactive forms** (`FormBuilder`): the form is defined in the class, validators included,
and the template binds to it with `formControlName`.

## How a request travels

Example: the claimant opens "My claims".

1. **Component** — `MyClaims` calls `claimsApi.my()`. `claimsApi` is the generated
   `ClaimControllerService`, given to the component by Angular's dependency injection (`inject()`).
2. **Generated client** — builds the request `GET /api/claims/my` with Angular's `HttpClient`.
   Its base path is set to `''` in `app.config.ts`, so the URL stays relative to the page.
3. **Interceptor** (`core/auth.interceptor.ts`) — every `HttpClient` request passes through it. For
   `/api/**` it adds `Authorization: Bearer <token>`. If the answer is **401** while logged in, the
   token has expired or is invalid: it logs out and shows the login page with "Session expired".
4. **Dev proxy** (`proxy.conf.json`, used by `npm start`) — the browser sends the request to
   `localhost:4200`; the Angular dev server forwards it by path prefix:

   | Path | Service |
   |---|---|
   | `/api/auth` | auth-service `:8081` |
   | `/api/claims`, `/api/policies` | claims-service `:8082` |
   | `/api/reports`, `/api/notifications` | reporting-service `:8083` |

   The browser only ever talks to one origin, so there is no CORS setup and no API gateway (design §2).
5. **Backend** — checks the token itself and answers with JSON, or a **ProblemDetail** error.
6. Back in the component, `subscribe({ next, error })` receives the result. Errors go to
   `ErrorService.show(err, form?)`: validation errors appear under the matching form fields, other
   errors in a snackbar using the ProblemDetail `detail`. 503 (or no answer) shows
   "A service is temporarily unavailable, please try again."

## Login state and guards

- `AuthService` (`core/auth.service.ts`) holds the session: token, expiry and user (name, role).
  The user is exposed as a **signal**, so the top bar and guards react when you log in or out.
- The session is saved in **sessionStorage**: it survives a page refresh and is gone when the tab
  closes. Trade-off: any script on the page can read it, so an XSS bug would leak the token. In
  production an httpOnly cookie (unreadable by scripts) would be the better choice; out of scope here.
- On app start, an expired stored session is discarded.
- **Route guards** (`core/role.guard.ts`) run before a page opens. `roleGuard('CLAIMANT')` lets only
  claimants in. If you're not logged in, you're sent to the login page. If you have another role,
  you're sent to your own start page. Guards only hide pages; the backend checks every request.
- After login: claimant → My claims (`/claims`); officer → Queue (`/officer`); manager → Dashboard (`/manager`).

**Routes**

| Path | Who | Page |
|---|---|---|
| `/claims`, `/claims/new`, `/claims/:id` | claimant | my claims, report an incident, claim detail |
| `/officer` | officer | queue (unassigned claims of my market, oldest first) |
| `/officer/claims`, `/officer/claims/:id` | officer | my open claims, staff claim detail with actions |
| `/officer/team` | officer | workload and performance of my market |
| `/manager` | manager | dashboard: exposure cards, outstanding, team workload |
| `/manager/claims`, `/manager/claims/:id` | manager | all claims (paginated, filters), staff claim detail read-only |
| `/manager/performance` | manager | team performance with market filter |

The officer and manager claim detail is one component (`features/staff/claim-detail`). Which action
buttons it shows comes from `allowedActions()` in `features/staff/staff-actions.ts`, a copy of the
backend's state machine: only the assigned officer acts, and only what the claim's status allows;
managers get none. Hiding buttons is convenience only: the backend checks every action again
(404/403/409) and its error is shown if they disagree.

## Notifications (polling)

The bell in the top nav shows the unread count and opens a panel (a `mat-menu`, so it is a glass
overlay that closes on Escape). `core/notification.service.ts` re-reads `GET /api/notifications`
every **15 s**, but only while someone is logged in (design §3):

```ts
toObservable(auth.isLoggedIn).pipe(            // login signal → stream of true/false
  switchMap((loggedIn) => loggedIn
    ? timer(0, 15_000).pipe(switchMap(() => api.mine()))   // now, then every 15 s
    : of([])),                                              // logged out: empty list
)
```

`switchMap` unsubscribes the previous inner stream whenever the login state changes, so logging out
(or a 401, which logs out) stops the timer. A failed poll is ignored and the next tick tries again,
so a service being down doesn't show an error every 15 s. Clicking a notification marks it read
(`POST /api/notifications/{id}/read`, and locally at once so the badge updates) and opens the claim:
claimant → `/claims/:id`, officer/manager → their staff claim detail (`AuthService.claimUrl`).
Server push (SSE/WebSocket) would replace the polling; out of scope.

## Styling (design system)

The look follows [`docs/FRONTEND-DESIGN.md`](../docs/FRONTEND-DESIGN.md): Angular Material components
re-skinned with our own tokens. Light mode only.

**Where things live**

| File | What it does |
|---|---|
| `src/styles/_tokens.scss` | All colours (`--brand`, `--surface`, `--text-muted`, …), radii, shadows and the font stack as CSS variables, plus the `glass` mixin. **The only place colours are written.** |
| `src/styles/_theme-colors.scss` | Generated by `ng generate @angular/material:theme-color --primary-color=#042e61 --error-color=#b42318`: Material's tonal palette for the navy. Don't edit; regenerate. |
| `src/styles/_theme.scss` | `mat.theme(...)` with that palette and the system font; then `mat.theme-overrides` pins Material's system colours (`--mat-sys-primary` and friends) to our tokens; then one `*-overrides` mixin per component for shapes and sizes (pill buttons, 24px cards, 12px fields, 16px menus). |
| `src/styles/_material-internals.scss` | The few rules Material has no token for: field background and focus ring, table header fill and row hover, table cell padding, the blur on glass menus. Check this file after upgrading Material. |
| `src/styles.scss` | Loads the above; body font, dot-grid canvas, headings, `.page` layout, helper classes, entrance animation, notification panel content (it renders in an overlay outside its component). |

Why the palette alone isn't enough: Material's generated palette puts "primary" at a lighter tone
(`#3f5e94`) than our navy, so the important system colours are pinned to the tokens directly.

**Styling a new component**

- Colours: always `var(--token)`, e.g. `color: var(--text-muted)`. Never a hex value; if a colour is
  missing, add a token.
- Material components pick up the theme on their own. Buttons use Material's current syntax:
  - `matButton="filled"`: the **one primary action** of a view
  - `matButton="tonal"`: secondary actions
  - `matButton`: low-emphasis text buttons such as "Cancel"
  - `matButton="tonal" class="btn-destructive"`: destructive actions (always behind a confirm dialog)
- Content sits in flat cards: `<mat-card appearance="outlined">`, padded with `<div class="card-body">`.
  A card holding a table gets `class="table-card"`.
- Form fields: `<mat-form-field appearance="outline">`.
- Numbers: `class="num"` (tabular figures so columns line up); numeric table columns: `class="num-col"`
  on the header cell and the cells (right-aligned).
- Page wrapper: `<div class="page">` (960px: forms, details) or `<div class="page wide">` (1152px: lists).
  Direct children of `.page` fade in, staggered, unless the user asked for reduced motion.
- Glass (`@include tokens.glass`) is only for floating things (nav, menus, dialogs), never body content.
- Claim status: `<app-status-chip [status]="claim.status" />` (icon + text, never colour alone).
- Claim timeline: `<app-timeline [history]="history" [status]="claim.status" />` (same on claimant and staff pages).
- Market filter (segmented control): `<app-market-filter [(market)]="market" />`, `null` = all markets.
- Dashboards: `<app-as-of [asOf]="report.asOf" />` prints "Includes changes up to …".

`appearance` is set in each template, not as an app-wide default: providing those defaults in
`app.config.ts` would pull the form-field and card code into the first download instead of the lazily
loaded pages.

**Bundle budget.** The production build warns when the initial download passes 700 kB (the scaffold
default was 500 kB; the user menu needs Material's overlay code on every page). The hard limit is 1 MB.

## API client (generated)

The TypeScript client in `src/app/api/` is generated from each service's OpenAPI description, so the
frontend's types always match the backend's contract (design Q5). The specs are committed in
`openapi/{auth,claims,reporting}.json`:

- Building never needs the backend running.
- A backend contract change shows up as a diff in `openapi/*.json` in code review, and as compile
  errors where the frontend used something that changed.

Two scripts:

```bash
npm run api:export     # services must be running: saves each /v3/api-docs into openapi/*.json
npm run api:generate   # regenerates src/app/api/* from openapi/*.json
```

`api:generate` uses **openapi-generator** (generator `typescript-angular`, settings in
`openapitools.json`). It runs a Java program, so it needs **Java 11+ on the PATH** (the JDK 25 used
for the backend works). If your `java` on the PATH is older, put the JDK first for that command, e.g.
`PATH="$JAVA_HOME/bin:$PATH" npm run api:generate`.

If the generated client is wrong, fix the backend's OpenAPI annotations and regenerate. Never edit
the generated files.

**Required fields (Q28).** The backend marks every response field required unless the Java field is
`@Nullable` (rule in `libs/web-common`, `RequiredUnlessNullable`). So in TypeScript `claim.claimNumber`
is a plain `string` and needs no `!`, while `claim.reserveAmount` is `number | null | undefined`
because it really can be missing (before assessment).

## Tests

`npm test` runs the unit tests with Vitest:

- `core/auth.interceptor.spec.ts` — adds the token to `/api` calls only; a 401 while logged in logs out;
  a 401 from a failed login does not.
- `core/role.guard.spec.ts` — a claimant is blocked from an officer route; an officer is let in; a
  logged-out visitor goes to login.
- `app.routes.spec.ts` — with the real route table: an officer opening `/manager/claims` ends up on
  their queue; each role starts on its own home page.
- `core/notification.service.spec.ts` — polls now and every 15 s while logged in; stops and clears
  after logout (fake timers, so the test runs instantly).
- `features/staff/staff-actions.spec.ts` — which actions per status and assignment (e.g. Settle only
  when `ASSESSED` and assigned to me; nothing for a manager).
- `features/staff/claim-detail.spec.ts` — renders the staff claim detail with a fake backend and checks
  the visible action buttons for the assigned officer, another officer and a manager.

Most behaviour is tested in the backend; the frontend tests cover the security plumbing.

## Libraries

| Library | Why |
|---|---|
| `@angular/*` | the framework (router, forms, HttpClient) |
| `@angular/material` + `@angular/cdk` | ready-made components: tables, forms, datepicker, menu, dialog, select, paginator, snackbar (design Q4), restyled by our theme |
| `@openapitools/openapi-generator-cli` (dev) | generates the API client from the OpenAPI specs (design Q5) |
| `vitest`, `jsdom` (dev) | Angular's default unit test runner and the fake browser it runs in |

## Formatting

- Money: `{{ amount | money: currency }}` → `MYR 1,234.50`. Decimals per currency come from Angular's
  currency data (VND 0, others 2), except IDR, which is shown without decimals as is usual in practice.
  The new-claim form uses the same rule for the claimed amount.
- Dates: Angular's `date` pipe, in the browser's time zone. Plain dates from the backend (`YYYY-MM-DD`)
  are read as local dates, so they never shift by a day.
