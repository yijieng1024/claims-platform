import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** Icon per status (FRONTEND-DESIGN.md §2). Status is never shown by colour alone: always icon + text. */
const ICONS: Record<string, string> = {
  SUBMITTED: 'inbox',
  UNDER_REVIEW: 'search',
  INFO_REQUESTED: 'help_outline',
  ASSESSED: 'fact_check',
  SETTLED: 'check_circle',
  REJECTED: 'cancel',
};

/** Claim status as a pill with an icon. A styled <span> is enough; no Material chip behaviour is needed. */
@Component({
  selector: 'app-status-chip',
  imports: [MatIconModule],
  template: `
    <span class="chip" [class]="status()">
      <mat-icon aria-hidden="true">{{ icon() }}</mat-icon>{{ label() }}
    </span>
  `,
  styles: `
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 3px 10px 3px 7px;
      border-radius: var(--radius-pill);
      font-size: 12px;
      font-weight: 600;
      white-space: nowrap;
      line-height: 18px;
    }
    mat-icon {
      font-size: 15px;
      width: 15px;
      height: 15px;
    }
    /* colours from the §2 status table; UNDER_REVIEW and ASSESSED share one, the icon tells them apart */
    .SUBMITTED { background: var(--surface-2); color: var(--text-muted); }
    .UNDER_REVIEW, .ASSESSED { background: var(--brand-tint); color: var(--brand); }
    .INFO_REQUESTED { background: var(--warning-soft); color: var(--warning); }
    .SETTLED { background: var(--positive-soft); color: var(--positive); }
    .REJECTED { background: var(--negative-soft); color: var(--negative); }
  `,
})
export class StatusChip {
  readonly status = input<string | undefined>();
  readonly icon = computed(() => ICONS[this.status() ?? ''] ?? 'circle');
  // "INFO_REQUESTED" -> "Info requested"
  readonly label = computed(() => {
    const s = (this.status() ?? '').replace(/_/g, ' ').toLowerCase();
    return s.charAt(0).toUpperCase() + s.slice(1);
  });
}
