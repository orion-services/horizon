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
 * Testes unitários do {@link AgentVerifier}.
 */
final class AgentVerifierTest {

    private StubLLMProvider llmProvider;
    private StubPersistence persistence;
    private AgentVerifier verifier;

    @BeforeEach
    void setUp() {
        llmProvider = new StubLLMProvider();
        persistence = new StubPersistence();
        verifier = new AgentVerifier(
                llmProvider,
                persistence,
                new ObjectMapper(),
                new RateLimiter(0, List.of(), 3, ms -> { }),
                512
        );
    }

    @Test
    void validJsonRelevantTrueReturnsTrue() {
        llmProvider.rawContent =
                "{\"relevant\": true, \"confidence\": 0.9,"
                + " \"justification\": \"Muito relevante\"}";

        final boolean result = verifier.verify(
                UUID.randomUUID(), "t1",
                buildContext(), "chunk text", 0, 1
        );

        assertTrue(result);
    }

    @Test
    void validJsonRelevantFalseReturnsFalse() {
        llmProvider.rawContent =
                "{\"relevant\": false, \"confidence\": 0.1,"
                + " \"justification\": \"Irrelevante\"}";

        final boolean result = verifier.verify(
                UUID.randomUUID(), "t1",
                buildContext(), "chunk text", 0, 1
        );

        assertFalse(result);
    }

    @Test
    void invalidJsonDoesNotThrowAndReturnsFalse() {
        llmProvider.rawContent = "INVALID JSON !!!";

        final boolean result = verifier.verify(
                UUID.randomUUID(), "t1",
                buildContext(), "chunk text", 0, 1
        );

        assertFalse(result);
    }

    @Test
    void agentCallLogPersistedAfterCall() {
        llmProvider.rawContent =
                "{\"relevant\": true, \"confidence\": 0.8,"
                + " \"justification\": \"ok\"}";

        verifier.verify(
                UUID.randomUUID(), "t1",
                buildContext(), "chunk text", 0, 3
        );

        assertFalse(persistence.savedLogs.isEmpty());
        assertNotNull(persistence.savedLogs.get(0));
    }

    @Test
    void llmExceptionDoesNotThrowAndReturnsFalse() {
        llmProvider.throwException = true;

        final boolean result = verifier.verify(
                UUID.randomUUID(), "t1",
                buildContext(), "chunk text", 0, 1
        );

        assertFalse(result);
    }

    private static NavigationContext buildContext() {
        return new NavigationContext(
                "test query",
                "https://example.com",
                null,
                0,
                5,
                null,
                null,
                ExtractionMethod.JSOUP,
                "Test Page"
        );
    }

    /**
     * Stub do LLMProviderPort para testes.
     */
    static final class StubLLMProvider implements LLMProviderPort {
        private String rawContent = "{}";
        private boolean throwException = false;

        @Override
        public AgentResponse call(final LLMRequest request) {
            if (throwException) {
                throw new RuntimeException("simulated LLM error");
            }
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
                final UUID jobId,
                final String sourceUrl,
                final LinkCandidate link) { }

        @Override
        public void saveCrawlResult(
                final UUID jobId,
                final CrawlResult result) { }

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
