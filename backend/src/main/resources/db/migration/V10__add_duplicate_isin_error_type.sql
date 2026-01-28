-- V10: Add DUPLICATE_ISIN error type for tracking ISIN conflicts during ingestion

ALTER TABLE ingestion_errors DROP CONSTRAINT chk_error_type;
ALTER TABLE ingestion_errors ADD CONSTRAINT chk_error_type CHECK (error_type IN (
    'API_ERROR', 'PARSE_ERROR', 'DB_ERROR', 'RATE_LIMIT', 'VALIDATION_ERROR',
    'AMBIGUOUS_MATCH', 'NOT_FOUND', 'DUPLICATE_ISIN'
));
