import { Injectable, computed, inject, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, of, switchMap, timer } from 'rxjs';
import { NotificationControllerService, NotificationView } from '../api/reporting';
import { AuthService } from './auth.service';

/** How often the list is re-read (design §3: polling; push via SSE/WebSocket is out of scope). */
export const POLL_INTERVAL_MS = 15_000;

/**
 * The current user's notifications, re-read every 15 s while someone is logged in.
 *
 * How the polling works, read top to bottom:
 * - `toObservable(auth.isLoggedIn)` turns the login signal into a stream: it emits true on login and
 *   false on logout (or on a 401, which logs out).
 * - `switchMap` swaps the inner stream every time that value changes and unsubscribes the old one.
 *   Logged in → `timer(0, 15 s)`: tick now, then every 15 s, and fetch on every tick.
 *   Logged out → `of([])`: clear the list. The timer was unsubscribed by switchMap, so polling stops.
 * - A failed fetch is ignored (`EMPTY`) and the last list stays: no snackbar every 15 s while a
 *   service is down; the next tick simply tries again.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly api = inject(NotificationControllerService);
  private readonly auth = inject(AuthService);

  /** Newest first. The backend sorts unread first; here the bell wants a plain timeline. */
  readonly notifications = signal<NotificationView[]>([]);
  readonly unreadCount = computed(() => this.notifications().filter((n) => !n.readAt).length);

  constructor() {
    toObservable(this.auth.isLoggedIn)
      .pipe(
        switchMap((loggedIn) =>
          loggedIn
            ? timer(0, POLL_INTERVAL_MS).pipe(switchMap(() => this.api.mine().pipe(catchError(() => EMPTY))))
            : of([]),
        ),
      )
      // a root service lives as long as the app, so this subscription never needs to be ended
      .subscribe((list) => this.notifications.set(sortNewestFirst(list)));
  }

  /** Marks one as read on the server, and locally straight away so the badge updates without waiting 15 s. */
  markRead(n: NotificationView): void {
    if (n.readAt) return;
    this.api.markRead(n.id).subscribe({ error: () => {} }); // not worth an error message; the next poll shows the truth
    const now = new Date().toISOString();
    this.notifications.update((list) => list.map((x) => (x.id === n.id ? { ...x, readAt: now } : x)));
  }
}

function sortNewestFirst(list: NotificationView[]): NotificationView[] {
  return [...list].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
}
