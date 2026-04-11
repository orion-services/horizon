/*
 * Copyright 2026 Orion Services.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.orion.horizon.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.orion.horizon.adapter.out.browser.ContentExtractor;
import dev.orion.horizon.domain.model.AgentCallLog;
import dev.orion.horizon.domain.model.AgentResponse;
import dev.orion.horizon.domain.model.CrawlJob;
import dev.orion.horizon.domain.model.CrawlParameters;
import dev.orion.horizon.domain.model.CrawlResult;
import dev.orion.horizon.domain.model.LinkCandidate;
import dev.orion.horizon.domain.model.LLMRequest;
import dev.orion.horizon.domain.model.PageNode;
import dev.orion.horizon.domain.model.PageStatus;
import dev.orion.horizon.domain.port.out.BrowserPort;
import dev.orion.horizon.domain.port.out.LinkEnricherPort;
import dev.orion.horizon.domain.port.out.LLMProviderPort;
import dev.orion.horizon.domain.port.out.PersistencePort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários do {@link CrawlerThread} com todos os ports mockados.
 */
final class CrawlerThreadTest {

    private static final String BASE_URL = "https://example.com";
    private static final String HTML_WITH_LINK =
            "<html><body>"
            + "<nav aria-label=\"Categorias\">"
            + "<ul>"
            + "<li><a href=\"https://example.com/page2\""
            + " aria-label=\"Ver Page 2\">Page 2</a></li>"
            + "</ul>"
            + "</nav>"
            + "<p>Content</p>"
            + "</body></html>";

    private StubBrowser browser;
    private StubPersistence persistence;
    private AtomicBoolean aborted;
    private AtomicBoolean timedOut;
    private AtomicInteger pagesVisited;
    private AtomicInteger activeWorkers;
    private CopyOnWriteArrayList<CrawlResult> results;
    private VisitRegistry visitRegistry;
    private CrawlParameters parameters;
    private BlockingQueue<PageNode> workQueue;

    @BeforeEach
    void setUp() {
        browser = new StubBrowser(HTML_WITH_LINK);
        persistence = new StubPersistence();
        aborted = new AtomicBoolean(false);
        timedOut = new AtomicBoolean(false);
        pagesVisited = new AtomicInteger(0);
        activeWorkers = new AtomicInteger(0);
        results = new CopyOnWriteArrayList<>();
        visitRegistry = new VisitRegistry();
        workQueue = new LinkedBlockingQueue<>();
        parameters = new CrawlParameters(
                2, 10, 60000L, 1, false,
                10, 100, 10, 5000L, 2000L,
                0.6, 0, List.of(), 3
        );
    }

    @Test
    void shouldStopReturnsTrueWhenAborted() {
        aborted.set(true);

        final CrawlerThread thread =
                buildThread(true, new StubRankerProvider());

        assertTrue(thread.shouldStop());
    }

    @Test
    void shouldStopReturnsTrueWhenTimedOut() {
        timedOut.set(true);

        final CrawlerThread thread =
                buildThread(false, new StubRankerProvider());

        assertTrue(thread.shouldStop());
    }

    @Test
    void alreadyVisitedUrlSkipped() {
        final PageNode root = minimalNode(BASE_URL, 0);
        visitRegistry.markVisited(BASE_URL, root);
        workQueue.add(root);

        final CrawlerThread thread =
                buildThread(true, new StubRankerProvider());
        thread.run();

        assertEquals(0, pagesVisited.get());
    }

    @Test
    void browserFailureResultsInDiscardedStatus() {
        final StubBrowser failBrowser = new StubBrowser(null);
        workQueue.add(minimalNode(BASE_URL, 0));

        final CrawlerThread thread =
                buildThread(true, new StubRankerProvider(), failBrowser);
        thread.run();

        assertTrue(pagesVisited.get() > 0);
        assertTrue(results.isEmpty());
    }

