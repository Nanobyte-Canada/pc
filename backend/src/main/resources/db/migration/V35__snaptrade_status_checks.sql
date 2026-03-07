CREATE TABLE snaptrade_status_checks (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    status          VARCHAR(20)  NOT NULL,
    response_time_ms INT,
    version         VARCHAR(50),
    error_message   TEXT,
    raw_response    JSONB,
    checked_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_snaptrade_status CHECK (status IN ('ONLINE','DEGRADED','OFFLINE','UNKNOWN'))
);

CREATE INDEX idx_snaptrade_status_checked_at ON snaptrade_status_checks(checked_at DESC);
