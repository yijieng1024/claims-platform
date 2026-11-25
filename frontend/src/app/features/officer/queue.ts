import { Component, inject, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ClaimControllerService, ClaimSummary } from '../../api/claims';
import { ErrorService } from '../../core/error.service';
import { waitingTime } from '../../shared/dates';
import { MoneyPipe } from '../../shared/money.pipe';

/** Officer start page: unassigned SUBMITTED claims of the officer's market, oldest first (the backend's order). */
@Component({
  selector: 'app-queue',
  imports: [RouterLink, DatePipe, TitleCasePipe, MatButtonModule, MatCardModule, MatProgressBarModule, MatTableModule, MoneyPipe],
  template: `
    <div class="page wide">
      <h1>Queue</h1>
      <mat-card appearance="outlined" class="table-card">
        @if (claims(); as claims) {
          @if (claims.length === 0) {
            <div class="empty-state">
              <p>No new claims are waiting in your market.</p>
              <a matButton="filled" routerLink="/officer/claims">Go to my claims</a>
            </div>
          } @else {
            <table mat-table [dataSource]="claims">
              <ng-container matColumnDef="claimNumber">
                <th mat-header-cell *matHeaderCellDef>Claim</th>
                <td mat-cell *matCellDef="let c"><a class="num claim-link" [routerLink]="['/officer/claims', c.id]">{{ c.claimNumber }}</a></td>
              </ng-container>
              <ng-container matColumnDef="policyNumber">
                <th mat-header-cell *matHeaderCellDef>Policy</th>
                <td mat-cell *matCellDef="let c" class="num">{{ c.policyNumber }}</td>
              </ng-container>
              <ng-container matColumnDef="type">
                <th mat-header-cell *matHeaderCellDef>Type</th>
                <td mat-cell *matCellDef="let c">{{ c.type | titlecase }}</td>
              </ng-container>
              <ng-container matColumnDef="claimedAmount">
                <th mat-header-cell *matHeaderCellDef class="num-col">Claimed</th>
                <td mat-cell *matCellDef="let c" class="num-col">{{ c.claimedAmount | money: c.currency }}</td>
              </ng-container>
              <ng-container matColumnDef="incidentDate">
                <th mat-header-cell *matHeaderCellDef class="num-col">Incident</th>
                <td mat-cell *matCellDef="let c" class="num-col">{{ c.incidentDate | date: 'mediumDate' }}</td>
              </ng-container>
              <ng-container matColumnDef="createdAt">
                <th mat-header-cell *matHeaderCellDef class="num-col">Submitted</th>
                <td mat-cell *matCellDef="let c" class="num-col">{{ c.createdAt | date: 'medium' }}</td>
              </ng-container>
              <ng-container matColumnDef="waiting">
                <th mat-header-cell *matHeaderCellDef class="num-col">Waiting</th>
                <td mat-cell *matCellDef="let c" class="num-col">{{ waiting(c) }}</td>
              </ng-container>
              <ng-container matColumnDef="pickUp">
                <th mat-header-cell *matHeaderCellDef class="num-col"><span class="visually-hidden">Action</span></th>
                <td mat-cell *matCellDef="let c" class="num-col">
                  <button matButton="tonal" (click)="pickUp(c)" [disabled]="busy()" [attr.aria-label]="'Pick up ' + c.claimNumber">Pick up</button>
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr mat-row *matRowDef="let row; columns: columns"></tr>
            </table>
          }
        } @else {
          <mat-progress-bar mode="indeterminate" />
        }
      </mat-card>
    </div>
  `,
  styles: `
    .claim-link { font-weight: 600; text-decoration: none; }
    .claim-link:hover { text-decoration: underline; }
  `,
})
export class Queue {
  private readonly api = inject(ClaimControllerService);
  private readonly errors = inject(ErrorService);
  private readonly router = inject(Router);

  readonly columns = ['claimNumber', 'policyNumber', 'type', 'claimedAmount', 'incidentDate', 'createdAt', 'waiting', 'pickUp'];
  readonly claims = signal<ClaimSummary[] | null>(null);
  readonly busy = signal(false);

  constructor() {
    this.load();
  }

  waiting(c: ClaimSummary): string {
    return waitingTime(c.createdAt);
  }

  /** Assigns the claim to me, then opens it. 409 = another officer was faster: show it and refresh the queue. */
  pickUp(c: ClaimSummary): void {
    this.busy.set(true);
    this.api.assign(c.id).subscribe({
      next: () => this.router.navigate(['/officer/claims', c.id]),
      error: (err) => {
        this.busy.set(false);
        this.errors.show(err);
        this.load();
      },
    });
  }

  private load(): void {
    this.api.queue().subscribe({
      next: (claims) => this.claims.set(claims),
      error: (err) => {
        this.claims.set([]);
        this.errors.show(err);
      },
    });
  }
}
