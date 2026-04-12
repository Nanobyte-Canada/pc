-- Add Alpha Vantage ingestion status tracking for stocks
ALTER TABLE stocks ADD COLUMN av_ingestion_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE stocks ADD COLUMN av_ingestion_last_attempt_at TIMESTAMPTZ;
ALTER TABLE stocks ADD COLUMN av_ingestion_last_success_at TIMESTAMPTZ;
ALTER TABLE stocks ADD COLUMN av_ingestion_retry_count INT DEFAULT 0;
ALTER TABLE stocks ADD COLUMN av_ingestion_error_code VARCHAR(50);
ALTER TABLE stocks ADD COLUMN av_ingestion_error_message VARCHAR(500);

-- Add Alpha Vantage ingestion status tracking for ETFs
ALTER TABLE etfs ADD COLUMN av_ingestion_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE etfs ADD COLUMN av_ingestion_last_attempt_at TIMESTAMPTZ;
ALTER TABLE etfs ADD COLUMN av_ingestion_last_success_at TIMESTAMPTZ;
ALTER TABLE etfs ADD COLUMN av_ingestion_retry_count INT DEFAULT 0;
ALTER TABLE etfs ADD COLUMN av_ingestion_error_code VARCHAR(50);
ALTER TABLE etfs ADD COLUMN av_ingestion_error_message VARCHAR(500);

-- Migrate existing data: if avRawPayload exists, mark ingestion as SUCCESS
UPDATE stocks SET av_ingestion_status = 'SUCCESS' WHERE av_raw_payload IS NOT NULL;
UPDATE etfs SET av_ingestion_status = 'SUCCESS' WHERE av_raw_payload IS NOT NULL;

-- Create indexes for efficient candidate queries
CREATE INDEX idx_stocks_av_ingestion_status ON stocks(av_ingestion_status);
CREATE INDEX idx_etfs_av_ingestion_status ON etfs(av_ingestion_status);
