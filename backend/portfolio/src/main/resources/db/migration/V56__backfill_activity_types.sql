-- Reclassify activities that were incorrectly mapped as FEE but are actually COMMISSION.
-- The raw_payload JSONB from SnapTrade contains the original type which we use to distinguish.
UPDATE broker_activities
SET type = 'COMMISSION'
WHERE type = 'FEE'
  AND raw_payload IS NOT NULL
  AND raw_payload::text ILIKE '%commission%';
