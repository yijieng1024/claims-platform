import { Component, computed, input } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { HistoryEntry } from '../api/claims';
import { StatusChip } from './status-chip';

/** Remaining steps per current status, following the state machine's normal path (design §5). */
const UPCOMING: Record<string, string[]> = {
  SUBMITTED: ['Under review', 'Assessed', 'Decision'],
  UNDER_REVIEW: ['Assessed', 'Decision'],
  INFO_REQUESTED: ['Under review', 'Assessed', 'Decision'],
  ASSESSED: ['Decision'],
  // SETTLED and REJECTED are final: nothing left
};

/**
 * Horizontal claim timeline (FRONTEND-DESIGN.md §7), used on the claimant and the staff claim detail.
 * Finished steps come from the status history (oldest first, the backend's order); the steps still ahead
 * on the normal path are shown greyed out after them, so the timeline covers the whole journey.
 * Usage: <app-timeline [history]="history" [status]="claim.status" />
 */
@Component({
  selector: 'app-timeline',
  imports: [DatePipe, TitleCasePipe, StatusChip],
  template: `
    <!-- all steps always fit the card width: columns shrink and wrap, no sideways scrolling -->
    <div aria-label="Claim timeline" role="group">
      <ol class="timeline">
        <!-- steps that happened, with when and by whom; each shows the status the claim moved to -->
        @for (h of history(); track $index; let last = $last) {
          <li class="done" [class.current]="last">
            <span class="dot" aria-hidden="true"></span>
            <app-status-chip [status]="h.toStatus" />
            <div class="when muted num">{{ h.changedAt | date: 'medium' }}</div>
            <div class="who muted">by {{ h.changedByName }} ({{ h.changedByRole | titlecase }})</div>
            @if (h.note) {
              <div class="note">{{ h.note }}</div>
            }
          </li>
        }
        <!-- steps still ahead, greyed out with dashed dots (the non-colour signal) -->
        @for (step of upcoming(); track step) {
          <li class="upcoming">
            <span class="dot" aria-hidden="true"></span>
            <span class="step-label">{{ step }}</span>
            <div class="when muted">{{ step === 'Decision' ? 'Settled or rejected' : 'Not yet' }}</div>
          </li>
        }
      </ol>
    </div>
  `,
  styleUrl: './timeline.scss',
})
export class Timeline {
  readonly history = input.required<HistoryEntry[]>();
  readonly status = input.required<string>();
  readonly upcoming = computed(() => UPCOMING[this.status()] ?? []);
}
