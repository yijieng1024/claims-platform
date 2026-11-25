import { Component, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { OfficerPerformance } from '../../api/reporting';
import { MoneyPipe } from '../../shared/money.pipe';

/**
 * One row per officer (GET /api/reports/performance): closed, settled, rejected, average days from pick-up
 * to close (Q20) and settled totals per currency (never added across currencies, Q2).
 */
@Component({
  selector: 'app-performance-table',
  imports: [DecimalPipe, MatTableModule, MoneyPipe],
  template: `
    @if (rows().length) {
      <table mat-table [dataSource]="rows()">
        <ng-container matColumnDef="officerName">
          <th mat-header-cell *matHeaderCellDef>Officer</th>
          <td mat-cell *matCellDef="let r">{{ r.officerName }}</td>
        </ng-container>
        <ng-container matColumnDef="market">
          <th mat-header-cell *matHeaderCellDef>Market</th>
          <td mat-cell *matCellDef="let r">{{ r.market }}</td>
        </ng-container>
        <ng-container matColumnDef="closed">
          <th mat-header-cell *matHeaderCellDef class="num-col">Closed</th>
          <td mat-cell *matCellDef="let r" class="num-col"><strong>{{ r.closed }}</strong></td>
        </ng-container>
        <ng-container matColumnDef="settled">
          <th mat-header-cell *matHeaderCellDef class="num-col">Settled</th>
          <td mat-cell *matCellDef="let r" class="num-col">{{ r.settled }}</td>
        </ng-container>
        <ng-container matColumnDef="rejected">
          <th mat-header-cell *matHeaderCellDef class="num-col">Rejected</th>
          <td mat-cell *matCellDef="let r" class="num-col">{{ r.rejected }}</td>
        </ng-container>
        <ng-container matColumnDef="averageDaysToClose">
          <th mat-header-cell *matHeaderCellDef class="num-col">Avg days assigned → closed</th>
          <td mat-cell *matCellDef="let r" class="num-col">
            {{ r.averageDaysToClose != null ? (r.averageDaysToClose | number: '1.1-1') : '—' }}
          </td>
        </ng-container>
        <ng-container matColumnDef="settledAmounts">
          <th mat-header-cell *matHeaderCellDef class="num-col">Settled total</th>
          <td mat-cell *matCellDef="let r" class="num-col">
            @for (a of r.settledAmounts; track a.currency) {
              <div>{{ a.amount | money: a.currency }}</div>
            } @empty {
              —
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    } @else {
      <p class="card-body muted empty">No officer has picked up a claim yet.</p>
    }
  `,
  styles: `.empty { margin: 0; }`,
})
export class PerformanceTable {
  readonly rows = input.required<OfficerPerformance[]>();
  readonly columns = ['officerName', 'market', 'closed', 'settled', 'rejected', 'averageDaysToClose', 'settledAmounts'];
}
