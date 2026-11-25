import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { NotificationView } from '../api/reporting';
import { AuthService } from '../core/auth.service';
import { NotificationService } from '../core/notification.service';
import { NotificationBell } from './notification-bell';

@Component({ template: '' })
class Page {}

describe('NotificationBell', () => {
  let created = 0;
  @Component({ template: '' })
  class ClaimPage {
    constructor() {
      created++; // counts how often the claim page is (re)created, i.e. re-reads the claim
    }
  }

  const n = { id: 'n1', claimId: 'c1' } as NotificationView;

  beforeEach(() => {
    created = 0;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', component: Page },
          { path: 'claims/:id', component: ClaimPage },
        ]),
        { provide: AuthService, useValue: { claimUrl: (id: string) => `/claims/${id}` } },
        { provide: NotificationService, useValue: { markRead: vi.fn() } },
      ],
    });
  });

  it('reloads the claim page when the notification is for the claim already open', async () => {
    // the harness provides a router outlet, so navigating really creates the page component
    const harness = await RouterTestingHarness.create('/claims/c1');
    const router = TestBed.inject(Router);
    expect(created).toBe(1);

    TestBed.runInInjectionContext(() => new NotificationBell()).open(n);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(created).toBe(2);
    });
    expect(router.url).toBe('/claims/c1');
  });
});
