-- occurredAt of the event that last overwrote the row; dashboards' asOf (Q21)
ALTER TABLE claim_summary ADD COLUMN last_event_at timestamptz;
-- rows written before this column: processing time is the closest value we have
UPDATE claim_summary SET last_event_at = updated_at;
ALTER TABLE claim_summary ALTER COLUMN last_event_at SET NOT NULL;
