import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Runs for every HttpClient request (registered in app.config.ts).
 * - Adds "Authorization: Bearer <token>" to our own /api/** calls (never to other hosts).
 * - A 401 while logged in means the token is expired or no longer accepted: log out.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/')) return next(req);

  const auth = inject(AuthService);
  const token = auth.token();
  const request = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(request).pipe(
    catchError((err: unknown) => {
      // when not logged in (login page), a 401 is a normal answer like "wrong password": just pass it on
      if (err instanceof HttpErrorResponse && err.status === 401 && auth.isLoggedIn()) {
        auth.logout('Session expired, please log in again.');
      }
      return throwError(() => err);
    }),
  );
};
