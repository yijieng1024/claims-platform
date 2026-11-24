import { Component, inject } from '@angular/core';
import { TitleCasePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../core/auth.service';

// Placeholder so officer and manager logins land somewhere; replaced by their screens in brief 6c.
@Component({
  selector: 'app-staff-home',
  imports: [TitleCasePipe, MatCardModule, MatIconModule],
  template: `
    <div class="page">
      <h1>Welcome, {{ auth.user()?.name }}</h1>
      <mat-card appearance="outlined">
        <div class="empty-state">
          <mat-icon class="big-icon" aria-hidden="true">construction</mat-icon>
          <p>The {{ auth.role() | titlecase }} screens are coming soon. Until then, use Swagger UI.</p>
        </div>
      </mat-card>
    </div>
  `,
  styles: `
    .big-icon {
      font-size: 40px;
      width: 40px;
      height: 40px;
      color: var(--brand);
      margin-bottom: 8px;
    }
  `,
})
export class StaffHome {
  protected readonly auth = inject(AuthService);
}
