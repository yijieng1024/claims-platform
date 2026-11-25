-- Q30: one claim per policy and incident date. A REJECTED claim doesn't count, so it may be re-submitted.
-- Backs up the check in ClaimService.open: two submissions racing past it can't both be saved.
CREATE UNIQUE INDEX claims_policy_incident_uidx ON claims (policy_id, incident_date) WHERE status <> 'REJECTED';
