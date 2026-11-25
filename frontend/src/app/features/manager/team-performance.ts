import { Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, switchMap } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ReportControllerService } from '../../api/reporting';
import { ErrorService } from '../../core/error.service';
import { AsOf } from '../../shared/as-of';
import { MarketFilter } from '../../shared/market-filter';
import { PerformanceTable } from '../staff/performance-table';

/** Performance of every officer, optionally one market (GET /api/reports/performance?market=). */
@Component({
  selector: 'app-team-performance',
  imports: [MatCardModule, MatProgressBarModule, AsOf, MarketFilter, PerformanceTable],
  template: `
    <div class="page wide">
      <div class="row head">
        <div>
          <h1>Team performance</h1>
          <app-as-of [asOf]="performance()?.asOf" />
        </div>
        <span class="spacer"></span>
        <app-market-filter [(market)]="market" />
      </div>
      <mat-card appearance="outlined" class="table-card">
        @if (performance(); as p) {
          <app-performance-table [rows]="p.officers" />
        } @else {
          <mat-progress-bar mode="indeterminate" />
        }
      </mat-card>
    </div>
  `,
  styles: `
    .head { margin-bottom: 16px; align-items: flex-end; }
    h1 { margin-bottom: 4px; }
  `,
})
export class TeamPerformance {
  private readonly reports = inject(ReportControllerService);
  private readonly errors = inject(ErrorService);

  readonly market = signal<string | null>(null);
  // re-fetch on every filter change; switchMap drops answers for a filter the user already left
  readonly performance = toSignal(
    toObservable(this.market).pipe(
      switchMap((m) => this.reports.performance(m ?? undefined).pipe(catchError((e) => (this.errors.show(e), EMPTY)))),
    ),
  );
}
