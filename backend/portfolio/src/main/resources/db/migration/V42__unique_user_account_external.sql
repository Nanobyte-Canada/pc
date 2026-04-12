-- Clean up duplicate broker_connections (keep the oldest record per user+account)
DELETE FROM broker_connections
WHERE id NOT IN (
    SELECT MIN(id) FROM broker_connections
    WHERE account_id_external IS NOT NULL
    GROUP BY user_id, account_id_external
);

-- Prevent future duplicates
ALTER TABLE broker_connections
    ADD CONSTRAINT uq_user_account_external UNIQUE (user_id, account_id_external);
