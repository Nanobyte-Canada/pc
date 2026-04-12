-- V49: Stock deduplication — consolidate cross-listed tickers, then drop exchange column.
-- MUST run AFTER backend code is deployed without stock.exchange in the Kotlin entity.

-- Step 1: Build winner map — prefer US > TO > V by exchange, then earliest created_at
CREATE TEMP TABLE stock_dedup_map AS
WITH ranked AS (
    SELECT
        id,
        ticker,
        ROW_NUMBER() OVER (
            PARTITION BY ticker
            ORDER BY
                CASE exchange WHEN 'US' THEN 0 WHEN 'TO' THEN 1 WHEN 'V' THEN 2 ELSE 3 END,
                created_at ASC
        ) AS rn,
        FIRST_VALUE(id) OVER (
            PARTITION BY ticker
            ORDER BY
                CASE exchange WHEN 'US' THEN 0 WHEN 'TO' THEN 1 WHEN 'V' THEN 2 ELSE 3 END,
                created_at ASC
        ) AS winner_id
    FROM stocks
)
SELECT id AS loser_id, winner_id
FROM ranked
WHERE rn > 1 AND id != winner_id;

-- Step 2: Remap etf_holdings.stock_id — delete conflicting duplicates first, then remap
DELETE FROM etf_holdings
WHERE stock_id IN (SELECT loser_id FROM stock_dedup_map)
  AND EXISTS (
      SELECT 1
      FROM etf_holdings eh2
      JOIN stock_dedup_map sdm ON sdm.winner_id = eh2.stock_id
      WHERE eh2.etf_id = etf_holdings.etf_id
        AND eh2.as_of_date = etf_holdings.as_of_date
        AND sdm.loser_id = etf_holdings.stock_id
  );

UPDATE etf_holdings
SET stock_id = sdm.winner_id
FROM stock_dedup_map sdm
WHERE etf_holdings.stock_id = sdm.loser_id;

-- Step 3: Delete loser records
DELETE FROM stocks
WHERE id IN (SELECT loser_id FROM stock_dedup_map);

-- Step 4: Guard — fail migration if duplicates remain
DO $$
BEGIN
    IF EXISTS (SELECT ticker FROM stocks GROUP BY ticker HAVING COUNT(*) > 1) THEN
        RAISE EXCEPTION 'Duplicate tickers still exist after deduplication — aborting migration';
    END IF;
END $$;

-- Step 5: Drop old unique constraint and exchange column, add new unique constraint
ALTER TABLE stocks DROP CONSTRAINT IF EXISTS uq_stocks_ticker_exchange;
ALTER TABLE stocks DROP COLUMN IF EXISTS exchange;
ALTER TABLE stocks ADD CONSTRAINT uq_stocks_ticker UNIQUE (ticker);
