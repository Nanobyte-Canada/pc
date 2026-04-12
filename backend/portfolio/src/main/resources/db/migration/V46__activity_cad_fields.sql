ALTER TABLE broker_activities
    ADD COLUMN amount_cad DECIMAL(18, 2),
    ADD COLUMN exchange_rate DECIMAL(18, 6);

-- Backfill: CAD records get exact rate of 1.0
UPDATE broker_activities SET amount_cad = amount, exchange_rate = 1.0 WHERE currency = 'CAD';

-- Non-CAD records: approximate with raw amount (will be corrected on next re-sync)
UPDATE broker_activities SET amount_cad = amount WHERE currency != 'CAD' AND amount_cad IS NULL;
