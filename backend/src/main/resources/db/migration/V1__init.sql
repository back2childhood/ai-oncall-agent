CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE uploaded_documents (
    id UUID PRIMARY KEY,
    filename TEXT NOT NULL,
    content_type TEXT,
    source_type TEXT NOT NULL,
    status TEXT NOT NULL,
    error_message TEXT,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE document_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES uploaded_documents(id) ON DELETE CASCADE,
    vector_id TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    chapter_path TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    token_estimate INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_document_chunks_document_id ON document_chunks(document_id);
CREATE INDEX idx_document_chunks_title ON document_chunks(title);

CREATE TABLE IF NOT EXISTS vector_store (
    id UUID PRIMARY KEY,
    content TEXT,
    metadata JSONB,
    embedding vector(1536)
);

CREATE INDEX IF NOT EXISTS vector_store_embedding_hnsw_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops);
