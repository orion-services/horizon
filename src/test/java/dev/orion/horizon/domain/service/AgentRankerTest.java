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
import dev.orion.horizon.domain.model.AgentCallLog;
import dev.orion.horizon.domain.model.AgentResponse;
import dev.orion.horizon.domain.model.CrawlJob;
import dev.orion.horizon.domain.model.CrawlResult;
import dev.orion.horizon.domain.model.LinkCandidate;
import dev.orion.horizon.domain.model.LLMRequest;
import dev.orion.horizon.domain.model.PageNode;
import dev.orion.horizon.domain.port.out.LinkEnricherPort;
import dev.orion.horizon.domain.port.out.LLMProviderPort;
import dev.orion.horizon.domain.port.out.PersistencePort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários do {@link AgentRanker}.
 */
final class AgentRankerTest {

    private static final double PRE_THRESHOLD = 0.4;
    private static final double FINAL_THRESHOLD = 0.6;

    private StubLLMProvider llmProvider;
    private StubEnricher enricher;
    private StubPersistence persistence;
    private AgentRanker ranker;

    @BeforeEach
    void setUp() {
        llmProvider = new StubLLMProvider();
        enricher = new StubEnricher();
        persistence = new StubPersistence();
        ranker = new AgentRanker(
                llmProvider,
                enricher,
                persistence,
                new ObjectMapper(),
                new RateLimiter(0, List.of(), 3, ms -> { }),
                1024,
                PRE_THRESHOLD,
                FINAL_THRESHOLD,
                5000L
        );
    }

    @Test
    void emptyInputReturnsEmptyQueue() {
        final PriorityQueue<LinkCandidate> result =
                ranker.rank(
                        UUID.randomUUID(), "t1",
                        "query", "https://example.com",
                        List.of()
                );
        assertTrue(result.isEmpty());
    }

    @Test
    void linksAboveThresholdsAppearsInResult() {
        llmProvider.rawContent =
                "[{\"url\": \"https://example.com/a\","
                + " \"score\": 0.8, \"justification\": \"good\"},"
                + "{\"url\": \"https://example.com/b\","
                + " \"score\": 0.7, \"justification\": \"ok\"}]";

        final List<LinkCandidate> candidates = List.of(
                new LinkCandidate(
                        "https://example.com/a",
                        "Link A", null, null,
                        null, null, null, null, false, null, null),
                new LinkCandidate(
                        "https://example.com/b",
                        "Link B", null, null,
                        null, null, null, null, false, null, null)
        );

        final PriorityQueue<LinkCandidate> result =
                ranker.rank(
                        UUID.randomUUID(), "t1",
                        "query", "https://example.com",
                        candidates
                );

        assertFalse(result.isEmpty());
    }

    @Test
    void linksBelowPreThresholdAreDiscarded() {
        llmProvider.rawContent =
                "[{\"url\": \"https://example.com/low\","
                + " \"score\": 0.1, \"justification\": \"irrelevant\"}]";

        final List<LinkCandidate> candidates = List.of(
                new LinkCandidate(
                        "https://example.com/low",
                        "Low link", null, null,
                        null, null, null, null, false, null, null)
        );

        final PriorityQueue<LinkCandidate> result =
                ranker.rank(
                        UUID.randomUUID(), "t1",
                        "query", "https://example.com",
                        candidates
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void enrichmentFailureUsesPhase1Score() {
        llmProvider.rawContentPhase1 =
                "[{\"url\": \"https://example.com/c\","
                + " \"score\": 0.75, \"justification\": \"relevant\"}]";
        llmProvider.rawContentPhase2 =
                "[{\"url\": \"https://example.com/c\","
                + " \"score\": 0.65, \"justification\": \"still good\"}]";
        enricher.fail = true;

        final List<LinkCandidate> candidates = List.of(
                new LinkCandidate(
                        "https://example.com/c",
                        "Link C", null, null,
                        null, null, null, null, false, null, null)
        );

        final PriorityQueue<LinkCandidate> result =
                ranker.rank(
                        UUID.randomUUID(), "t1",
                        "query", "https://example.com",
                        candidates
                );

        assertFalse(result.isEmpty());
    }

    @Test
    void resultQueueOrderedByScoreDescending() {
        llmProvider.rawContent =
                "[{\"url\": \"https://example.com/low\","
                + " \"score\": 0.65, \"justification\": \"ok\"},"
                + "{\"url\": \"https://example.com/high\","
                + " \"score\": 0.85, \"justification\": \"best\"}]";

        final List<LinkCandidate> candidates = List.of(
                new LinkCandidate(
                        "https://example.com/low",
                        "Low", null, null,
                        null, null, null, null, false, null, null),
                new LinkCandidate(
                        "https://example.com/high",
                        "High", null, null,
                        null, null, null, null, false, null, null)
        );

        final PriorityQueue<LinkCandidate> result =
                ranker.rank(
                        UUID.randomUUID(), "t1",
                        "query", "https://example.com",
                        candidates
                );

        final LinkCandidate first = result.poll();
        assertEquals("https://example.com/high", first.url());
    }

    /**
     * Stub LLM provider para testes de AgentRanker.
     */
    static final class StubLLMProvider implements LLMProviderPort {
        private String rawContent = "[]";
        private String rawContentPhase1 = null;
        private String rawContentPhase2 = null;
        private int callCount = 0;

        @Override
        public AgentResponse call(final LLMRequest request) {
            callCount++;
            final String content;
            if (rawContentPhase1 != null && callCount == 1) {
                content = rawContentPhase1;
            } else if (rawContentPhase2 != null && callCount == 2) {
                content = rawContentPhase2;
            } else {
                content = rawContent;
            }
            return AgentResponse.builder()
                    .rawContent(content)
                    .providerUsed("STUB")
                    .modelUsed("stub-model")
                    .inputTokens(10)
                    .outputTokens(5)
                    .latencyMs(100L)
                    .httpStatus(200)
                    .build();
        }

        @Override
        public String getProviderName() {
            return "STUB";
        }

        @Override
        public String getModelName() {
            return "stub-model";
        }
    }

    /**
     * Stub LinkEnricher para testes.
     */
    static final class StubEnricher implements LinkEnricherPort {
        private boolean fail = false;

        @Override
        public void enrich(
                final List<LinkCandidate> candidates,
                final long timeoutMs) {
            if (fail) {
                for (int i = 0; i < candidates.size(); i++) {
                    final LinkCandidate c = candidates.get(i);
                    candidates.set(i, new LinkCandidate(
                            c.url(), c.anchorText(), c.domContext(),
                            c.ariaLabel(), c.phase1Score(),
                            c.phase1Justification(), c.pageTitle(),
                            c.metaDescription(), true,
                            c.finalScore(), c.finalJustification()
                    ));
                }
            }
        }
    }

    /**
     * Stub PersistencePort para testes.
     */
    static final class StubPersistence implements PersistencePort {
        private final List<AgentCallLog> savedLogs = new ArrayList<>();

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
