CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS link_candidates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES crawl_jobs (id) ON DELETE CASCADE,
    source_page_url TEXT,
    url TEXT NOT NULL,
    anchor_text TEXT,
    dom_context TEXT,
    aria_label TEXT,
    page_title TEXT,
    meta_description TEXT,
    enrichment_failed BOOLEAN NOT NULL DEFAULT FALSE,
    phase1_score DOUBLE PRECISION,
    phase1_justification TEXT,
    final_score DOUBLE PRECISION,
    final_justification TEXT,
    approved BOOLEAN,
    evaluated_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_link_candidates_job_id
    ON link_candidates (job_id);

CREATE INDEX IF NOT EXISTS idx_link_candidates_job_id_approved
    ON link_candidates (job_id, approved);

