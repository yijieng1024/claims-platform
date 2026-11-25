import { Component, inject, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ClaimControllerService, ClaimSummary } from '../../api/claims';
import { ErrorService } from '../../core/error.service';
import { MoneyPipe } from '../../shared/money.pipe';
import { StatusChip } from '../../shared/status-chip';

/** The officer's open claims (GET /api/claims/assigned); a row opens the staff claim detail. */
@Component({
  selector: 'app-assigned-claims',
  imports: [RouterLink, DatePipe, TitleCasePipe, MatButtonModule, MatCardModule, MatProgressBarModule, MatTableModule, MoneyPipe, StatusChip],
  template: `
    <div class="page wide">
      <h1>My claims</h1>
      <mat-card appearance="outlined" class="table-card">
        @if (claims(); as claims) {
          @if (claims.length === 0) {
            <div class="empty-state">
              <p>You have no open claims. Pick one up from the queue.</p>
              <a matButton="filled" routerLink="/officer">Open the queue</a>
            </div>
          } @else {
            <table mat-table [dataSource]="claims">
              <ng-container matColumnDef="claimNumber">
                <th mat-header-cell *matHeaderCellDef>Claim</th>
                <!-- a real link too, so the row can be opened with the keyboard -->
                <td mat-cell *matCellDef="let c">
                  <a class="num claim-link" [routerLink]="['/officer/claims', c.id]" (click)="$event.stopPropagation()">{{ c.claimNumber }}</a>
                </td>
              </ng-container>
              <ng-container matColumnDef="policyNumber">
                <th mat-header-cell *matHeaderCellDef>Policy</th>
                <td mat-cell *matCellDef="let c" class="num">{{ c.policyNumber }}</td>
              </ng-container>
              <ng-container matColumnDef="type">
                <th mat-header-cell *matHeaderCellDef>Type</th>
                <td mat-cell *matCellDef="let c">{{ c.type | titlecase }}</td>
              </ng-container>
              <ng-container matColumnDef="status">
                <th mat-header-cell *matHeaderCellDef>Status</th>
                <td mat-cell *matCellDef="let c"><app-status-chip [status]="c.status" /></td>
              </ng-container>
              <ng-container matColumnDef="claimedAmount">
                <th mat-header-cell *matHeaderCellDef class="num-col">Claimed</th>
                <td mat-cell *matCellDef="let c" class="num-col">{{ c.claimedAmount | money: c.currency }}</td>
              </ng-container>
              <ng-container matColumnDef="reserveAmount">
                <th mat-header-cell *matHeaderCellDef class="num-col">Reserve</th>
                <td mat-cell *matCellDef="let c" class="num-col">{{ c.reserveAmount != null ? (c.reserveAmount | money: c.currency) : '—' }}</td>
              </ng-container>
              <ng-container matColumnDef="updatedAt">
                <th mat-header-cell *matHeaderCellDef class="num-col">Last change</th>
                <td mat-cell *matCellDef="let c" class="num-col">{{ c.updatedAt | date: 'medium' }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr mat-row *matRowDef="let row; columns: columns" (click)="open(row)"></tr>
            </table>
          }
        } @else {
          <mat-progress-bar mode="indeterminate" />
        }
      </mat-card>
    </div>
  `,
  styles: `
    tr.mat-mdc-row { cursor: pointer; }
    .claim-link { font-weight: 600; text-decoration: none; }
    .claim-link:hover { text-decoration: underline; }
  `,
})
export class AssignedClaims {
  private readonly router = inject(Router);
  private readonly errors = inject(ErrorService);

  readonly columns = ['claimNumber', 'policyNumber', 'type', 'status', 'claimedAmount', 'reserveAmount', 'updatedAt'];
  readonly claims = signal<ClaimSummary[] | null>(null);

  constructor() {
    inject(ClaimControllerService).assigned().subscribe({
      next: (claims) => this.claims.set(claims),
      error: (err) => {
        this.claims.set([]);
        this.errors.show(err);
      },
    });
  }

  open(c: ClaimSummary): void {
    this.router.navigate(['/officer/claims', c.id]);
  }
}
