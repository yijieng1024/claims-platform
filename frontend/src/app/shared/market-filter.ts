import { Component, model } from '@angular/core';
import { MARKETS } from './markets';

/**
 * Segmented control "All · MY · SG · ..." (FRONTEND-DESIGN.md §7 tab rail): a --surface-2 pill with the
 * active segment on --surface-solid. Plain buttons with aria-pressed, so it works with keyboard and screen readers.
 * Usage: <app-market-filter [(market)]="market" />   (null = all markets)
 */
@Component({
  selector: 'app-market-filter',
  template: `
    <div class="rail" role="group" aria-label="Market">
      <button type="button" [attr.aria-pressed]="market() === null" (click)="market.set(null)">All</button>
      @for (m of markets; track m) {
        <button type="button" [attr.aria-pressed]="market() === m" (click)="market.set(m)">{{ m }}</button>
      }
    </div>
  `,
  styles: `
    .rail {
      display: inline-flex;
      flex-wrap: wrap;
      gap: 2px;
      padding: 3px;
      border-radius: var(--radius-pill);
      background: var(--surface-2);
    }
    button {
      border: 0;
      padding: 6px 14px;
      border-radius: var(--radius-pill);
      background: transparent;
      color: var(--text-muted);
      font: inherit;
      font-weight: 600;
      font-size: 12.5px;
      cursor: pointer;
    }
    button:hover { color: var(--text); }
    button[aria-pressed='true'] {
      background: var(--surface-solid);
      color: var(--text);
      box-shadow: 0 1px 4px var(--shadow);
    }
    button:focus-visible { outline: 2px solid var(--brand); outline-offset: 1px; }
  `,
})
export class MarketFilter {
  readonly market = model<string | null>(null);
  readonly markets = MARKETS;
}
