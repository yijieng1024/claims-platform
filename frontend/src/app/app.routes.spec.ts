import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { provideApi as provideAuthApi } from './api/auth';
import { provideApi as provideClaimsApi } from './api/claims';
import { provideApi as provideReportingApi } from './api/reporting';
import { routes } from './app.routes';

/** The real route table with a logged-in user of each role (session as AuthService stores it). */
describe('routes', () => {
  function loginAs(role: string): Router {
    sessionStorage.setItem(
      'claims-platform.session',
      JSON.stringify({ token: 't', expiresAt: Date.now() + 60_000, user: { id: 'u', name: 'U', email: 'u@example.com', role } }),
    );
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(), // pages that load data get a fake backend that never answers
        provideAuthApi({ basePath: '' }),
        provideClaimsApi({ basePath: '' }),
        provideReportingApi({ basePath: '' }),
      ],
    });
    return TestBed.inject(Router);
  }

  afterEach(() => sessionStorage.clear());

  it("sends an officer who opens a manager page back to the officer's queue", async () => {
    const router = loginAs('OFFICER');
    await router.navigateByUrl('/manager/claims');
    expect(router.url).toBe('/officer');
  });

  it('lets a manager open the dashboard', async () => {
    const router = loginAs('MANAGER');
    await router.navigateByUrl('/manager');
    expect(router.url).toBe('/manager');
  });

  it('starts each role on its own home page', async () => {
    const router = loginAs('OFFICER');
    await router.navigateByUrl('/');
    expect(router.url).toBe('/officer');
  });
});
