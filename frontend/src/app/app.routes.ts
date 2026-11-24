import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AuthService } from './core/auth.service';
import { guestGuard, roleGuard } from './core/role.guard';

// loadComponent = lazy loading: each page's code is downloaded only when the user first opens it.
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: () => inject(AuthService).homeUrl() },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login').then((m) => m.Login),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register').then((m) => m.Register),
  },
  {
    // ponytail: temporary landing page for officers and managers until their screens exist (brief 6b)
    path: 'staff',
    canActivate: [roleGuard('OFFICER', 'MANAGER')],
    loadComponent: () => import('./features/staff-home').then((m) => m.StaffHome),
  },
  { path: '**', redirectTo: '' },
];
