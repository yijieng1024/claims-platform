import { Component, inject, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ClaimControllerService, ClaimSummary } from '../../api/claims';
import { ErrorService } from '../../core/error.service';
import { MoneyPipe } from '../../shared/money.pipe';
import { StatusChip } from '../../shared/status-chip';

@Component({
  selector: 'app-my-claims',
  imports: [RouterLink, DatePipe, TitleCasePipe, MatButtonModule, MatTableModule, MatProgressBarModule, MoneyPipe, StatusChip],
  templateUrl: './my-claims.html',
  styles: `
    tr.mat-mdc-row { cursor: pointer; }
    tr.mat-mdc-row:hover { background: var(--mat-sys-surface-container); }
    .empty { text-align: center; padding: 48px 0; }
  `,
})
export class MyClaims {
  private readonly router = inject(Router);
  private readonly errors = inject(ErrorService);

  readonly columns = ['claimNumber', 'policyNumber', 'type', 'status', 'claimedAmount', 'createdAt'];
  readonly claims = signal<ClaimSummary[] | null>(null); // null = still loading

  constructor() {
    // the backend already returns them newest first
    inject(ClaimControllerService).my().subscribe({
      next: (claims) => this.claims.set(claims),
      error: (err) => {
        this.claims.set([]);
        this.errors.show(err);
      },
    });
  }

  open(claim: ClaimSummary): void {
    this.router.navigate(['/claims', claim.id]);
  }
}
