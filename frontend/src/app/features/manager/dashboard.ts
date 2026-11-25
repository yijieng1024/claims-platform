import { Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { DecimalPipe } from '@angular/common';
import { EMPTY, catchError, switchMap } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Exposure, Outstanding, ReportControllerService } from '../../api/reporting';
import { ErrorService } from '../../core/error.service';
import { AsOf } from '../../shared/as-of';
import { MarketFilter } from '../../shared/market-filter';
import { MoneyPipe } from '../../shared/money.pipe';
import { StatusChip } from '../../shared/status-chip';
import { WorkloadTable } from '../staff/workload-table';

/**
 * Manager start page: cards and tables, no charts (Q8). All numbers come from reporting-service, so they
 * can trail claims-service by a few seconds; "Includes changes up to …" says how fresh they are (design §3).
 */
@Component({
  selector: 'app-dashboard',
  imports: [DecimalPipe, MatCardModule, MatProgressBarModule, MatTableModule, AsOf, MarketFilter, MoneyPipe, StatusChip, WorkloadTable],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly reports = inject(ReportControllerService);
  private readonly errors = inject(ErrorService);

  readonly exposure = signal<Exposure | null>(null);
  readonly outstanding = signal<Outstanding | null>(null);

  /** Workload table filter; null = all markets. */
  readonly market = signal<string | null>(null);
  // Re-fetch whenever the filter changes. switchMap drops the answer for a filter the user already left,
  // so a slow response can't overwrite the table for the newer choice.
  readonly workload = toSignal(
    toObservable(this.market).pipe(
      switchMap((m) => this.reports.workload(m ?? undefined).pipe(catchError((e) => (this.errors.show(e), EMPTY)))),
    ),
  );

  constructor() {
    // Q24: always six currencies, in market order (MYR, SGD, THB, IDR, PHP, VND), zeros included
    this.reports.exposure().subscribe({ next: (e) => this.exposure.set(e), error: (e) => this.errors.show(e) });
    this.reports.outstanding().subscribe({ next: (o) => this.outstanding.set(o), error: (e) => this.errors.show(e) });
  }
}
