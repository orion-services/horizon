CREATE TABLE agent_calls (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES crawl_jobs (id) ON DELETE CASCADE,
    thread_id TEXT,
    agent_role TEXT,
    provider TEXT,
    model TEXT,
    page_url TEXT,
    chunk_index INTEGER,
    chunk_total INTEGER,
    system_prompt TEXT,
    user_prompt TEXT,
    raw_response TEXT,
    parsed_relevant TEXT,
    confidence DOUBLE PRECISION,
    input_tokens INTEGER,
    output_tokens INTEGER,
    latency_ms BIGINT,
    http_status INTEGER,
    error_message TEXT,
    called_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_calls_job_id ON agent_calls (job_id);
CREATE INDEX idx_agent_calls_job_id_called_at ON agent_calls (job_id, called_at);
