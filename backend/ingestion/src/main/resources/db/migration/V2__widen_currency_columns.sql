-- Widen columns to handle non-standard values from data providers
ALTER TABLE exchanges ALTER COLUMN currency TYPE VARCHAR(10);
ALTER TABLE exchanges ALTER COLUMN operating_mic TYPE VARCHAR(50);
ALTER TABLE instruments ALTER COLUMN currency TYPE VARCHAR(10);
