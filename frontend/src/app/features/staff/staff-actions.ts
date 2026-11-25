import { ClaimDetail } from '../../api/claims';
import { UserResponse } from '../../api/auth';

export type StaffAction = 'pickUp' | 'requestInfo' | 'assess' | 'settle' | 'reject';

/**
 * Which action buttons the staff claim detail shows, mirroring the backend's ClaimStateMachine (design §5):
 * - Managers are read-only: nothing.
 * - SUBMITTED and unassigned: any officer who can see it may pick it up (officers only see their own market).
 * - After pick-up only the assigned officer acts:
 *   UNDER_REVIEW → request info, assess, reject
 *   ASSESSED     → request info (Q7), assess again (update the reserve), settle, reject
 *   INFO_REQUESTED waits for the claimant; SETTLED and REJECTED are closed.
 *
 * Hiding buttons is convenience only: the backend checks every action again (404/403/409) and is the
 * real rule. If this list and the backend ever disagree, the backend's error is shown.
 */
export function allowedActions(
  claim: Pick<ClaimDetail, 'status' | 'assignedOfficerId'>,
  user: Pick<UserResponse, 'id' | 'role'> | null,
): StaffAction[] {
  if (user?.role !== 'OFFICER') return [];
  if (claim.status === 'SUBMITTED' && !claim.assignedOfficerId) return ['pickUp'];
  if (claim.assignedOfficerId !== user.id) return [];
  switch (claim.status) {
    case 'UNDER_REVIEW':
      return ['requestInfo', 'assess', 'reject'];
    case 'ASSESSED':
      return ['requestInfo', 'assess', 'settle', 'reject'];
    default:
      return [];
  }
}

