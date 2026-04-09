CREATE TABLE IF NOT EXISTS page_visits (
  id UUID PRIMARY KEY,
  job_id UUID NOT NULL REFERENCES crawl_jobs (id) ON DELETE CASCADE,
  thread_id TEXT,
  url TEXT NOT NULL,
  origin_url TEXT,
  depth INT,
  link_score DOUBLE PRECISION,
  link_justification TEXT,
  status TEXT,
  extraction_method TEXT,
  content_length INT,
  chunk_count INT,
  failure_reason TEXT,
  visited_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_page_visits_job_id ON page_visits (job_id);
CREATE INDEX IF NOT EXISTS idx_page_visits_url ON page_visits (url);
