CREATE TABLE crawl_jobs (
    id UUID PRIMARY KEY,
    status TEXT NOT NULL,
    user_query TEXT NOT NULL,
    root_url TEXT NOT NULL,
    max_depth INTEGER NOT NULL,
    max_steps INTEGER NOT NULL,
    timeout_ms BIGINT NOT NULL,
    thread_count INTEGER NOT NULL,
    final_answer TEXT,
    stop_reason TEXT,
    total_pages INTEGER,
    total_results INTEGER,
    total_tokens BIGINT,
    errors TEXT[],
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ
);

CREATE INDEX idx_crawl_jobs_status ON crawl_jobs (status);
CREATE INDEX idx_crawl_jobs_created_at ON crawl_jobs (created_at);