    @Test
    void relevantChunksProduceResults() {
        workQueue.add(minimalNode(BASE_URL, 0));

        final CrawlerThread thread =
                buildThread(true, new StubRankerProvider());
        thread.run();

        assertTrue(pagesVisited.get() > 0);
        assertTrue(results.size() > 0);
    }

    @Test
    void irrelevantChunksProduceNoResults() {
        workQueue.add(minimalNode(BASE_URL, 0));

        final CrawlerThread thread =
                buildThread(false, new StubRankerProvider());
        thread.run();

        assertTrue(pagesVisited.get() > 0);
        assertTrue(results.isEmpty());
    }

    @Test
    void sameDocumentUnifiesRootWithAndWithoutTrailingSlash() {
        assertTrue(CrawlerThread.isSameDocument(
                "https://www.example.com",
                "https://www.example.com/"));
    }

    @Test
    void sameDocumentFalseForDifferentPaths() {
        assertFalse(CrawlerThread.isSameDocument(
                "https://example.com/a",
                "https://example.com/b"));
    }

    @Test
    void depthLimitPreventsChildrenFromBeingEnqueued() {
        final CrawlParameters shallow = new CrawlParameters(
                0, 10, 60000L, 1, false,
                10, 100, 10, 5000L, 2000L,
                0.6, 0, List.of(), 3
        );
        workQueue.add(minimalNode(BASE_URL, 0));

        final CrawlerThread thread = new CrawlerThread(
                UUID.randomUUID(), "t-test",
                "test query",
                shallow,
                browser,
                new ContentExtractor(10),
                new TextChunker(100, 10),
                buildVerifier(true),
                buildExtractor(),
                buildRanker(new StubRankerProvider()),
                visitRegistry,
                persistence,
                aborted, timedOut, pagesVisited, results,
                workQueue, activeWorkers
        );
        thread.run();

        assertEquals(1, visitRegistry.size());
    }

    private CrawlerThread buildThread(
            final boolean relevant,
            final StubRankerProvider rankerProvider) {
        return buildThread(relevant, rankerProvider, browser);
    }

    private CrawlerThread buildThread(
            final boolean relevant,
            final StubRankerProvider rankerProvider,
            final BrowserPort browserPort) {
        return new CrawlerThread(
                UUID.randomUUID(), "t-test",
                "test query",
                parameters,
                browserPort,
                new ContentExtractor(10),
                new TextChunker(100, 10),
                buildVerifier(relevant),
                buildExtractor(),
                buildRanker(rankerProvider),
                visitRegistry,
                persistence,
                aborted, timedOut, pagesVisited, results,
                workQueue, activeWorkers
        );
    }

    private AgentVerifier buildVerifier(final boolean relevant) {
        final String json = relevant
                ? "{\"relevant\":true,\"confidence\":0.9}"
                : "{\"relevant\":false,\"confidence\":0.1}";
        return new AgentVerifier(
                new FixedLLMProvider(json),
                persistence,
                new ObjectMapper(),
                new RateLimiter(0, List.of(), 3, ms -> { }),
                512
        );
    }

    private AgentExtractor buildExtractor() {
        final String json =
                "{\"extractedContent\": \"Content\","
                + " \"keyFacts\": [],"
                + " \"fields\": {},"
                + " \"completeness\": \"COMPLETE\","
                + " \"missingAspects\": null}";
        return new AgentExtractor(
                new FixedLLMProvider(json),
                persistence,
                new ObjectMapper(),
                new RateLimiter(0, List.of(), 3, ms -> { }),
                1000
        );
    }

    private AgentRanker buildRanker(
            final StubRankerProvider rankerProvider) {
        return new AgentRanker(
                rankerProvider,
                new StubEnricher(),
                persistence,
                new ObjectMapper(),
                new RateLimiter(0, List.of(), 3, ms -> { }),
                1024,
                0.6, 2000L
        );
    }

    private static PageNode minimalNode(final String url, final int depth) {
        return new PageNode(
                url, null, depth, null, null,
                null, null, null, null, null,
                PageStatus.PENDING, null, null, null
        );
    }

