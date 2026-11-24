-- When an officer picked the claim up (Q19); carried in every event snapshot.
ALTER TABLE claims ADD COLUMN assigned_at timestamptz;

-- claims picked up before this column existed: take the time from the pick-up history row
UPDATE claims c
SET assigned_at = h.changed_at
FROM claim_status_history h
WHERE h.claim_id = c.id AND h.from_status = 'SUBMITTED' AND h.to_status = 'UNDER_REVIEW';
