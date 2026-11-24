import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideApi as provideAuthApi } from './api/auth';
import { provideApi as provideClaimsApi } from './api/claims';
import { provideApi as provideReportingApi } from './api/reporting';
import { authInterceptor } from './core/auth.interceptor';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // withComponentInputBinding: a route parameter like :id arrives as the component's `id` input
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    // The generated clients default to "http://localhost". An empty base path makes them call
    // relative URLs (/api/...) on the page's own origin, where the dev proxy forwards them to the right service.
    provideAuthApi({ basePath: '' }),
    provideClaimsApi({ basePath: '' }),
    provideReportingApi({ basePath: '' }),
    // the Material datepicker works with plain JavaScript Date objects (no date library needed)
    provideNativeDateAdapter(),
  ],
};
