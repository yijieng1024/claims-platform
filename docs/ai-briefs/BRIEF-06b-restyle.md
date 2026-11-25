# Brief 06b — Restyle the frontend to the design system

## Context
Read: `docs/DESIGN.md` **v0.9** (new: Q27–Q29) and the new **`docs/FRONTEND-DESIGN.md`** — the visual design system for the frontend. Also `frontend/README.md` from brief 6a.

Plan:
1–5. Backend ✅
6a. Angular foundation + claimant ✅
6b. **Restyle to the design system** ← this brief (only this)
6c. Contract fix (required fields), officer screens, manager dashboard, notifications — next brief
7. README, DECISIONS.md, compose for all services, clean-start check

**Only restyle.** Don't build officer/manager screens or notifications, and don't change the API contract in this brief.

Same rules: small meaningful commits, no real company branding, don't edit `AI_JOURNAL.md`, `docs/DESIGN.md` or `docs/FRONTEND-DESIGN.md`, explain every new library, **report straight away**, don't silently change the design — ask. I'm new to Angular: keep it conventional and comment the *why*.

## Part 0 — Housekeeping (one commit each)
1. Brief 6a changed backend OpenAPI config but didn't re-run the backend tests. Run `./mvnw clean verify` and report the result (fix anything that fails).
2. Remove the stray `// ponytail:` comment in `app.routes.ts`.

## Part A — Restyle (Q29)
Apply `docs/FRONTEND-DESIGN.md` to everything built in 6a:
- **Tokens** file with the colours from §2, used everywhere instead of hardcoded values.
- **Custom Material theme** with the primary palette generated from the navy `#042e61`; system font stack (§3).
- **Shape overrides**: pill buttons, 12px form fields, 24px cards, 16px menus/dialogs (§6).
- **Dot-grid canvas** on the page background.
- **Glass floating top nav** (§4, §5) with the new logo: white bold "C" on a navy rounded square + "Claims Platform". Role-based links and the user menu as now.
- **Flat cards** for content sections and tables (§5, §7); table styling per §7.
- **Status chips** with icon + text and the colours in the §2 table.
- **Buttons**: one primary per view; secondary and destructive styles per §7.
- **Inputs**: canvas background, hairline border, brand focus ring (§7, §8).
- **Login / register**: one hero card with the large soft shadow.
- **Numbers**: `tabular-nums` on amounts, claim numbers, dates.
- **Motion**: fade-in-up entrance, only under `prefers-reduced-motion: no-preference`.
- The officer/manager placeholder page just needs to look consistent (it's replaced in 6c).

Prefer Material's supported override mechanisms (theme mixins, `*-overrides` mixins, `--mat-sys-*` variables). Put any unavoidable deep selectors into Material internals in one file, with a comment saying why.

## Part B — Checks
- Existing frontend tests still pass; `ng build` (production) with no errors or warnings.
- Check contrast of `--text-muted` on `--surface-2` and on `--surface` (≥ 4.5:1); adjust the token if needed and tell me.
- Update `frontend/README.md`: where the tokens and theme live, and how to style a new component with them.

## Done when
- Every 6a screen (login, register, my claims, new claim, claim detail, placeholder) follows `docs/FRONTEND-DESIGN.md`.
- The claimant flow from 6a still works end to end.

## Report back
- Backend `mvnw clean verify` result, frontend test count, `ng build` result
- Commit list
- How the theme and overrides were done; anything from `FRONTEND-DESIGN.md` you couldn't follow exactly, and why
- Any token you changed (e.g. for contrast)
- New libraries (if any) and what each is for
- A short description or screenshots of each screen
