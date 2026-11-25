# Claims Platform — Frontend Design System

The visual language for the Claims Platform frontend: Apple-inspired glassmorphism over a calm, mostly neutral canvas, with a deep navy brand colour. Adapted from the design system of my own earlier project (PickWise) and translated from Tailwind/shadcn to **Angular Material**.

**Light mode only.** Dark mode is out of scope; because every colour is a CSS variable, adding it later means adding a second set of values, not touching components.

All styling goes through the **tokens** below (CSS custom properties in `src/styles/tokens.scss`, or the project's equivalent). Components never use hardcoded colour values.

No real insurer's name, logo or brand colours anywhere.

---

## 1. How it maps onto Angular Material

- Keep Angular Material components (table, form field, datepicker, dialog, snackbar, menu, chips).
- Build a **custom Material theme** whose primary palette is generated from the brand navy `#042e61` (Angular Material ships a schematic to generate a theme palette from a hex colour). Typography uses the system font stack below.
- Override Material's system tokens / component overrides for **shape** (pill buttons, 12px fields, 24px cards, 16px menus) and **surface colours** so Material components sit on our canvas and surfaces.
- Custom pieces (top nav, cards, status chips, KPI cards) are plain components styled with the tokens.
- Prefer Material's supported override mechanisms (theme mixins / `*-overrides` mixins / `--mat-sys-*` variables) over deep CSS selectors into Material internals. Where a deep selector is unavoidable, keep it in one file and comment why.

---

## 2. Colours

| Token | Value | Use |
|---|---|---|
| `--bg` | `#fbfbfd` | Page canvas; input field background |
| `--surface` | `rgba(255,255,255,0.85)` | Cards and panels (slightly translucent so the dot grid shows faintly) |
| `--surface-solid` | `#ffffff` | Opaque fallback / where translucency looks wrong (e.g. inside dialogs) |
| `--surface-2` | `#f5f5f7` | Nested fills: chips, tab rails, hover fills, table header |
| `--brand` | `#042e61` | Primary actions, logo, active nav item, links |
| `--brand-light` | `#0b4a8f` | Lighter end of the logo gradient |
| `--brand-tint` | `#eaf1fa` | Selected pills, soft badges, focus ring |
| `--positive` | `#17754a` | Success, settled (darkened from `#1a7f4f` so 12px chip text on its 10% background reaches 4.96:1) |
| `--negative` | `#b42318` | Errors, rejected, destructive actions |
| `--warning` | `#b54708` | Needs attention (information requested). Text-safe on white (≥ 4.5:1) |
| `--text` | `#1d1d1f` | Body text |
| `--text-muted` | `#6e6e73` | Secondary text, captions (check ≥ 4.5:1 on its surface) |
| `--line` | `rgba(29,29,31,0.07)` | Hairline borders, table dividers |
| `--glass` | `rgba(255,255,255,0.62)` | Glass surface fill |
| `--glass-edge` | `rgba(255,255,255,0.55)` | Glass edge highlight / inner border |
| `--shadow` | `rgba(4,46,97,0.08)` | Soft ambient shadows |
| `--dot` | `rgb(15 23 42 / 0.14)` | Page dot grid |

Avoid `--text-muted` on `--brand-tint` (4.46:1, below 4.5:1).

Status colours are used at **low alpha for backgrounds** (about 10%) with the full colour for text/icon.

**Dot grid:** a 1.35px radial-gradient dot on a 26px grid, on `body`. CSS only. It sits under the canvas, never under text-heavy surfaces (cards cover it).

### Claim status chips
Every status chip shows **an icon and text**, never colour alone.

| Status | Background / text | Icon idea |
|---|---|---|
| `SUBMITTED` | `--surface-2` / `--text-muted` | inbox |
| `UNDER_REVIEW` | `--brand-tint` / `--brand` | search |
| `INFO_REQUESTED` | `--warning` @10% / `--warning` | help / question |
| `ASSESSED` | `--brand-tint` / `--brand` | fact check |
| `SETTLED` | `--positive` @10% / `--positive` | check circle |
| `REJECTED` | `--negative` @10% / `--negative` | cancel |

`UNDER_REVIEW` and `ASSESSED` share a colour; the icon and label tell them apart.

---

## 3. Typography

- **Font stack:** system native — `-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif` (SF Pro on Apple, Segoe UI on Windows). No web font download.
- **Headings:** bold, tight letter-spacing. Sizes 30 / 24 / 18 / 16px (page title → card header).
- **Body & controls:** 13–13.5px for controls and table cells; 11–12.5px for captions and chips. Secondary copy uses `--text-muted`.
- **Numbers:** every amount, claim number, date column and count uses `font-variant-numeric: tabular-nums` so columns line up.

---

## 4. Layout

- **Top navigation:** a floating **glass island**, sticky 16px from the top, centred, max width 1152px. Contains the logo, role-based links, the notification bell and the user menu.
- **Page container:** top padding to clear the nav; horizontal padding 16px (24px on wider screens). Width by density: forms and detail pages ~768–960px; lists and dashboards up to ~1280px.
- **Logo:** a rounded square with a white bold **"C"** on a navy gradient (`--brand` to a slightly lighter navy), followed by "Claims Platform".

### Motion
- Entrance: fade-in-up (12px rise, 0.5s, ease-out). Card grids stagger by ~60ms.
- All animation only under `@media (prefers-reduced-motion: no-preference)`.
- No decorative motion beyond this.

---

## 5. Elevation — three levels only

1. **Canvas** (`--bg`): page background, input backgrounds.
2. **Flat card** (`--surface` + 1px `--line` border, radius 24px): all body content — tables, forms, claim detail sections, KPI cards. Large soft shadow (`0 24px 72px var(--shadow)`) only for a single hero panel per page if any (e.g. the login card).
3. **Glass overlay**: only floating things — top nav, notification panel, user menu, dialogs.
   Recipe: `background: var(--glass)`, `backdrop-filter: blur(20px) saturate(180%)`, `1px solid var(--glass-edge)`, `box-shadow: 0 12px 40px var(--shadow)`.

---

## 6. Shapes

| Element | Radius |
|---|---|
| Buttons, chips, pills, tab rails | fully rounded (999px) |
| Cards, panels | 24px |
| Inputs, selects, date fields | 12px |
| Menus, popovers, dialogs | 16–20px |

---

## 7. Components

- **Primary button:** `--brand` background, white text, pill. Hover: opacity 0.9. Disabled: opacity 0.6. **One primary action per view.**
- **Secondary button:** `--brand-tint` background, `--brand` text, pill.
- **Destructive button** (e.g. Reject): `--negative` text on `--negative` @10%, pill. Always behind a confirm dialog.
- **Inputs:** `--bg` background, `--line` border, 12px radius, ~46px tall. Focus: `box-shadow: 0 0 0 3px var(--brand-tint)` plus border in `--brand`. Never remove focus indication without this replacement.
- **Form errors:** field validation errors appear under the field (`--negative`). Login failure ("Invalid email or password") is shown inline and marks **both** fields, because the backend deliberately doesn't say which one was wrong; editing either field clears both. Other errors (e.g. a service down) use the snackbar.
- **Required fields:** the required asterisk is `--negative` on every form; Material's built-in required attribute still tells screen readers.
- **Password fields:** an eye toggle (`type="button"`, never submits) switching between hidden and visible text, with `aria-label` "Show password" / "Hide password". Same on login and register.
- **Tables:** inside a flat card; header row on `--surface-2`, 12px muted labels; rows separated by `--line` hairlines; row hover `--surface-2`; numeric columns right-aligned with `tabular-nums`.
- **KPI cards** (dashboard): flat card, small muted label, large tabular number, optional caption line underneath.
- **Tab rails / segmented filters:** `--surface-2` pill container with the active segment on `--surface-solid` and a soft shadow.
- **Timeline** (claim detail): horizontal, directly under the claim number and status, oldest step on the left. Finished steps: status chip, date/time, who, note; joined by a solid `--brand` line; the current step has a filled dot with a soft ring. Upcoming steps (normal path from the state machine) in muted grey with **dashed** dots and labels — dashed is the non-colour signal. The final step is labelled "Settled or rejected". Each step ≥ 180px; scrolls sideways (keyboard-scrollable) when it doesn't fit. Used on both claimant and staff claim detail.
- **Empty states:** centred muted text + one primary action.
- **Snackbar:** keep Material's default dark snackbar (good contrast on any page).

### Accepted deviations (Material limitations)
- Primary button hover uses Material's hover overlay instead of 90% opacity (no supported way to swap it).
- Form fields are 48px tall (Material density steps are 48 or 44), not ~46px.
- Field and card `appearance` is set per template instead of app-wide, to keep Material code out of the first download.

---

## 8. Accessibility

- Body text contrast ≥ 4.5:1 against its surface (check `--text-muted` on `--surface-2`).
- Icon-only buttons have `aria-label`; disclosure controls (notification bell, user menu) set `aria-expanded`.
- Status is never shown by colour alone (icon + text).
- Visible focus on every interactive element (see inputs).

---

## 9. Don'ts

- **Don't** use any real insurer's name, logo or colours.
- **Don't** put glass on body content, or nest glass inside glass.
- **Don't** use third-party "liquid glass" packages.
- **Don't** add charts — dashboards are cards and tables.
- **Don't** hardcode colours in components; add a token instead.
- **Don't** use `outline: none` without the focus-ring replacement.
- **Don't** add dark mode in this scope.
