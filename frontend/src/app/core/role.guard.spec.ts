import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { AuthService, Role } from './auth.service';
import { roleGuard } from './role.guard';

describe('roleGuard', () => {
  function runGuard(currentRole: Role | null, allowed: Role[]) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        // fake AuthService with a fixed role; homeUrl mirrors the real mapping
        { provide: AuthService, useValue: { role: () => currentRole, homeUrl: (r: Role) => (r === 'CLAIMANT' ? '/claims' : '/staff') } },
      ],
    });
    // guards use inject(), so they must run inside an injection context
    return TestBed.runInInjectionContext(() => roleGuard(...allowed)({} as never, {} as never));
  }

  const url = (result: unknown) => TestBed.inject(Router).serializeUrl(result as UrlTree);

  it('blocks a claimant from an officer route and sends them to their own start page', () => {
    const result = runGuard('CLAIMANT', ['OFFICER']);
    expect(result).toBeInstanceOf(UrlTree);
    expect(url(result)).toBe('/claims');
  });

  it('lets an officer into an officer route', () => {
    expect(runGuard('OFFICER', ['OFFICER', 'MANAGER'])).toBe(true);
  });

  it('sends a logged-out visitor to login', () => {
    expect(url(runGuard(null, ['CLAIMANT']))).toBe('/login');
  });
});
