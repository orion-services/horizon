# HorizOn — Architecture Guide

> **Semantic Web Crawler guided by LLM Agents**  
> Stack: Java 25 · Quarkus (latest) · Hexagonal Architecture · PostgreSQL

This document is the single source of truth for implementing the HorizOn
project. Read it entirely before working on any issue.

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Hexagonal Architecture](#2-hexagonal-architecture)
3. [Package Structure](#3-package-structure)
4. [Domain Model](#4-domain-model)
5. [Ports (Interfaces)](#5-ports-interfaces)
6. [Adapters](#6-adapters)
7. [Configuration](#7-configuration)
8. [REST API](#8-rest-api)
9. [Database Schema](#9-database-schema)
10. [LLM Agents](#10-llm-agents)
11. [Navigation Flow](#11-navigation-flow)
12. [Content Extraction Pipeline](#12-content-extraction-pipeline)
13. [Link Ranking Pipeline](#13-link-ranking-pipeline)
14. [Rate Limiting and Backoff](#14-rate-limiting-and-backoff)
15. [Concurrency Model](#15-concurrency-model)
16. [Code Quality Rules](#16-code-quality-rules)
17. [Testing Strategy](#17-testing-strategy)
18. [Decision Log](#18-decision-log)

---

## 1. System Overview

HorizOn is a Quarkus REST service that autonomously navigates websites guided
by LLM agents. The user submits a natural-language query and a starting URL;
the system crawls the site, understands page content semantically, ranks links
by relevance, collects partial results, and returns a consolidated answer with
source references.

```
POST /HorizOn/search  →  job created (async)
GET  /HorizOn/status/{jobId}  →  job status + result
GET  /HorizOn/csv/{jobId}     →  full execution log as CSV
```

**Key design constraints:**

- All LLM calls are serialized through a central `RateLimiter` (500 ms minimum
  gap by default) to avoid HTTP 429 errors.
- Navigation uses DFS with backtracking across N configurable threads.
- Every LLM call (prompt + response + tokens + latency) is persisted in
  PostgreSQL for offline analysis and prompt refinement.
- The architecture is hexagonal so any LLM provider, browser engine, or
  database can be swapped without touching domain logic.

---

## 2. Hexagonal Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│              DRIVING ADAPTERS (in)                              │
│  SearchResource · StatusResource · CsvResource  (JAX-RS)       │
└──────────────────────────┬──────────────────────────────────────┘
                           │ uses ↓
              ┌────────────▼────────────┐
              │     PORT (in)           │
              │   CrawlJobPort          │
              └────────────┬────────────┘
                           │ implemented by ↓
┌──────────────────────────▼──────────────────────────────────────┐
│                      DOMAIN CORE                                │
│                                                                 │
│  CrawlJobService · CrawlerOrchestrator · CrawlerThread          │
│  AgentVerifier · AgentExtractor · AgentRanker                   │
│  AgentConsolidator · ContentExtractor · TextChunker             │
│  VisitRegistry · RateLimiter                                    │
│                                                                 │
│  Models: CrawlJob · PageNode · LinkCandidate · CrawlResult      │
│          NavigationContext · AgentResponse · AgentCallLog       │
└────┬──────────────┬──────────────┬──────────────┬──────────────┘
     │              │              │              │
     │ uses ↓       │ uses ↓       │ uses ↓       │ uses ↓
┌────▼──────┐ ┌─────▼──────┐ ┌────▼──────┐ ┌────▼────────────┐
│ PORT(out) │ │ PORT(out)  │ │ PORT(out) │ │ PORT(out)       │
│ LLMPro-  │ │ BrowserPort│ │Persistence│ │ LinkEnricher-   │
│ viderPort │ │            │ │Port       │ │ Port            │
└────┬──────┘ └─────┬──────┘ └────┬──────┘ └────┬────────────┘
     │ impl ↓       │ impl ↓      │ impl ↓       │ impl ↓
┌────▼──────────────▼─────────────▼──────────────▼────────────┐
│              DRIVEN ADAPTERS (out)                           │
│  AnthropicAdapter · OllamaAdapter · OpenAIAdapter            │
│  PlaywrightAdapter                                           │
│  PostgresPersistenceAdapter                                  │
│  JsoupLinkEnricherAdapter                                    │
└──────────────────────────────────────────────────────────────┘
```

**Golden rule:** The domain core must have **zero** imports from Quarkus,
Jakarta EE, OkHttp, Jsoup, Playwright, or any framework. It only imports
Java standard library and its own classes.

---

## 3. Package Structure

```
br.ifrs.horizon
│
├── domain/
│   ├── model/
│   │   ├── AgentCallLog.java
│   │   ├── AgentResponse.java
│   │   ├── CrawlJob.java
│   │   ├── CrawlResult.java
│   │   ├── ExtractionMethod.java       (enum: READABILITY, JSOUP, RAW)
│   │   ├── JobStatus.java              (enum: PENDING, RUNNING, COMPLETED,
│   │   │                                       FAILED, TIMEOUT, ABORTED)
│   │   ├── LinkCandidate.java
│   │   ├── NavigationContext.java
│   │   ├── PageNode.java
│   │   ├── PageStatus.java             (enum: PENDING, PROCESSING, DONE,
│   │   │                                       RESULT_FOUND, DISCARDED)
│   │   └── StopReason.java             (enum: EXHAUSTED, MAX_STEPS,
│   │                                           TIMEOUT, ABORTED)
│   ├── port/
│   │   ├── in/
│   │   │   └── CrawlJobPort.java
│   │   └── out/
│   │       ├── BrowserPort.java
│   │       ├── LinkEnricherPort.java
│   │       ├── LLMProviderPort.java
│   │       └── PersistencePort.java
│   └── service/
│       ├── agent/
│       │   ├── AgentConsolidator.java
│       │   ├── AgentExtractor.java
│       │   ├── AgentRanker.java
│       │   └── AgentVerifier.java
│       ├── ContentExtractor.java
│       ├── CrawlJobService.java        (implements CrawlJobPort)
│       ├── CrawlerOrchestrator.java
│       ├── CrawlerThread.java
│       ├── RateLimiter.java
│       ├── TextChunker.java
│       └── VisitRegistry.java
│
├── adapter/
│   ├── in/
│   │   └── rest/
│   │       ├── CsvResource.java
│   │       ├── SearchResource.java
│   │       ├── StatusResource.java
│   │       └── dto/
│   │           ├── JobResultDto.java
│   │           ├── JobStatusResponseDto.java
│   │           ├── SearchOptionsDto.java
│   │           ├── SearchRequestDto.java
│   │           └── SourceReferenceDto.java
│   └── out/
│       ├── browser/
│       │   └── PlaywrightAdapter.java
│       ├── enricher/
│       │   └── JsoupLinkEnricherAdapter.java
│       ├── llm/
│       │   ├── AnthropicAdapter.java
│       │   ├── OllamaAdapter.java
│       │   └── OpenAIAdapter.java
│       └── persistence/
│           ├── PostgresPersistenceAdapter.java
│           └── entity/
│               ├── AgentCallEntity.java
│               ├── CrawlJobEntity.java
│               ├── CrawlResultEntity.java
│               ├── LinkCandidateEntity.java
│               └── PageVisitEntity.java
│
├── infrastructure/
│   ├── config/
│   │   ├── AgentConfig.java
│   │   └── CrawlerConfig.java
│   └── db/migration/
│       ├── V1__create_crawl_jobs.sql
│       ├── V2__create_page_visits.sql
│       ├── V3__create_link_candidates.sql
│       ├── V4__create_crawl_results.sql
│       └── V5__create_agent_calls.sql
│
└── HorizOnApplication.java
```

---

## 4. Domain Model

### 4.1 CrawlJob

Root aggregate. Represents a crawling session from start to finish.

```java
public class CrawlJob {
    private final UUID id;
    private final String userQuery;
    private final String rootUrl;
    private final CrawlerConfig config;
    private JobStatus status;           // PENDING → RUNNING → COMPLETED|FAILED
    private String finalAnswer;         // output of AgentConsolidator
    private StopReason stopReason;
    private List<String> errors;        // non-fatal errors
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private int totalPagesVisited;
    private int totalResultsFound;
    private int totalTokensConsumed;
}
```

### 4.2 PageNode

Represents one visited or queued page. Each thread owns its own instance.

```java
public class PageNode {
    private final String url;
    private final String originUrl;
    private final int depth;
    private final double scoreReceived;     // score given by AgentRanker
    private final String rankerJustification;
    private String extractedContent;
    private ExtractionMethod extractionMethod;
    private List<String> chunks;
    private List<LinkCandidate> allLinks;
    private PriorityQueue<LinkCandidate> candidateQueue;
    private PageStatus status;
    private Instant visitedAt;
    private String failureReason;
    private CrawlResult partialResult;
}
```

### 4.3 LinkCandidate

Implements `Comparable<LinkCandidate>` — descending order by `finalScore`.

```java
public class LinkCandidate implements Comparable<LinkCandidate> {
    // From DOM
    private final String url;
    private final String anchorText;
    private final String domContext;        // parent element text, max 200 chars
    private final String ariaLabel;
    // Phase 1 ranking
    private double phase1Score;
    private String phase1Justification;
    // Jsoup enrichment (between Phase 1 and Phase 2)
    private String pageTitle;
    private String metaDescription;
    private boolean enrichmentFailed;
    // Phase 2 ranking
    private double finalScore;
    private String finalJustification;
}
```

### 4.4 NavigationContext

Immutable record serialized as JSON and passed to every LLM agent call.

```java
public record NavigationContext(
    String userQuery,
    String currentUrl,
    String originUrl,
    int currentDepth,
    int maxDepth,
    double linkScore,
    String linkJustification,
    String extractionMethod,    // ExtractionMethod.name()
    String pageTitle
) {}
```

### 4.5 AgentResponse

Wraps any LLM response with observability metadata.

```java
public class AgentResponse {
    private final String rawContent;
    private final String providerUsed;
    private final String modelUsed;
    private final int inputTokens;
    private final int outputTokens;
    private final long latencyMs;
    private final int httpStatus;
    private final boolean parseError;
    private final String errorMessage;
    // Agent-specific parsed fields (null if not applicable)
    private Boolean relevant;           // AgentVerifier
    private Double confidence;          // AgentVerifier
    private String justification;       // AgentVerifier, AgentRanker
}
```

### 4.6 AgentCallLog

Persisted to `agent_calls` table after every LLM call.

```java
public class AgentCallLog {
    private UUID jobId;
    private String threadId;
    private String agentRole;       // VERIFIER, EXTRACTOR, RANKER, CONSOLIDATOR
    private String provider;        // ANTHROPIC, OLLAMA, OPENAI
    private String model;
    private String pageUrl;
    private int chunkIndex;         // -1 if not applicable
    private int chunkTotal;
    private String systemPrompt;    // full prompt
    private String userPrompt;      // full prompt
    private String rawResponse;
    private Boolean parsedRelevant;
    private Double confidence;
    private int inputTokens;
    private int outputTokens;
    private long latencyMs;
    private int httpStatus;
    private String errorMessage;
    private Instant calledAt;
}
```

---

## 5. Ports (Interfaces)

### 5.1 CrawlJobPort (in)

```java
public interface CrawlJobPort {
    UUID submitJob(String query, String url, JobOptions options);
    Optional<CrawlJob> getStatus(UUID jobId);
    Optional<String> exportCsv(UUID jobId);
}
```

### 5.2 LLMProviderPort (out)

```java
public interface LLMProviderPort {
    AgentResponse call(LLMRequest request);
    String getProviderName();   // "ANTHROPIC", "OLLAMA", "OPENAI"
    String getModelName();
}
```

`LLMRequest` value object:

```java
public record LLMRequest(
    String systemPrompt,
    String userPrompt,
    String agentRole,
    int maxTokens
) {}
```

### 5.3 BrowserPort (out)

```java
public interface BrowserPort {
    Optional<String> loadPage(String url, long timeoutMs);
}
```

Returns the fully-rendered HTML after dynamic content expansion. Returns
`Optional.empty()` on timeout or navigation error — never throws.

### 5.4 PersistencePort (out)

```java
public interface PersistencePort {
    void saveJob(CrawlJob job);
    void updateJob(CrawlJob job);
    void savePageVisit(UUID jobId, PageNode node);
    void saveLinkCandidate(UUID jobId, String sourceUrl, LinkCandidate link);
    void saveCrawlResult(UUID jobId, CrawlResult result);
    void saveAgentCall(UUID jobId, AgentCallLog log);
    Optional<CrawlJob> findJobById(UUID jobId);
    List<String[]> exportJobAsCsvRows(UUID jobId);
}
```

### 5.5 LinkEnricherPort (out)

```java
public interface LinkEnricherPort {
    void enrich(List<LinkCandidate> candidates, long timeoutMs);
}
```

Enriches candidates in-place. Individual failures set `enrichmentFailed=true`
and are never propagated — the link is kept with its Phase 1 score.

---

## 6. Adapters

### 6.1 LLM Adapters

All implement `LLMProviderPort` and are injected via CDI `@Named` qualifier.

| Adapter | Provider | Endpoint |
|---|---|---|
| `AnthropicAdapter` | Anthropic | `POST https://api.anthropic.com/v1/messages` |
| `OllamaAdapter` | Ollama (local) | `POST {baseUrl}/api/chat` |
| `OpenAIAdapter` | OpenAI | `POST https://api.openai.com/v1/chat/completions` |

HTTP transport: **OkHttp 4.x** for all adapters.

Error handling contract:
- HTTP 429 → throw `LLMRateLimitException` (caught by agents to call
  `rateLimiter.on429()`)
- Other HTTP errors → throw `LLMException` with status + body
- Timeout → throw `LLMException`

### 6.2 PlaywrightAdapter

Implements `BrowserPort`. Browser is **`@ApplicationScoped`** singleton —
never recreated per request. Each page load opens a new `Page` tab.

Expansion sequence per page:
1. `page.navigate(url)`
2. `page.waitForLoadState(NETWORKIDLE)`
3. Full scroll via `page.evaluate("window.scrollTo(0, document.body.scrollHeight)")`
4. Wait 1000 ms
5. Conservatively expand collapsed elements:
   - `<details>` without `open` attribute → click `<summary>`
   - Elements with `aria-expanded="false"` → click (only if no external href)
   - Elements with `data-toggle="collapse"` → click
   - **Never click** `button[type=submit]` or links with external href
6. Wait 500 ms
7. Inject `Readability.js` from `src/main/resources/readability/Readability.js`
8. Run extraction via `page.evaluate()`
9. Return `document.documentElement.outerHTML`

### 6.3 PostgresPersistenceAdapter

Implements `PersistencePort` using Hibernate ORM + Panache.

The `exportJobAsCsvRows` method performs a JOIN across all 5 tables and returns
rows with **25 columns** in this order:

```
job_id, event_timestamp, event_type, thread_id, page_url, page_depth,
extraction_method, content_length, chunk_index, chunk_total, agent_role,
agent_provider, agent_model, agent_input_tokens, agent_output_tokens,
agent_latency_ms, agent_prompt, agent_response, link_url, link_phase1_score,
link_final_score, link_justification, result_content, stop_reason,
error_message
```

### 6.4 JsoupLinkEnricherAdapter

Implements `LinkEnricherPort`. Runs enrichment requests in parallel using a
fixed thread pool. Per-request timeout comes from `CrawlerConfig`.

```java
Jsoup.connect(url)
    .userAgent("Mozilla/5.0 (compatible; HorizOn/1.0)")
    .timeout((int) timeoutMs)
    .get();
```

---

## 7. Configuration

All settings use `@ConfigMapping(prefix = "horizon.crawler")`.

### application.properties defaults

```properties
# Navigation
horizon.crawler.max-depth=5
horizon.crawler.max-steps=20
horizon.crawler.session-timeout-ms=60000
horizon.crawler.thread-count=3
horizon.crawler.restrict-to-domain=true

# Content extraction
horizon.crawler.min-content-length=200
horizon.crawler.chunk-size=3000
horizon.crawler.chunk-overlap=300
horizon.crawler.playwright-timeout-ms=15000
horizon.crawler.jsoup-enrich-timeout-ms=5000

# Ranking thresholds
horizon.crawler.pre-threshold=0.40
horizon.crawler.final-threshold=0.60

# Rate limiting
horizon.crawler.llm-delay-ms=500
horizon.crawler.backoff-429-seconds=30,60,120
horizon.crawler.max-consecutive-429=3

# Agents — each can have an independent provider and model
horizon.agents.verifier.provider=OLLAMA
horizon.agents.verifier.model=llama3.1
horizon.agents.verifier.base-url=http://localhost:11434
horizon.agents.verifier.max-tokens=512

horizon.agents.ranker.provider=OLLAMA
horizon.agents.ranker.model=mistral
horizon.agents.ranker.base-url=http://localhost:11434
horizon.agents.ranker.max-tokens=1024

horizon.agents.extractor.provider=ANTHROPIC
horizon.agents.extractor.model=claude-haiku-4-5
horizon.agents.extractor.api-key=${ANTHROPIC_API_KEY}
horizon.agents.extractor.max-tokens=1000

horizon.agents.consolidator.provider=ANTHROPIC
horizon.agents.consolidator.model=claude-sonnet-4-5
horizon.agents.consolidator.api-key=${ANTHROPIC_API_KEY}
horizon.agents.consolidator.max-tokens=2000

# Database
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=${DB_USER}
quarkus.datasource.password=${DB_PASSWORD}
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/horizon
quarkus.hibernate-orm.database.generation=none
quarkus.flyway.migrate-at-start=true
```

> ⚠ Never hardcode API keys. Use environment variables.

---

## 8. REST API

Base path: `http://localhost:8080/HorizOn`

### POST /HorizOn/search

Submit a new crawling job (async — returns immediately).

**Request:**
```json
{
  "query": "busque por preços de mac mini",
  "url": "https://www.magazineluiza.com.br/busca",
  "options": {
    "max_depth": 3,
    "max_steps": 15,
    "timeout_ms": 45000
  }
}
```

**Response 202:**
```json
{
  "job_id": "d769b3fd-6e38-496c-ab2e-6e13d4bc7c79",
  "status": "PENDING",
  "created_at": "2026-04-09T02:01:51.077453Z"
}
```

Validation: `query` and `url` are `@NotBlank`. `url` must match
`https?://.*`. `options` fields are optional — fall back to config defaults
when null.

### GET /HorizOn/status/{jobId}

**Response 200 (COMPLETED):**
```json
{
  "status": "COMPLETED",
  "query": "me encontra 3 opcoes de tv 55 polegadas",
  "result": {
    "answer": "Aqui estão 3 opções de TV 55 polegadas...",
    "fields": {},
    "sources": [
      {
        "url": "https://www.magazineluiza.com.br",
        "found": true,
        "confidence": "medium",
        "pages_visited": 2
      }
    ]
  },
  "errors": [],
  "job_id": "d769b3fd-6e38-496c-ab2e-6e13d4bc7c79",
  "created_at": "2026-04-09T02:01:51.077453Z",
  "finished_at": "2026-04-09T02:02:51.160194Z"
}
```

`result` is `null` while the job is `RUNNING` or `PENDING`.  
Returns **404** for unknown `jobId`.

### GET /HorizOn/csv/{jobId}

Returns the full execution log as a CSV file.

```
Content-Type: text/csv; charset=UTF-8
Content-Disposition: attachment; filename="horizon_{jobId}.csv"
```

25 columns (see Section 6.3). Fields containing newlines are quoted per
RFC 4180.

---

## 9. Database Schema

Managed by Flyway. Migrations live in
`src/main/resources/db/migration/`.

### crawl_jobs (V1)
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | `gen_random_uuid()` |
| status | VARCHAR(20) | PENDING/RUNNING/COMPLETED/FAILED/TIMEOUT/ABORTED |
| user_query | TEXT | |
| root_url | TEXT | |
| max_depth | INT | |
| max_steps | INT | |
| timeout_ms | BIGINT | |
| thread_count | INT | |
| final_answer | TEXT | output of AgentConsolidator |
| stop_reason | VARCHAR(30) | |
| total_pages | INT | |
| total_results | INT | |
| total_tokens | INT | sum of all tokens consumed |
| errors | TEXT[] | non-fatal error messages |
| created_at | TIMESTAMPTZ | |
| started_at | TIMESTAMPTZ | |
| finished_at | TIMESTAMPTZ | |

### page_visits (V2)
Stores every `PageNode` visit. FK to `crawl_jobs` with `ON DELETE CASCADE`.

Key columns: `job_id`, `thread_id`, `url`, `origin_url`, `depth`,
`link_score`, `status`, `extraction_method`, `content_length`,
`chunk_count`, `failure_reason`, `visited_at`.

### link_candidates (V3)
Stores every `LinkCandidate` evaluated by `AgentRanker`.

Key columns: `job_id`, `source_page_url`, `url`, `anchor_text`,
`dom_context`, `page_title`, `meta_description`, `enrichment_failed`,
`phase1_score`, `phase1_justification`, `final_score`,
`final_justification`, `approved`.

### crawl_results (V4)
Stores every `CrawlResult` collected by threads.

Key columns: `job_id`, `thread_id`, `source_url`, `origin_chain`,
`found_at_depth`, `extracted_content`, `key_facts` (TEXT[]),
`fields` (JSONB), `completeness`, `missing_aspects`.

### agent_calls (V5)
Stores **every** LLM call. Critical for prompt analysis and cost tracking.

Key columns: `job_id`, `agent_role`, `provider`, `model`, `page_url`,
`chunk_index`, `system_prompt`, `user_prompt`, `raw_response`,
`parsed_relevant`, `confidence`, `input_tokens`, `output_tokens`,
`latency_ms`, `http_status`, `error_message`.

---

## 10. LLM Agents

Each agent is a domain service that uses `LLMProviderPort` (injected by CDI)
and persists its calls via `PersistencePort`. All agents call
`rateLimiter.acquire()` before the LLM call, then `onSuccess()` or
`on429()` after.

### AgentVerifier (Agent 1)

Called once per chunk. Determines if a chunk contains relevant content.

**Recommended provider:** Ollama (small model — binary task).

**System prompt:**
```
You are a web content relevance checker.
Determine if a text excerpt from a web page contains information
relevant to answer the user's query.

Reply ONLY with valid JSON, no extra text:
{
  "relevant":      true | false,
  "confidence":    0.0 to 1.0,
  "justification": "1-2 sentence explanation"
}
```

**User prompt includes:**
- `NavigationContext` as JSON
- Chunk index and total (e.g. "Chunk 2 of 5")
- Extraction method (READABILITY / JSOUP / RAW)
- Chunk content

**Failure handling:** If JSON parse fails → log raw content, set
`parseError=true`, treat as `relevant=false`. Never throw.

### AgentExtractor (Agent 2)

Called once per page when `positiveChunks` is not empty.

**Recommended provider:** Anthropic (Claude Haiku) — needs precision.

**System prompt:**
```
You are a specialized content extractor.
Extract information that answers the user's query from web page excerpts.
Preserve precise data: numbers, prices, dates, names.
Do not invent information.

Reply ONLY with valid JSON:
{
  "extractedContent": "structured text",
  "keyFacts":         ["Fact 1", "Fact 2"],
  "fields":           {},
  "completeness":     "COMPLETE | PARTIAL",
  "missingAspects":   "what is missing, or null"
}
```

### AgentRanker (Ranking Agent)

Called twice per page (Phase 1 then Phase 2). Evaluates all links at once
in a single LLM call per phase — never one-by-one.

**Recommended provider:** Ollama (ordering task, not max intelligence).

**System prompt (both phases):**
```
You are a web navigation link ranker.
Score links by relevance (0.0 to 1.0) to find the sought information.
Evaluate links comparatively, not in isolation.

Reply ONLY with a valid JSON array:
[{ "url": "...", "score": 0.0–1.0, "justification": "1 sentence" }]
```

**Phase 1 user prompt:** query + current URL + list of links with
`anchorText` and `domContext`.

**Phase 2 user prompt:** same + `pageTitle` and `metaDescription`
from Jsoup enrichment (may be null if enrichment failed).

### AgentConsolidator (Agent 3)

Called **once** at the end of the session by `CrawlerOrchestrator`.
Receives all `CrawlResult` objects. Deduplicates and writes the final answer.

**Recommended provider:** Anthropic (Claude Sonnet) — highest quality output.

**System prompt:**
```
You are a multi-source information consolidator.
Tasks:
1. Analyze all collected content fragments
2. Identify and remove duplications
3. Identify gaps (PARTIAL fragments)
4. Produce a coherent, complete answer using markdown
5. Include source URL references

Reply ONLY with valid JSON:
{
  "answer": "full markdown answer",
  "fields": {},
  "sources": [
    { "url": "...", "found": true, "confidence": "low|medium|high",
      "pagesVisited": N }
  ],
  "coverageAssessment": "COMPLETE|PARTIAL|INSUFFICIENT",
  "missingAspects": "...or null"
}
```

---

## 11. Navigation Flow

### CrawlerOrchestrator

```
1. Initialize thread-safe globals:
   - ConcurrentHashMap<String, PageNode>  visitRegistry
   - CopyOnWriteArrayList<CrawlResult>    results
   - AtomicInteger                        pagesVisited
   - AtomicInteger                        resultsCount
   - AtomicBoolean                        aborted
   - AtomicBoolean                        timedOut

2. Create root PageNode (depth=0, score=1.0)

3. Schedule global timeout → set timedOut=true after sessionTimeoutMs

4. Submit threadCount CrawlerThread instances to ExecutorService

5. awaitTermination(sessionTimeout + 30s)

6. Determine StopReason (first condition met):
   EXHAUSTED   — all queues drained
   MAX_STEPS   — pagesVisited >= maxSteps
   TIMEOUT     — timedOut == true
   ABORTED     — aborted == true

7. Call AgentConsolidator if results is not empty

8. Persist final CrawlJob state
```

### CrawlerThread — crawl(PageNode node)

```
1. if shouldStop() → return
2. if node.depth > maxDepth → return
3. if registry.markVisited(url, node) != null → return  (atomic)
4. pagesVisited.incrementAndGet()
5. if pagesVisited > maxSteps → return

6. browser.loadPage(url, timeout)
   → on empty: mark DISCARDED, persist, return

7. contentExtractor.extract(html)
   → sets extractedContent + extractionMethod on node

8. chunks = textChunker.chunk(content)

9. for each chunk i:
     rateLimiter.acquire()
     response = agentVerifier.verify(chunk, i, total, context)
     persist AgentCallLog
     if response.relevant → positiveChunks.add(chunk)

10. if positiveChunks not empty:
      rateLimiter.acquire()
      result = agentExtractor.extract(positiveChunks, context)
      persist AgentCallLog
      results.add(result)
      resultsCount.incrementAndGet()
      persist CrawlResult
      // DO NOT return — continue to rank links

11. if shouldStop() → return

12. rawLinks = extract all links from HTML

13. // Ranking Phase 1
    rateLimiter.acquire()
    ranked1 = agentRanker.rankPhase1(rawLinks, context)
    persist AgentCallLog
    phase1Passed = ranked1.filter(score >= preThreshold)
    if phase1Passed.empty → mark DONE, return  ← backtrack

14. // Jsoup enrichment (parallel, silent failures)
    linkEnricher.enrich(phase1Passed)
    persist each LinkCandidate

15. // Ranking Phase 2
    rateLimiter.acquire()
    ranked2 = agentRanker.rankPhase2(phase1Passed, context)
    persist AgentCallLog
    approved = ranked2.filter(score >= finalThreshold)
                      .sorted(descending)
    if approved.empty → mark DONE, return  ← backtrack

16. node.candidateQueue = PriorityQueue(approved)

17. while queue not empty and not shouldStop():
      next = queue.poll()
      if registry.isVisited(next.url) → continue
      child = new PageNode(next.url, node.url, depth+1, ...)
      crawl(child)   ← DFS recursion

18. mark DONE, persist node
```

### shouldStop()

```java
return aborted.get()
    || timedOut.get()
    || pagesVisited.get() > config.maxSteps();
```

---

## 12. Content Extraction Pipeline

Three-layer fallback in `ContentExtractor`. The layer used is recorded as
`ExtractionMethod` and passed to agents via `NavigationContext`.

```
Layer 1 — Readability.js
  Input:  textContent from Playwright page.evaluate()
  Check:  not null AND length >= minContentLength
  Method: READABILITY

Layer 2 — Jsoup cleanup
  Remove tags:    nav, header, footer, aside, script, style, noscript
  Remove by role: navigation, banner, complementary
  Remove classes: .cookie-banner .popup .modal .advertisement .sidebar
  Extract:        document.text()
  Check:          length >= minContentLength
  Method: JSOUP

Layer 3 — Raw body text
  Extract: Jsoup.parse(html).body().text()  (no filtering)
  Log:     WARN with URL — used for algorithm refinement
  Method:  RAW
```

**Readability.js script** (injected by PlaywrightAdapter):

```javascript
const clone = document.cloneNode(true);
const reader = new Readability(clone, {
    charThreshold: 100,
    keepClasses: false
});
const art = reader.parse();
if (!art) return null;
return {
    title: art.title,
    textContent: art.textContent,
    excerpt: art.excerpt,
    siteName: art.siteName,
    length: art.length
};
```

**Chunking with overlap** (`TextChunker`):

```
chunks = []
start = 0
while start < content.length:
    end = min(start + chunkSize, content.length)
    chunks.add(content[start:end])
    if end == content.length: break
    start = end - overlap        ← overlap repeats last N chars
return chunks
```

---

## 13. Link Ranking Pipeline

```
Page HTML
    ↓
Extract all <a> tags → List<RawLink> (url, anchorText, domContext, ariaLabel)
    ↓
AgentRanker.rankPhase1(rawLinks, context)
    → one LLM call with ALL links
    → filter: score >= preThreshold (default 0.40)
    ↓
JsoupLinkEnricher.enrich(phase1Passed)  ← parallel HTTP, silent failures
    → adds pageTitle, metaDescription to each LinkCandidate
    ↓
AgentRanker.rankPhase2(enrichedLinks, context)
    → one LLM call with ALL enriched links
    → filter: score >= finalThreshold (default 0.60)
    ↓
PriorityQueue<LinkCandidate>  ← sorted descending by finalScore
```

---

## 14. Rate Limiting and Backoff

`RateLimiter` is instantiated **per LLM provider** and shared across all
threads for that provider.

```
acquire():
  synchronized:
    elapsed = now - lastCallTimestamp
    if elapsed < llmDelayMs: sleep(llmDelayMs - elapsed)
    lastCallTimestamp = now

on429():
  attempt = consecutive429.incrementAndGet()
  if attempt > maxConsecutive429:
    aborted.set(true)
    return
  penalty = backoff429Seconds[min(attempt-1, array.length-1)]
  sleep(penalty * 1000)

onSuccess():
  consecutive429.set(0)   ← reset on every successful call
```

Default backoff schedule: 30 s → 60 s → 120 s → abort.

---

## 15. Concurrency Model

| Structure | Type | Purpose |
|---|---|---|
| Visit registry | `ConcurrentHashMap<String, PageNode>` | Atomic URL deduplication via `putIfAbsent()` |
| Results | `CopyOnWriteArrayList<CrawlResult>` | Thread-safe result collection |
| Pages visited | `AtomicInteger` | `maxSteps` enforcement |
| Results count | `AtomicInteger` | Optional max-results limit |
| Abort flag | `AtomicBoolean` | Signals fatal 429 to all threads |
| Timeout flag | `AtomicBoolean` | Set by `ScheduledExecutor` |
| Rate limiter | Per-provider singleton | Serializes LLM calls with min delay |

**Important:** The Playwright browser is `@ApplicationScoped` (CDI singleton).
Each thread opens its own `Page` (browser tab) and closes it after use.
Do not create a new `Browser` instance per request.

---

## 16. Code Quality Rules

Enforced by **Checkstyle** on every `mvn validate`.

| Rule | Setting |
|---|---|
| Line length | max 80 columns |
| Javadoc | Required on all public classes |
| Javadoc | Required on all public and protected methods |
| Javadoc | `@param` and `@return` required when applicable |
| Parameters | Must be `final` |
| Local variables | Must be `final` if not reassigned |
| Imports | No wildcard imports (`.*`) |
| Braces | Required on all `if/else/for/while` blocks |
| Statements | One per line |

**Example of compliant method:**

```java
/**
 * Divides content into chunks with overlap between adjacent chunks.
 *
 * @param content the text to divide
 * @return immutable list of chunks
 */
public List<String> chunk(final String content) {
    final List<String> chunks = new ArrayList<>();
    int start = 0;
    while (start < content.length()) {
        final int end = Math.min(
            start + chunkSize,
            content.length()
        );
        chunks.add(content.substring(start, end));
        if (end == content.length()) {
            break;
        }
        start = end - overlap;
    }
    return Collections.unmodifiableList(chunks);
}
```

---

## 17. Testing Strategy

### Unit tests (no I/O, no framework)

Test every domain class in isolation with mocks for ports.

| Class | What to test | Mocks needed |
|---|---|---|
| `TextChunker` | Overlap, edge cases, empty input | None |
| `VisitRegistry` | Atomic insert, concurrency (100 threads) | None |
| `RateLimiter` | Delay, backoff steps, abort on max429 | None |
| `ContentExtractor` | Each layer triggered correctly | `BrowserPort` |
| `AgentVerifier` | JSON parse, parse failure fallback | `LLMProviderPort`, `PersistencePort` |
| `AgentRanker` | Phase 1 filter, enrichment failure, Phase 2 | `LLMProviderPort`, `LinkEnricherPort` |
| `CrawlerThread` | shouldStop, deduplication, result collection | All ports |

### Integration tests (`@QuarkusTest`)

- Use **Quarkus Dev Services** (auto-starts PostgreSQL via Testcontainers)
- Mock `BrowserPort` and `LLMProviderPort` with `@InjectMock`
- Cover all 3 REST endpoints with RestAssured

### End-to-end test

Uses real Playwright + real LLM (Ollama for cheap agents, Anthropic for
extractor/consolidator). Configure with `application-e2e.properties`:

```properties
horizon.crawler.max-depth=2
horizon.crawler.max-steps=5
horizon.crawler.thread-count=1
```

---

## 18. Decision Log

| Decision | Rationale |
|---|---|
| Hexagonal architecture | Allows swapping any LLM provider or browser engine without touching domain logic; makes unit testing trivial |
| DFS with backtracking | Explores depth before breadth; respects link priority queue; natural recursion with backtracking when queues drain |
| Per-page priority queue | Keeps ranked candidates available for backtracking; threads don't compete for a global queue |
| Readability.js via Playwright | Runs on the already-rendered DOM; avoids re-fetching; best extraction quality for editorial content |
| Three-layer extraction fallback | Sites vary enormously; graceful degradation ensures the system always produces some content for the LLM to evaluate |
| Chunking with overlap | Prevents information loss at chunk boundaries; overlap is configurable |
| Single LLM call per phase for all links | Enables comparative ranking; far cheaper than per-link calls |
| Per-provider RateLimiter | Ollama (local) has no rate limit; Anthropic does; mixing them in a single limiter would be wasteful |
| agent_calls table stores full prompts | Enables offline prompt quality analysis and cost tracking — essential for iterative improvement |
| Threads continue after finding a result | Multiple sources produce a richer consolidated answer; the AgentConsolidator deduplicates |
