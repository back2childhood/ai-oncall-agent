CREATE TABLE operational_sync_runs (
    id UUID PRIMARY KEY,
    source TEXT NOT NULL,
    status TEXT NOT NULL,
    item_count INTEGER NOT NULL DEFAULT 0,
    document_id UUID REFERENCES uploaded_documents(id) ON DELETE SET NULL,
    message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_operational_sync_runs_source_started
    ON operational_sync_runs(source, started_at DESC);
