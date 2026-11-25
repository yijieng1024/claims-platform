import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideApi } from '../api/reporting';
import { AuthService } from './auth.service';
import { NotificationService, POLL_INTERVAL_MS } from './notification.service';

describe('NotificationService', () => {
  const loggedIn = signal(true);
  let backend: HttpTestingController;
  let service: NotificationService;

  /** The polling requests sent so far and not yet answered. */
  const polls = () => backend.match('/api/notifications');

  beforeEach(() => {
    vi.useFakeTimers(); // lets the test move the 15 s timer forward instantly
    loggedIn.set(true);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi({ basePath: '' }),
        // only the login signal matters to the service
        { provide: AuthService, useValue: { isLoggedIn: loggedIn } },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    service = TestBed.inject(NotificationService);
    TestBed.tick(); // runs the signal → observable bridge so the first poll starts
  });

  afterEach(() => vi.useRealTimers());

  it('fetches immediately and then every 15 s while logged in', () => {
    vi.advanceTimersByTime(0);
    expect(polls().length).toBe(1);
    vi.advanceTimersByTime(POLL_INTERVAL_MS);
    expect(polls().length).toBe(1);
    vi.advanceTimersByTime(POLL_INTERVAL_MS);
    expect(polls().length).toBe(1);
  });

  it('counts unread notifications', () => {
    vi.advanceTimersByTime(0);
    polls()[0].flush([
      { id: '1', claimId: 'c', claimNumber: 'CLM-1', type: 'X', message: 'a', createdAt: '2026-11-25T01:00:00Z', readAt: null },
      { id: '2', claimId: 'c', claimNumber: 'CLM-1', type: 'X', message: 'b', createdAt: '2026-11-25T02:00:00Z', readAt: '2026-11-25T03:00:00Z' },
    ]);
    expect(service.unreadCount()).toBe(1);
    expect(service.notifications()[0].id).toBe('2'); // newest first
  });

  it('stops polling and clears the list after logout', () => {
    vi.advanceTimersByTime(0);
    polls()[0].flush([
      { id: '1', claimId: 'c', claimNumber: 'CLM-1', type: 'X', message: 'a', createdAt: '2026-11-25T01:00:00Z', readAt: null },
    ]);

    loggedIn.set(false);
    TestBed.tick();
    vi.advanceTimersByTime(POLL_INTERVAL_MS * 4);

    expect(polls().length).toBe(0);
    expect(service.notifications()).toEqual([]);
  });
});
