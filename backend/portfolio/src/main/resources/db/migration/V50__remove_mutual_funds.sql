-- V50: Remove mutual fund tables entirely.
-- MUST run AFTER backend code is deployed without MF entity/repository references.

ALTER TABLE etf_holdings DROP COLUMN IF EXISTS held_mutual_fund_id;
ALTER TABLE mutual_fund_holdings DROP CONSTRAINT IF EXISTS mutual_fund_holdings_held_mutual_fund_id_fkey;
DROP TABLE IF EXISTS mutual_fund_holdings;
DROP TABLE IF EXISTS mutual_funds;
