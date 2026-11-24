import { Component, inject } from '@angular/core';
import { AuthService } from '../core/auth.service';

// Placeholder so officer and manager logins land somewhere; replaced by their screens in brief 6c.
@Component({
  selector: 'app-staff-home',
  template: `
    <div class="page">
      <h1>Welcome, {{ auth.user()?.name }}</h1>
      <p class="muted">The officer and manager screens are coming soon. Until then, use Swagger UI.</p>
    </div>
  `,
})
export class StaffHome {
  protected readonly auth = inject(AuthService);
}
