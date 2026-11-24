import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthControllerService, LoginResponse, UserResponse } from '../api/auth';

export type Role = 'CLAIMANT' | 'OFFICER' | 'MANAGER';

interface StoredSession {
  token: string;
  expiresAt: number; // epoch millis
  user: UserResponse;
}

// Token storage: sessionStorage survives a page refresh and is cleared when the tab closes.
// Trade-off: any script running on the page can read it (an XSS bug would leak the token).
// The production choice is an httpOnly cookie set by the server, which scripts cannot read (out of scope).
const STORAGE_KEY = 'claims-platform.session';

/** Holds who is logged in. One instance for the whole app (providedIn: 'root'). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthControllerService);
  private readonly router = inject(Router);

  // A signal so the top bar, guards and pages update automatically when the user logs in or out.
  private readonly session = signal<StoredSession | null>(readStoredSession());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly role = computed(() => (this.user()?.role ?? null) as Role | null);
  readonly isLoggedIn = computed(() => this.session() !== null);

  /**
   * The token, or null if there is none or it has expired. An expired session stays "logged in" until
   * the backend answers 401; the interceptor then logs out with "Session expired".
   */
  token(): string | null {
    const s = this.session();
    return s && s.expiresAt > Date.now() ? s.token : null;
  }

  login(email: string, password: string): Observable<LoginResponse> {
    return this.api.login({ email, password }).pipe(
      tap((res) => {
        const session: StoredSession = {
          token: res.accessToken!,
          expiresAt: Date.now() + (res.expiresIn ?? 0) * 1000,
          user: res.user!,
        };
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
        this.session.set(session);
      }),
    );
  }

  /** Clears the session and goes to the login page, optionally with a message (e.g. "Session expired"). */
  logout(message?: string): void {
    this.clear();
    this.router.navigate(['/login'], { queryParams: message ? { message } : {} });
  }

  /** Where each role starts after login. Officer/manager pages arrive in brief 6b. */
  homeUrl(role: Role | null = this.role()): string {
    switch (role) {
      case 'CLAIMANT':
        return '/claims';
      case 'OFFICER':
      case 'MANAGER':
        return '/staff';
      default:
        return '/login';
    }
  }

  private clear(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
  }
}

/** On app start: reuse a stored session, but throw it away if the token has already expired. */
function readStoredSession(): StoredSession | null {
  try {
    const s = JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? 'null') as StoredSession | null;
    if (s && s.expiresAt > Date.now()) return s;
  } catch {
    // corrupt value: fall through and discard it
  }
  sessionStorage.removeItem(STORAGE_KEY);
  return null;
}
