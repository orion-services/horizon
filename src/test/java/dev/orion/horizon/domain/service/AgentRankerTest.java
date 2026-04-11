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
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
 * Testes unitários do {@link AgentRanker} — fase única com enriquecimento
 * prévio.
 */
final class AgentRankerTest {

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
    void linksAboveThresholdAppearInResult() {
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
    void linksBelowFinalThresholdAreDiscarded() {
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
    void enrichmentIsCalledBeforeLlm() {
        llmProvider.rawContent =
                "[{\"url\": \"https://example.com/c\","
                + " \"score\": 0.75, \"justification\": \"relevant\"}]";
        enricher.injectTitle = "Página Enriquecida";

        final List<LinkCandidate> candidates = new ArrayList<>(List.of(
                new LinkCandidate(
                        "https://example.com/c",
                        "Link C", null, null,
                        null, null, null, null, false, null, null)
        ));

        ranker.rank(
                UUID.randomUUID(), "t1",
                "query", "https://example.com",
                candidates
        );

        assertTrue(enricher.wasCalled,
                "enricher deve ser chamado antes do LLM");
        assertEquals(1, llmProvider.callCount,
                "deve haver exatamente uma chamada ao LLM");
    }

    @Test
    void enrichmentFailureStillRanks() {
        llmProvider.rawContent =
                "[{\"url\": \"https://example.com/c\","
                + " \"score\": 0.75, \"justification\": \"relevant\"}]";
        enricher.fail = true;

        final List<LinkCandidate> candidates = new ArrayList<>(List.of(
                new LinkCandidate(
                        "https://example.com/c",
                        "Link C", null, null,
                        null, null, null, null, false, null, null)
        ));

        final PriorityQueue<LinkCandidate> result =
                ranker.rank(
                        UUID.randomUUID(), "t1",
                        "query", "https://example.com",
                        candidates
                );

        assertFalse(result.isEmpty(),
                "deve ranquear mesmo com falha no enriquecimento");
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

    // -----------------------------------------------------------------------
    // Pré-filtro léxico
    // -----------------------------------------------------------------------

    @Test
    void lexicalPreFilterApprovesCandidateWithQueryTokenInUrl() {
        llmProvider.rawContent = "[]";

        final List<LinkCandidate> candidates = List.of(
                new LinkCandidate(
                        "https://example.com/topicos/jwt/jwt.html",
                        "JSON Web Token", null, null,
                        null, null, null, null, false, null, null)
        );

        final PriorityQueue<LinkCandidate> result =
                ranker.rank(
                        UUID.randomUUID(), "t1",
                        "jwt authentication", "https://example.com",
                        candidates
                );

        assertFalse(result.isEmpty(),
                "candidato com token da query na URL deve ser aprovado pelo "
                + "pré-filtro");
        assertEquals(0, llmProvider.callCount,
                "LLM não deve ser chamado quando todos os candidatos passam "
                + "pelo pré-filtro");
        assertEquals(AgentRanker.LEXICAL_SYNTHETIC_SCORE,
                result.poll().finalScore(), 0.001,
                "score sintético do pré-filtro deve ser "
                + "LEXICAL_SYNTHETIC_SCORE");
    }

    @Test
    void lexicalPreFilterSendsNonMatchingCandidatesToLlm() {
        llmProvider.rawContent =
                "[{\"url\": \"https://example.com/unrelated\","
                + " \"score\": 0.7, \"justification\": \"ok\"}]";

        final List<LinkCandidate> candidates = List.of(
                new LinkCandidate(
                        "https://example.com/topicos/jwt/jwt.html",
                        "JSON Web Token", null, null,
                        null, null, null, null, false, null, null),
                new LinkCandidate(
                        "https://example.com/unrelated",
                        "Unrelated Page", null, null,
                        null, null, null, null, false, null, null)
        );

        final PriorityQueue<LinkCandidate> result =
                ranker.rank(
                        UUID.randomUUID(), "t1",
                        "jwt", "https://example.com",
                        candidates
                );

        assertEquals(1, llmProvider.callCount,
                "LLM deve ser chamado apenas para candidatos sem match léxico");
        assertEquals(2, result.size(),
                "deve retornar o match léxico + o aprovado pelo LLM");
    }

    @Test
    void lexicalPreFilterIgnoresShortTokens() {
        llmProvider.rawContent = "[]";

        final List<LinkCandidate> candidates = List.of(
                new LinkCandidate(
                        "https://example.com/ao/um",
                        "Artigo", null, null,
                        null, null, null, null, false, null, null)
        );

        final PriorityQueue<LinkCandidate> result =
                ranker.rank(
                        UUID.randomUUID(), "t1",
                        "o a um",
                        "https://example.com",
                        candidates
                );

        assertTrue(result.isEmpty(),
                "tokens com menos de 3 chars não devem causar match léxico");
        assertEquals(1, llmProvider.callCount,
                "candidato sem match léxico deve ir ao LLM");
    }

    @Test
    void queryTokensExtractsSignificantTokens() {
        final List<String> tokens =
                AgentRanker.queryTokens("o que esse site fala sobre jwt?");
        assertTrue(tokens.contains("jwt"),
                "deve conter 'jwt'");
        assertFalse(tokens.contains("o"),
                "não deve conter tokens de 1 char");
        assertFalse(tokens.contains("a"),
                "não deve conter tokens de 1 char");
    }

    @Test
    void lexicalSplitSeparatesMatchesFromRemainder() {
        final List<LinkCandidate> candidates = List.of(
                new LinkCandidate("https://example.com/jwt", "JWT page",
                        null, null, null, null, null, null, false, null, null),
                new LinkCandidate("https://example.com/other", "Other",
                        null, null, null, null, null, null, false, null, null)
        );

        final List<LinkCandidate> approved = new ArrayList<>();
        final List<LinkCandidate> remaining =
                AgentRanker.lexicalSplit(candidates, "jwt token", approved);

        assertEquals(1, approved.size(), "um candidato deve passar no filtro");
        assertEquals(1, remaining.size(), "um candidato deve ir ao LLM");
        assertEquals("https://example.com/jwt", approved.get(0).url());
        assertNotNull(approved.get(0).finalJustification());
    }

    // -----------------------------------------------------------------------
    // Recuperação de JSON truncado
    // -----------------------------------------------------------------------

    @Test
    void truncatedJsonResponseRecoveriesPartialEntries() {
        llmProvider.rawContent =
                "[{\"url\": \"https://example.com/a\","
                + " \"score\": 0.8, \"justification\": \"good\"},"
                + "{\"url\": \"https://example.com/b\","
                + " \"score\": 0.7, \"justification\": \"truncated...";

        final List<LinkCandidate> candidates = List.of(
                new LinkCandidate(
                        "https://example.com/a",
                        "Page Alpha", null, null,
                        null, null, null, null, false, null, null),
                new LinkCandidate(
                        "https://example.com/b",
                        "Page Beta", null, null,
                        null, null, null, null, false, null, null)
        );

        final PriorityQueue<LinkCandidate> result =
                ranker.rank(
                        UUID.randomUUID(), "t1",
                        "quarkus microservices", "https://example.com",
                        candidates
                );

        assertFalse(result.isEmpty(),
                "entradas completas antes do truncamento devem ser "
                + "recuperadas");
        final LinkCandidate recovered = result.poll();
        assertEquals("https://example.com/a", recovered.url(),
                "a entrada completa antes do truncamento deve ser "
                + "incluída");
        assertEquals(0.8, recovered.finalScore(), 0.001);
    }

    @Test
    void totallyTruncatedJsonReturnsEmptyWithoutException() {
        llmProvider.rawContent = "[{\"url\": \"https://example.com/a\", \"score";

        final List<LinkCandidate> candidates = List.of(
                new LinkCandidate(
                        "https://example.com/a",
                        "Link A", null, null,
                        null, null, null, null, false, null, null)
        );

        final PriorityQueue<LinkCandidate> result =
                ranker.rank(
                        UUID.randomUUID(), "t1",
                        "query", "https://example.com",
                        candidates
                );

        assertTrue(result.isEmpty(),
                "JSON sem nenhuma entrada completa deve retornar vazio sem "
                + "lançar exceção");
    }

    @Test
    void extractJsonArrayRecoversFromTruncation() {
        final String truncated =
                "[{\"url\": \"a\", \"score\": 0.9, "
                + "\"justification\": \"ok\"},"
                + "{\"url\": \"b\", \"score\": 0.8, "
                + "\"justification\": \"truncat";
        final String recovered = AgentRanker.extractJsonArray(truncated);
        assertTrue(recovered.endsWith("]"),
                "o JSON recuperado deve terminar com ']'");
        assertTrue(recovered.contains("\"url\": \"a\""),
                "deve conter a primeira entrada completa");
    }

    @Test
    void approvedCandidateHasFinalScoreSet() {
        llmProvider.rawContent =
                "[{\"url\": \"https://example.com/x\","
                + " \"score\": 0.9, \"justification\": \"best\"}]";

        final List<LinkCandidate> candidates = List.of(
                new LinkCandidate(
                        "https://example.com/x",
                        "Link X", null, null,
                        null, null, null, null, false, null, null)
        );

        final PriorityQueue<LinkCandidate> result =
                ranker.rank(
                        UUID.randomUUID(), "t1",
                        "query", "https://example.com",
                        candidates
                );

        final LinkCandidate approved = result.poll();
        assertEquals(0.9, approved.finalScore(), 0.001);
        assertEquals("best", approved.finalJustification());
    }

    /**
     * Stub LLM provider para testes de AgentRanker.
     */
    static final class StubLLMProvider implements LLMProviderPort {
        private String rawContent = "[]";
        private int callCount = 0;

        @Override
        public AgentResponse call(final LLMRequest request) {
            callCount++;
            return AgentResponse.builder()
                    .rawContent(rawContent)
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
        private boolean wasCalled = false;
        private String injectTitle = null;

        @Override
        public void enrich(
                final List<LinkCandidate> candidates,
                final long timeoutMs) {
            wasCalled = true;
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
            } else if (injectTitle != null) {
                for (int i = 0; i < candidates.size(); i++) {
                    final LinkCandidate c = candidates.get(i);
                    candidates.set(i, new LinkCandidate(
                            c.url(), c.anchorText(), c.domContext(),
                            c.ariaLabel(), c.phase1Score(),
                            c.phase1Justification(), injectTitle,
                            c.metaDescription(), false,
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
