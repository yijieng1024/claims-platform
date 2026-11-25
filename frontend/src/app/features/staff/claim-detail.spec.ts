import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideApi } from '../../api/claims';
import { AuthService } from '../../core/auth.service';
import { StaffClaimDetail } from './claim-detail';

/** Renders the page with a fake backend and returns the visible button labels in the Actions card. */
async function actionButtons(user: { id: string; role: string }, status: string, assignedOfficerId: string | null) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      provideApi({ basePath: '' }),
      {
        provide: AuthService,
        useValue: { user: signal(user), role: signal(user.role), homeUrl: () => '/officer' },
      },
    ],
  });
  const fixture = TestBed.createComponent(StaffClaimDetail);
  fixture.componentRef.setInput('id', 'c1');
  fixture.detectChanges(); // ngOnInit → the four requests

  const backend = TestBed.inject(HttpTestingController);
  backend.expectOne('/api/claims/c1').flush({
    id: 'c1', claimNumber: 'CLM-1', version: 1, status, policyId: 'p', policyNumber: 'POL-1', sumInsured: 10000, remainingCover: 10000,
    type: 'MOTOR', market: 'MY', currency: 'MYR', incidentDate: '2026-11-01', incidentLocation: 'KL',
    description: 'd', claimedAmount: 500, claimantId: 'u', claimantName: 'Claimant', assignedOfficerId,
    assignedOfficerName: assignedOfficerId ? 'Officer' : null, createdAt: '2026-11-02T00:00:00Z', updatedAt: '2026-11-02T00:00:00Z',
  });
  backend.expectOne('/api/claims/c1/history').flush([]);
  backend.expectOne('/api/claims/c1/documents').flush([]);
  backend.expectOne('/api/claims/c1/information-requests').flush([]);
  await fixture.whenStable();

  const buttons = fixture.nativeElement.querySelectorAll('.actions-card button') as NodeListOf<HTMLElement>;
  return Array.from(buttons).map((b) => b.textContent!.replace(/\s+/g, ' ').trim());
}

describe('StaffClaimDetail actions', () => {
  it('shows Settle to the assigned officer of an ASSESSED claim', async () => {
    const labels = await actionButtons({ id: 'o1', role: 'OFFICER' }, 'ASSESSED', 'o1');
    expect(labels.some((l) => l.includes('Settle'))).toBe(true);
    expect(labels.some((l) => l.includes('Reject'))).toBe(true);
  });

  it('shows no actions to another officer', async () => {
    expect(await actionButtons({ id: 'o2', role: 'OFFICER' }, 'ASSESSED', 'o1')).toEqual([]);
  });

  it('shows no actions to a manager', async () => {
    expect(await actionButtons({ id: 'm1', role: 'MANAGER' }, 'ASSESSED', 'o1')).toEqual([]);
  });
});
