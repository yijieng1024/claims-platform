import { Component, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Performance, ReportControllerService, Workload } from '../../api/reporting';
import { AuthService } from '../../core/auth.service';
import { ErrorService } from '../../core/error.service';
import { AsOf } from '../../shared/as-of';
import { PerformanceTable } from '../staff/performance-table';
import { WorkloadTable } from '../staff/workload-table';

/** The officer's team = the officers of their market (Q1). The backend limits both reports to that market. */
@Component({
  selector: 'app-team',
  imports: [MatCardModule, MatProgressBarModule, AsOf, PerformanceTable, WorkloadTable],
  template: `
    <div class="page wide">
      <h1>Team · {{ auth.user()?.market }}</h1>

      <mat-card appearance="outlined" class="table-card">
        <div class="card-body section-head">
          <h3>Workload</h3>
          <app-as-of [asOf]="workload()?.asOf" />
        </div>
        @if (workload(); as w) {
          <app-workload-table [rows]="w.officers" />
        } @else {
          <mat-progress-bar mode="indeterminate" />
        }
      </mat-card>

      <mat-card appearance="outlined" class="table-card">
        <div class="card-body section-head">
          <h3>Performance</h3>
          <app-as-of [asOf]="performance()?.asOf" />
        </div>
        @if (performance(); as p) {
          <app-performance-table [rows]="p.officers" />
        } @else {
          <mat-progress-bar mode="indeterminate" />
        }
      </mat-card>
    </div>
  `,
  styles: `
    mat-card { margin-bottom: 16px; }
    .section-head { padding-bottom: 12px; }
    .section-head h3 { margin: 0 0 2px; }
  `,
})
export class Team {
  protected readonly auth = inject(AuthService);
  private readonly errors = inject(ErrorService);

  readonly workload = signal<Workload | null>(null);
  readonly performance = signal<Performance | null>(null);

  constructor() {
    const reports = inject(ReportControllerService);
    reports.workload().subscribe({ next: (w) => this.workload.set(w), error: (e) => this.errors.show(e) });
    reports.performance().subscribe({ next: (p) => this.performance.set(p), error: (e) => this.errors.show(e) });
  }
}
