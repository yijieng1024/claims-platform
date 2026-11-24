import { Component, inject, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ClaimControllerService, ClaimSummary } from '../../api/claims';
import { ErrorService } from '../../core/error.service';
import { MoneyPipe } from '../../shared/money.pipe';
import { StatusChip } from '../../shared/status-chip';

@Component({
  selector: 'app-my-claims',
  imports: [RouterLink, DatePipe, TitleCasePipe, MatButtonModule, MatCardModule, MatIconModule, MatTableModule, MatProgressBarModule, MoneyPipe, StatusChip],
  templateUrl: './my-claims.html',
  styles: `
    .header { margin-bottom: 16px; }
    .header h1 { margin: 0; }
    tr.mat-mdc-row { cursor: pointer; }
    .claim-link { font-weight: 600; text-decoration: none; }
    .claim-link:hover { text-decoration: underline; }
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
