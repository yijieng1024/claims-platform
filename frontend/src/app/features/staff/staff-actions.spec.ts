import { ClaimDetailStatusEnum as S } from '../../api/claims';
import { UserResponseRoleEnum as R } from '../../api/auth';
import { allowedActions } from './staff-actions';

describe('allowedActions', () => {
  const me = { id: 'officer-1', role: R.Officer };
  const colleague = { id: 'officer-2', role: R.Officer };
  const manager = { id: 'manager-1', role: R.Manager };
  const claim = (status: S, assignedOfficerId: string | null = 'officer-1') => ({ status, assignedOfficerId });

  it('offers pick-up on an unassigned SUBMITTED claim to any officer', () => {
    expect(allowedActions(claim(S.Submitted, null), me)).toEqual(['pickUp']);
    expect(allowedActions(claim(S.Submitted, null), colleague)).toEqual(['pickUp']);
  });

  it('offers request info, assess and reject when UNDER_REVIEW and assigned to me', () => {
    expect(allowedActions(claim(S.UnderReview), me)).toEqual(['requestInfo', 'assess', 'reject']);
  });

  it('shows Settle only when ASSESSED and assigned to me', () => {
    expect(allowedActions(claim(S.Assessed), me)).toContain('settle');
    expect(allowedActions(claim(S.UnderReview), me)).not.toContain('settle');
    expect(allowedActions(claim(S.Assessed), colleague)).toEqual([]);
  });

  it('offers nothing while waiting for the claimant or once closed', () => {
    for (const status of [S.InfoRequested, S.Settled, S.Rejected]) {
      expect(allowedActions(claim(status), me)).toEqual([]);
    }
  });

  it('offers nothing on a claim assigned to someone else', () => {
    expect(allowedActions(claim(S.UnderReview, 'officer-2'), me)).toEqual([]);
  });

  it('offers a manager nothing, in any status', () => {
    for (const status of Object.values(S)) {
      expect(allowedActions(claim(status, null), manager)).toEqual([]);
      expect(allowedActions(claim(status), manager)).toEqual([]);
    }
  });
});
