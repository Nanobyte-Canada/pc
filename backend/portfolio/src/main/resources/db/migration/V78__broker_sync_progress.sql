CREATE TABLE broker_sync_progress (
    connection_id   BIGINT       NOT NULL,
    sync_kind       VARCHAR(20)  NOT NULL,
    next_chunk_end  DATE         NOT NULL,   -- next (earlier) chunk end to attempt; sync iterates backward
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (connection_id, sync_kind),
    CONSTRAINT fk_sync_progress_connection
        FOREIGN KEY (connection_id) REFERENCES broker_connections (id) ON DELETE CASCADE
);
