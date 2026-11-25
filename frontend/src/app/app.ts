import { Component, computed, inject } from '@angular/core';
import { TitleCasePipe } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Logo } from './shared/logo';
import { NotificationBell } from './shared/notification-bell';
import { AuthService } from './core/auth.service';

/** The app shell: the floating top nav + the current page (<router-outlet>). */
@Component({
  selector: 'app-root',
  imports: [Logo, NotificationBell, RouterOutlet, RouterLink, RouterLinkActive, TitleCasePipe, MatButtonModule, MatIconModule, MatMenuModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  // first letter of the user's name for the small avatar circle
  protected readonly initial = computed(() => (this.auth.user()?.name ?? '?').charAt(0).toUpperCase());
}
