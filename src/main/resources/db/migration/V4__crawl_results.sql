-- V4 — crawl_results
CREATE TABLE IF NOT EXISTS crawl_results (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES crawl_jobs (id) ON DELETE CASCADE,
    thread_id INTEGER,
    source_url TEXT NOT NULL,
    origin_chain TEXT[],
    found_at_depth INTEGER,
    page_link_score DOUBLE PRECISION,
    extracted_content TEXT,
    key_facts TEXT[],
    completeness DOUBLE PRECISION,
    missing_aspects TEXT,
    extraction_method TEXT,
    found_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_crawl_results_job_id ON crawl_results (job_id);
CREATE INDEX IF NOT EXISTS idx_crawl_results_source_url ON crawl_results (source_url);
*** End Patch to=functions.ApplyPatch"}}
{"commentary":"{\"description\":\"Add V4 migration for crawl_results\"}"}*/}Oops wrong format. I'll redo. Could be tool expects only patch content without json. Let's call properly. We'll need to ensure patch format correct. Let's send again. }<commentary to=functions.ApplyPatch  尚度ిబ_patch code.json
