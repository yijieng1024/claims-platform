import { Component, computed, input } from '@angular/core';

/** Claim status as a coloured pill. A styled <span> is enough here; no Material chip behaviour is needed. */
@Component({
  selector: 'app-status-chip',
  template: `<span class="chip" [class]="status()">{{ label() }}</span>`,
  styles: `
    .chip {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 500;
      white-space: nowrap;
      background: #eceff1;
      color: #37474f;
    }
    .SUBMITTED { background: #e3f2fd; color: #0d47a1; }
    .UNDER_REVIEW { background: #ede7f6; color: #4527a0; }
    .INFO_REQUESTED { background: #fff3e0; color: #e65100; }
    .ASSESSED { background: #e0f7fa; color: #006064; }
    .SETTLED { background: #e8f5e9; color: #1b5e20; }
    .REJECTED { background: #ffebee; color: #b71c1c; }
  `,
})
export class StatusChip {
  readonly status = input<string | undefined>();
  // "INFO_REQUESTED" -> "Info requested"
  readonly label = computed(() => {
    const s = (this.status() ?? '').replace(/_/g, ' ').toLowerCase();
    return s.charAt(0).toUpperCase() + s.slice(1);
  });
}
