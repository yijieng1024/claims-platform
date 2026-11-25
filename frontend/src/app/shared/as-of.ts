import { Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';

/**
 * "Includes changes up to …" under a dashboard (design §3, Q21). Reporting is updated from events, so it can
 * be a few seconds behind claims-service; `asOf` is when the newest claim change it has seen happened.
 */
@Component({
  selector: 'app-as-of',
  imports: [DatePipe],
  template: `
    <p class="muted as-of">
      @if (asOf(); as asOf) {
        Includes changes up to <span class="num">{{ asOf | date: 'medium' }}</span>
      } @else {
        No claim changes yet
      }
    </p>
  `,
  styles: `.as-of { margin: 0; font-size: 12.5px; }`,
})
export class AsOf {
  readonly asOf = input<string | null | undefined>();
}
