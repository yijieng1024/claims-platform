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

## Folder structure

```
src/
  main.ts               starts the app: bootstrapApplication(App, appConfig)
  index.html, styles.scss
  app/
    app.ts / app.html   the shell: top bar + <router-outlet> (where the current page appears)
    app.config.ts       app-wide setup: router, HttpClient + interceptor, API base path, date adapter
    app.routes.ts       URL → page, and which role may open it
    core/               app-wide singletons: AuthService, HTTP interceptor, route guards, ErrorService
    shared/             small reusable pieces: status chip, money pipe, file picker, date helpers
    api/                GENERATED API clients — never edit by hand (see "API client" below)
      auth/ claims/ reporting/
    features/           one folder per area of the app; each page is one component
      auth/             login, register
      claimant/         my claims, new claim, claim detail
      staff-home.ts     temporary landing page for officers and managers (replaced in brief 6b)
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
- After login: claimant → My claims (`/claims`); officer and manager → `/staff` (placeholder until 6b).

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

## Tests

`npm test` runs the unit tests with Vitest:

- `core/auth.interceptor.spec.ts` — adds the token to `/api` calls only; a 401 while logged in logs out;
  a 401 from a failed login does not.
- `core/role.guard.spec.ts` — a claimant is blocked from an officer route; an officer is let in; a
  logged-out visitor goes to login.

Most behaviour is tested in the backend; the frontend tests cover the security plumbing.

## Libraries

| Library | Why |
|---|---|
| `@angular/*` | the framework (router, forms, HttpClient) |
| `@angular/material` + `@angular/cdk` | ready-made components: tables, forms, datepicker, snackbar (design Q4) |
| `@openapitools/openapi-generator-cli` (dev) | generates the API client from the OpenAPI specs (design Q5) |
| `vitest`, `jsdom` (dev) | Angular's default unit test runner and the fake browser it runs in |

## Formatting

- Money: `{{ amount | money: currency }}` → `MYR 1,234.50`. Decimals per currency come from Angular's
  currency data (VND 0, others 2), except IDR, which is shown without decimals as is usual in practice.
  The new-claim form uses the same rule for the claimed amount.
- Dates: Angular's `date` pipe, in the browser's time zone. Plain dates from the backend (`YYYY-MM-DD`)
  are read as local dates, so they never shift by a day.
