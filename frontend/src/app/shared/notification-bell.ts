import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { NotificationView } from '../api/reporting';
import { AuthService } from '../core/auth.service';
import { NotificationService } from '../core/notification.service';

/**
 * Bell in the top nav with an unread badge; opens a glass panel with the notifications (FRONTEND-DESIGN.md §4, §5).
 * The panel is a mat-menu: it is already a glass overlay in our theme, closes on Escape / outside click,
 * and sets aria-expanded on the bell (§8).
 */
@Component({
  selector: 'app-notification-bell',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatMenuModule],
  template: `
    <button
      matIconButton
      class="bell"
      [matMenuTriggerFor]="panel"
      [attr.aria-label]="'Notifications, ' + notifications.unreadCount() + ' unread'"
    >
      <mat-icon>notifications</mat-icon>
      @if (notifications.unreadCount(); as count) {
        <span class="badge num" aria-hidden="true">{{ count > 9 ? '9+' : count }}</span>
      }
    </button>

    <mat-menu #panel="matMenu" xPosition="before" class="notification-panel">
      <div class="panel-head">Notifications</div>
      @for (n of notifications.notifications(); track n.id) {
        <button mat-menu-item class="item" [class.unread]="!n.readAt" (click)="open(n)">
          <span class="item-body">
            <span class="item-title">
              <!-- unread is marked by the dot, bold text and the hidden word: never by colour alone -->
              @if (!n.readAt) {
                <span class="dot" aria-hidden="true"></span><span class="visually-hidden">Unread: </span>
              }
              <span class="num">{{ n.claimNumber }}</span>
            </span>
            <span class="message">{{ n.message }}</span>
            <span class="when muted num">{{ n.createdAt | date: 'medium' }}</span>
          </span>
        </button>
      } @empty {
        <p class="empty muted">No notifications yet.</p>
      }
    </mat-menu>
  `,
  styles: `
    .bell { position: relative; color: var(--text); }
    .badge {
      position: absolute;
      top: 4px;
      right: 2px;
      min-width: 16px;
      height: 16px;
      padding: 0 4px;
      box-sizing: border-box;
      border-radius: var(--radius-pill);
      background: var(--negative);
      color: var(--on-brand);
      font-size: 10.5px;
      font-weight: 700;
      line-height: 16px;
      text-align: center;
    }
  `,
})
export class NotificationBell {
  protected readonly notifications = inject(NotificationService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Marks it read and opens the claim in the page that fits the user's role. */
  open(n: NotificationView): void {
    this.notifications.markRead(n);
    const url = this.auth.claimUrl(n.claimId);
    if (this.router.url === url) {
      // Already on this claim: Angular ignores navigation to the current URL, so the page would keep
      // showing old data (e.g. no "Action needed" card). Leave and come back without touching the
      // address bar, which recreates the page and re-reads the claim.
      this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => this.router.navigateByUrl(url));
    } else {
      this.router.navigateByUrl(url);
    }
  }
}
