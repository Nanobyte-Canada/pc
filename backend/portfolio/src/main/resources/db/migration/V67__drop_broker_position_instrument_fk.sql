-- Drop the instrument_id foreign key from broker_positions.
-- BrokerPosition now uses its existing symbol, security_name, and instrument_type columns
-- instead of a JPA relationship to the stocks table.
-- No data migration needed: symbol, security_name, and instrument_type are already populated
-- on every position row (set during SnapTrade position fetch).

-- Drop the FK constraint first
ALTER TABLE broker_positions DROP CONSTRAINT IF EXISTS fk_broker_positions_instrument;

-- Also try the auto-generated constraint name pattern used by Hibernate/JPA
DO $$
BEGIN
    -- Find and drop any FK constraint referencing stocks from broker_positions on instrument_id
    PERFORM 1 FROM information_schema.table_constraints tc
    JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name
    WHERE tc.table_name = 'broker_positions'
      AND tc.constraint_type = 'FOREIGN KEY'
      AND ccu.column_name = 'instrument_id';

    IF FOUND THEN
        EXECUTE (
            SELECT 'ALTER TABLE broker_positions DROP CONSTRAINT ' || tc.constraint_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
            WHERE tc.table_name = 'broker_positions'
              AND tc.constraint_type = 'FOREIGN KEY'
              AND kcu.column_name = 'instrument_id'
            LIMIT 1
        );
    END IF;
END $$;

-- Drop the column itself
ALTER TABLE broker_positions DROP COLUMN IF EXISTS instrument_id;

-- Drop the index on instrument_id if it exists
DROP INDEX IF EXISTS idx_broker_positions_instrument_id;
