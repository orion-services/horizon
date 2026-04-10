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

package dev.orion.horizon.adapter.out.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.orion.horizon.config.AgentExtractorConfig;
import dev.orion.horizon.domain.model.AgentResponse;
import dev.orion.horizon.domain.model.LLMRequest;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Testes do {@link AnthropicAdapter} com {@link MockWebServer}.
 */
final class AnthropicAdapterTest {

    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void shutdown() throws IOException {
        server.shutdown();
    }

    @Test
    void successParsesContentTokensAndLatency() throws Exception {
        final String body =
                "{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}],"
                        + "\"usage\":{\"input_tokens\":10,"
                        + "\"output_tokens\":5}}";
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(body)
                        .addHeader("content-type", "application/json")
        );

        final AnthropicAdapter adapter =
                adapterForBaseUrl(server.url("/v1/messages").toString());
        final LLMRequest request =
                new LLMRequest("sys", "user", "extractor", 100);

        final AgentResponse response = adapter.call(request);

        assertEquals("hello", response.rawContent);
        assertEquals(Integer.valueOf(10), response.inputTokens);
        assertEquals(Integer.valueOf(5), response.outputTokens);
        assertEquals(Integer.valueOf(200), response.httpStatus);
        assertNotNull(response.latencyMs);
        assertTrue(response.latencyMs >= 0L);
    }

    @Test
    void http429ThrowsLLMRateLimitException() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("{}"));

        final AnthropicAdapter adapter =
                adapterForBaseUrl(server.url("/v1/messages").toString());
        final LLMRequest request =
                new LLMRequest("s", "u", "r", 50);

        final LLMRateLimitException ex =
                assertThrows(
                        LLMRateLimitException.class,
                        () -> adapter.call(request)
                );
        assertEquals(Integer.valueOf(429), ex.getHttpStatus());
    }

    @Test
    void readTimeoutThrowsLLMException() throws Exception {
        server.enqueue(
                new MockResponse()
                        .setHeadersDelay(5L, TimeUnit.SECONDS)
                        .setBody("{}")
        );

        final OkHttpClient fastTimeout =
                new OkHttpClient.Builder()
                        .readTimeout(50L, TimeUnit.MILLISECONDS)
                        .connectTimeout(1L, TimeUnit.SECONDS)
                        .build();
        final AnthropicAdapter adapter =
                new AnthropicAdapter(
                        fastTimeout,
                        new ObjectMapper(),
                        stubConfig(server.url("/v1/messages").toString())
                );
        final LLMRequest request =
                new LLMRequest("s", "u", "r", 50);

        assertThrows(LLMException.class, () -> adapter.call(request));
    }

    private AnthropicAdapter adapterForBaseUrl(final String baseUrl) {
        return new AnthropicAdapter(
                new OkHttpClient(),
                new ObjectMapper(),
                stubConfig(baseUrl)
        );
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
    void realCallReturnsAgentResponseWithContent() {
        final AnthropicAdapter adapter =
                new AnthropicAdapter(
                        new OkHttpClient(),
                        new ObjectMapper(),
                        realAnthropicConfig()
                );
        final LLMRequest request =
                new LLMRequest(
                        "You reply with exactly: OK",
                        "Say OK.",
                        "test",
                        32
                );
        final AgentResponse response = adapter.call(request);
        assertNotNull(response.rawContent);
        assertTrue(response.rawContent.length() > 0);
        assertNotNull(response.latencyMs);
    }

    private static AgentExtractorConfig realAnthropicConfig() {
        final String key = System.getenv("ANTHROPIC_API_KEY");
        final String model =
                System.getenv().getOrDefault(
                        "ANTHROPIC_MODEL",
                        "claude-3-5-haiku-20241022"
                );
        return new AgentExtractorConfig() {
            @Override
            public String baseUrl() {
                return "https://api.anthropic.com/v1/messages";
            }

            @Override
            public String provider() {
                return "ANTHROPIC";
            }

            @Override
            public String model() {
                return model;
            }

            @Override
            public String apiKey() {
                return key;
            }

            @Override
            public int maxTokens() {
                return 256;
            }
        };
    }

    private static AgentExtractorConfig stubConfig(final String baseUrl) {
        return new StubAgentExtractorConfig(baseUrl);
    }

    /**
     * Configuração fixa para testes unitários com {@link MockWebServer}.
     */
    private static final class StubAgentExtractorConfig
            implements AgentExtractorConfig {

        private final String baseUrl;

        private StubAgentExtractorConfig(final String baseUrl) {
            this.baseUrl = baseUrl;
        }

        @Override
        public String baseUrl() {
            return baseUrl;
        }

        @Override
        public String provider() {
            return "ANTHROPIC";
        }

        @Override
        public String model() {
            return "claude-test";
        }

        @Override
        public String apiKey() {
            return "test-key";
        }

        @Override
        public int maxTokens() {
            return 1000;
        }
    }
}
