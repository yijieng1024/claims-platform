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
    path: 'claims',
    canActivate: [roleGuard('CLAIMANT')],
    children: [
      { path: '', loadComponent: () => import('./features/claimant/my-claims').then((m) => m.MyClaims) },
      { path: 'new', loadComponent: () => import('./features/claimant/new-claim').then((m) => m.NewClaim) },
      { path: ':id', loadComponent: () => import('./features/claimant/claim-detail').then((m) => m.ClaimDetailPage) },
    ],
  },
  {
    path: 'officer',
    canActivate: [roleGuard('OFFICER')],
    children: [
      { path: '', loadComponent: () => import('./features/officer/queue').then((m) => m.Queue) },
      { path: 'claims', loadComponent: () => import('./features/officer/assigned-claims').then((m) => m.AssignedClaims) },
      // the staff claim detail is shared with managers; for them it is read-only
      { path: 'claims/:id', loadComponent: () => import('./features/staff/claim-detail').then((m) => m.StaffClaimDetail) },
      { path: 'team', loadComponent: () => import('./features/officer/team').then((m) => m.Team) },
    ],
  },
  {
    path: 'manager',
    canActivate: [roleGuard('MANAGER')],
    children: [
      { path: '', loadComponent: () => import('./features/manager/dashboard').then((m) => m.Dashboard) },
      { path: 'claims', loadComponent: () => import('./features/manager/all-claims').then((m) => m.AllClaims) },
      { path: 'claims/:id', loadComponent: () => import('./features/staff/claim-detail').then((m) => m.StaffClaimDetail) },
      { path: 'performance', loadComponent: () => import('./features/manager/team-performance').then((m) => m.TeamPerformance) },
    ],
  },
  { path: '**', redirectTo: '' },
];
