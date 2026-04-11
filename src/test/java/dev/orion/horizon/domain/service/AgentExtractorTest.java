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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.orion.horizon.domain.model.AgentCallLog;
import dev.orion.horizon.domain.model.AgentResponse;
import dev.orion.horizon.domain.model.CrawlJob;
import dev.orion.horizon.domain.model.CrawlResult;
import dev.orion.horizon.domain.model.ExtractionMethod;
import dev.orion.horizon.domain.model.LinkCandidate;
import dev.orion.horizon.domain.model.LLMRequest;
import dev.orion.horizon.domain.model.NavigationContext;
import dev.orion.horizon.domain.model.PageNode;
import dev.orion.horizon.domain.port.out.LLMProviderPort;
import dev.orion.horizon.domain.port.out.PersistencePort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários do {@link AgentExtractor}.
 */
final class AgentExtractorTest {

    private StubLLMProvider llmProvider;
    private StubPersistence persistence;
    private AgentExtractor extractor;

    @BeforeEach
    void setUp() {
        llmProvider = new StubLLMProvider();
        persistence = new StubPersistence();
        extractor = new AgentExtractor(
                llmProvider,
                persistence,
                new ObjectMapper(),
                new RateLimiter(0, List.of(), 3, ms -> { }),
                1000
        );
    }

    @Test
    void validJsonFillsCrawlResultCorrectly() {
        llmProvider.rawContent =
                "{\"extractedContent\": \"Conteúdo extraído\","
                + " \"keyFacts\": [\"Fato 1\", \"Fato 2\"],"
                + " \"fields\": {},"
                + " \"completeness\": \"COMPLETE\","
                + " \"missingAspects\": null}";

        final CrawlResult result = extractor.extract(
                UUID.randomUUID(), "t1",
                buildContext(),
                List.of("chunk positivo"),
                List.of("https://example.com"),
                0.8
        );

        assertNotNull(result);
        assertEquals("Conteúdo extraído", result.extractedContent);
        assertEquals(2, result.keyFacts.size());
        assertEquals(1.0, result.completeness);
    }

    @Test
    void partialCompletenessPreserved() {
        llmProvider.rawContent =
                "{\"extractedContent\": \"Parcial\","
                + " \"keyFacts\": [],"
                + " \"fields\": {},"
                + " \"completeness\": \"PARTIAL\","
                + " \"missingAspects\": \"Faltam dados\"}";

        final CrawlResult result = extractor.extract(
                UUID.randomUUID(), "t1",
                buildContext(),
                List.of("chunk"),
                List.of(),
                0.5
        );

        assertEquals(0.5, result.completeness);
        assertEquals("Faltam dados", result.missingAspects);
    }

    @Test
    void invalidJsonReturnsCrawlResultWithRawContent() {
        llmProvider.rawContent = "NOT VALID JSON!!!";

        final CrawlResult result = extractor.extract(
                UUID.randomUUID(), "t1",
                buildContext(),
                List.of("chunk"),
                List.of(),
                0.5
        );

        assertNotNull(result);
        assertNotNull(result.extractedContent);
    }

    @Test
    void jsonWithTrailingTextContainingBracesStillParses() {
        llmProvider.rawContent =
                "{\"extractedContent\": \"JWT\","
                + " \"keyFacts\": [],"
                + " \"fields\": {},"
                + " \"completeness\": \"PARTIAL\","
                + " \"missingAspects\": null}\n"
                + "Nota: grupos usam { e } para delimitar.";

        final CrawlResult result = extractor.extract(
                UUID.randomUUID(), "t1",
                buildContext(),
                List.of("chunk"),
                List.of(),
                0.5
        );

        assertEquals("JWT", result.extractedContent);
    }

    @Test
    void extractedContentNestedObjectIsSerializedToString() {
        llmProvider.rawContent =
                "{\"extractedContent\": {"
                + " \"definicao\": \"JWT assina tokens\","
                + " \"estrutura\": \"header.payload.signature\""
                + "},"
                + " \"keyFacts\": [\"Fato\"],"
                + " \"fields\": {},"
                + " \"completeness\": \"COMPLETE\","
                + " \"missingAspects\": null}";

        final CrawlResult result = extractor.extract(
                UUID.randomUUID(), "t1",
                buildContext(),
                List.of("chunk"),
                List.of(),
                0.5
        );

        assertNotNull(result.extractedContent);
        assertFalse(result.extractedContent.isBlank());
        assertTrue(result.extractedContent.contains("definicao"));
    }

    @Test
    void markdownFenceWrappedJsonParses() {
        llmProvider.rawContent =
                "```json\n"
                + "{\"extractedContent\": \"Dentro do fence\","
                + " \"keyFacts\": [],"
                + " \"fields\": {},"
                + " \"completeness\": \"COMPLETE\","
                + " \"missingAspects\": null}\n"
                + "```";

        final CrawlResult result = extractor.extract(
                UUID.randomUUID(), "t1",
                buildContext(),
                List.of("chunk"),
                List.of(),
                0.5
        );

        assertEquals("Dentro do fence", result.extractedContent);
    }

    @Test
    void agentCallLogPersistedAfterCall() {
        llmProvider.rawContent =
                "{\"extractedContent\": \"ok\","
                + " \"keyFacts\": [],"
                + " \"fields\": {},"
                + " \"completeness\": \"COMPLETE\","
                + " \"missingAspects\": null}";

        extractor.extract(
                UUID.randomUUID(), "t1",
                buildContext(),
                List.of("chunk"),
                List.of(),
                0.8
        );

        assertFalse(persistence.savedLogs.isEmpty());
    }

    private static NavigationContext buildContext() {
        return new NavigationContext(
                "test query",
                "https://example.com",
                null,
                1,
                5,
                0.8,
                "relevant link",
                ExtractionMethod.JSOUP,
                "Test Page"
        );
    }

    /**
     * Stub do LLMProviderPort para testes.
     */
    static final class StubLLMProvider implements LLMProviderPort {
        private String rawContent = "{}";

        @Override
        public AgentResponse call(final LLMRequest request) {
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
     * Stub do PersistencePort para testes.
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
