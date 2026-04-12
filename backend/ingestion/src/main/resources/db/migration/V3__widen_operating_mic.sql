-- Widen operating_mic to handle comma-separated MIC codes (e.g., "XNAS, XNYS, OTCM")
ALTER TABLE exchanges ALTER COLUMN operating_mic TYPE VARCHAR(50);
