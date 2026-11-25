import { Component, input } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { OfficerWorkload } from '../../api/reporting';

/** Open claims per officer, by status (GET /api/reports/workload). Used on the officer's Team page and the manager dashboard. */
@Component({
  selector: 'app-workload-table',
  imports: [MatTableModule],
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
        <ng-container matColumnDef="openClaims">
          <th mat-header-cell *matHeaderCellDef class="num-col">Open</th>
          <td mat-cell *matCellDef="let r" class="num-col"><strong>{{ r.openClaims }}</strong></td>
        </ng-container>
        <ng-container matColumnDef="underReview">
          <th mat-header-cell *matHeaderCellDef class="num-col">Under review</th>
          <td mat-cell *matCellDef="let r" class="num-col">{{ r.underReview }}</td>
        </ng-container>
        <ng-container matColumnDef="infoRequested">
          <th mat-header-cell *matHeaderCellDef class="num-col">Info requested</th>
          <td mat-cell *matCellDef="let r" class="num-col">{{ r.infoRequested }}</td>
        </ng-container>
        <ng-container matColumnDef="assessed">
          <th mat-header-cell *matHeaderCellDef class="num-col">Assessed</th>
          <td mat-cell *matCellDef="let r" class="num-col">{{ r.assessed }}</td>
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
export class WorkloadTable {
  readonly rows = input.required<OfficerWorkload[]>();
  readonly columns = ['officerName', 'market', 'openClaims', 'underReview', 'infoRequested', 'assessed'];
}
