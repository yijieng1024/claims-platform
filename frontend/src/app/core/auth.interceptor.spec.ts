import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  // a fake AuthService: only what the interceptor uses
  const auth = { token: vi.fn(), isLoggedIn: vi.fn(), logout: vi.fn() };

  beforeEach(() => {
    vi.resetAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(), // replaces the real network with a controllable fake
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('adds the bearer token to /api requests', () => {
    auth.token.mockReturnValue('abc');
    http.get('/api/claims/my').subscribe();
    expect(backend.expectOne('/api/claims/my').request.headers.get('Authorization')).toBe('Bearer abc');
  });

  it('does not send the token to other hosts', () => {
    auth.token.mockReturnValue('abc');
    http.get('https://example.com/api/x').subscribe();
    expect(backend.expectOne('https://example.com/api/x').request.headers.has('Authorization')).toBe(false);
  });

  it('logs out on 401 while logged in', () => {
    auth.token.mockReturnValue('expired');
    auth.isLoggedIn.mockReturnValue(true);
    http.get('/api/claims/my').subscribe({ error: () => {} });
    backend.expectOne('/api/claims/my').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(auth.logout).toHaveBeenCalledWith('Session expired, please log in again.');
  });

  it('does not log out on a 401 from a failed login (not logged in)', () => {
    auth.token.mockReturnValue(null);
    auth.isLoggedIn.mockReturnValue(false);
    http.post('/api/auth/login', {}).subscribe({ error: () => {} });
    backend.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(auth.logout).not.toHaveBeenCalled();
  });
});
