import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, switchMap } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { ClaimControllerService, ClaimSummary, ClaimSummaryStatusEnum } from '../../api/claims';
import { ErrorService } from '../../core/error.service';
import { MARKETS } from '../../shared/markets';
import { MoneyPipe } from '../../shared/money.pipe';
import { StatusChip } from '../../shared/status-chip';

type Status = ClaimSummaryStatusEnum;

/** Every claim in every market, newest first, 20 per page (GET /api/claims). Rows open the read-only staff detail. */
@Component({
  selector: 'app-all-claims',
  imports: [
    RouterLink, DatePipe, TitleCasePipe, MatCardModule, MatFormFieldModule, MatPaginatorModule, MatProgressBarModule,
    MatSelectModule, MatTableModule, MoneyPipe, StatusChip,
  ],
  templateUrl: './all-claims.html',
  styles: `
    .filters { margin-bottom: 4px; }
    .filters mat-form-field { width: 200px; }
    tr.mat-mdc-row { cursor: pointer; }
    .claim-link { font-weight: 600; text-decoration: none; }
    .claim-link:hover { text-decoration: underline; }
  `,
})
export class AllClaims {
  private readonly api = inject(ClaimControllerService);
  private readonly errors = inject(ErrorService);
  private readonly router = inject(Router);

  readonly columns = ['claimNumber', 'market', 'type', 'status', 'claimantName', 'officer', 'claimedAmount', 'reserveAmount', 'createdAt'];
  readonly statuses = Object.values(ClaimSummaryStatusEnum);
  readonly markets = MARKETS;
  readonly pageSize = 20;

  readonly status = signal<Status | null>(null);
  readonly market = signal<string | null>(null);
  readonly page = signal(0);

  // One object for "what to load"; any change re-fetches, and switchMap drops answers for older choices.
  private readonly query = computed(() => ({ status: this.status(), market: this.market(), page: this.page() }));
  readonly result = toSignal(
    toObservable(this.query).pipe(
      switchMap((q) =>
        this.api
          .all(q.status ?? undefined, q.market ?? undefined, q.page, this.pageSize)
          .pipe(catchError((e) => (this.errors.show(e), EMPTY))),
      ),
    ),
  );

  /** A new filter starts again at the first page. */
  setStatus(s: Status | null): void {
    this.status.set(s);
    this.page.set(0);
  }

  setMarket(m: string | null): void {
    this.market.set(m);
    this.page.set(0);
  }

  onPage(e: PageEvent): void {
    this.page.set(e.pageIndex);
  }

  open(c: ClaimSummary): void {
    this.router.navigate(['/manager/claims', c.id]);
  }
}