    /**
     * Stub do BrowserPort para testes.
     */
    static final class StubBrowser implements BrowserPort {
        private final String html;

        private StubBrowser(final String html) {
            this.html = html;
        }

        @Override
        public Optional<String> loadPage(
                final String url, final long timeoutMs) {
            return Optional.ofNullable(html);
        }
    }

    /**
     * Stub do LLMProviderPort para ranker.
     */
    static final class StubRankerProvider implements LLMProviderPort {
        @Override
        public AgentResponse call(final LLMRequest request) {
            return AgentResponse.builder()
                    .rawContent("[]")
                    .providerUsed("STUB")
                    .modelUsed("stub")
                    .inputTokens(1)
                    .outputTokens(1)
                    .latencyMs(1L)
                    .httpStatus(200)
                    .build();
        }

        @Override
        public String getProviderName() {
            return "STUB";
        }

        @Override
        public String getModelName() {
            return "stub";
        }
    }

    /**
     * Stub com resposta fixa para LLM.
     */
    static final class FixedLLMProvider implements LLMProviderPort {
        private final String response;

        private FixedLLMProvider(final String response) {
            this.response = response;
        }

        @Override
        public AgentResponse call(final LLMRequest request) {
            return AgentResponse.builder()
                    .rawContent(response)
                    .providerUsed("STUB")
                    .modelUsed("stub")
                    .inputTokens(1)
                    .outputTokens(1)
                    .latencyMs(1L)
                    .httpStatus(200)
                    .build();
        }

        @Override
        public String getProviderName() {
            return "STUB";
        }

        @Override
        public String getModelName() {
            return "stub";
        }
    }

    /**
     * Stub do LinkEnricherPort para testes.
     */
    static final class StubEnricher implements LinkEnricherPort {
        @Override
        public void enrich(
                final List<LinkCandidate> candidates,
                final long timeoutMs) {
        }
    }

    /**
     * Stub de persistência que não faz nada.
     */
    static final class NoOpPersistence implements PersistencePort {
        @Override
        public void saveJob(final CrawlJob job) { }

        @Override
        public void updateJob(final CrawlJob job) { }

        @Override
        public void savePageVisit(final UUID jobId, final PageNode node) { }

        @Override
        public void saveLinkCandidate(
                final UUID jobId, final String sourceUrl,
                final LinkCandidate link) { }

        @Override
        public void saveCrawlResult(
                final UUID jobId, final CrawlResult result) { }

        @Override
        public void saveAgentCall(
                final UUID jobId, final AgentCallLog log) { }

        @Override
        public Optional<CrawlJob> findJobById(final UUID jobId) {
            return Optional.empty();
        }

        @Override
        public List<String[]> exportJobAsCsvRows(final UUID jobId) {
            return List.of();
        }
    }

    /**
     * Stub do PersistencePort que registra saves.
     */
    static final class StubPersistence implements PersistencePort {
        private final List<AgentCallLog> savedLogs = new ArrayList<>();
        private final List<CrawlResult> savedResults = new ArrayList<>();

        @Override
        public void saveJob(final CrawlJob job) { }

        @Override
        public void updateJob(final CrawlJob job) { }

        @Override
        public void savePageVisit(final UUID jobId, final PageNode node) { }

        @Override
        public void saveLinkCandidate(
                final UUID jobId, final String sourceUrl,
                final LinkCandidate link) { }

        @Override
        public void saveCrawlResult(
                final UUID jobId, final CrawlResult result) {
            savedResults.add(result);
        }

        @Override
        public void saveAgentCall(
                final UUID jobId, final AgentCallLog log) {
            savedLogs.add(log);
        }

        @Override
        public Optional<CrawlJob> findJobById(final UUID jobId) {
            return Optional.empty();
        }

        @Override
        public List<String[]> exportJobAsCsvRows(final UUID jobId) {
            return List.of();
        }
    }
}
