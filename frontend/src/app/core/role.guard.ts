import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService, Role } from './auth.service';

/**
 * Route guard: only lets users with one of `roles` open the route.
 * Not logged in → login page. Logged in with another role → that role's own start page.
 * This only hides pages; the backend still checks every request (the real security boundary).
 */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const role = auth.role();
    if (!role) return router.parseUrl('/login');
    return roles.includes(role) ? true : router.parseUrl(auth.homeUrl(role));
  };
}

/** For login/register: an already logged-in user goes straight to their start page. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? inject(Router).parseUrl(auth.homeUrl()) : true;
};
