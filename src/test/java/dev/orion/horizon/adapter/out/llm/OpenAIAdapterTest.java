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
import dev.orion.horizon.domain.model.AgentResponse;
import dev.orion.horizon.domain.model.LLMRequest;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários do {@link OpenAIAdapter} com {@link MockWebServer}.
 */
final class OpenAIAdapterTest {

    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    void successExtractsChoicesContent() throws Exception {
        final String body =
                "{\"choices\":[{\"message\":"
                + "{\"role\":\"assistant\",\"content\":\"Resposta\"}}],"
                + "\"usage\":{\"prompt_tokens\":10,"
                + "\"completion_tokens\":5}}";
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(body)
                        .addHeader("content-type", "application/json")
        );

        final AgentResponse resp =
                adapterFor(baseUrl()).call(
                        new LLMRequest("sys", "user", "extractor", 256)
                );

        assertEquals("Resposta", resp.rawContent);
        assertEquals("OPENAI", resp.providerUsed);
        assertEquals(Integer.valueOf(10), resp.inputTokens);
        assertEquals(Integer.valueOf(5), resp.outputTokens);
        assertEquals(Integer.valueOf(200), resp.httpStatus);
        assertNotNull(resp.latencyMs);
        assertTrue(resp.latencyMs >= 0L);
    }

    @Test
    void requestIncludesBearerToken() throws Exception {
        final String body =
                "{\"choices\":[{\"message\":"
                + "{\"content\":\"ok\"}}],\"usage\":{}}";
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(body)
        );

        adapterFor(baseUrl()).call(
                new LLMRequest("s", "u", "r", 64)
        );

        final RecordedRequest recorded = server.takeRequest();
        final String auth = recorded.getHeader("Authorization");
        assertNotNull(auth);
        assertTrue(auth.startsWith("Bearer "));
    }

    @Test
    void http429ThrowsLLMRateLimitException() {
        server.enqueue(
                new MockResponse().setResponseCode(429).setBody("{}")
        );

        assertThrows(
                LLMRateLimitException.class,
                () -> adapterFor(baseUrl()).call(
                        new LLMRequest("s", "u", "r", 64)
                )
        );
    }

    @Test
    void httpErrorThrowsLLMException() {
        server.enqueue(
                new MockResponse().setResponseCode(503).setBody("{}")
        );

        assertThrows(
                LLMException.class,
                () -> adapterFor(baseUrl()).call(
                        new LLMRequest("s", "u", "r", 64)
                )
        );
    }

    @Test
    void timeoutThrowsLLMException() {
        server.enqueue(
                new MockResponse()
                        .setHeadersDelay(5L, TimeUnit.SECONDS)
                        .setBody("{}")
        );

        final OkHttpClient fastClient =
                new OkHttpClient.Builder()
                        .readTimeout(50L, TimeUnit.MILLISECONDS)
                        .connectTimeout(1L, TimeUnit.SECONDS)
                        .build();
        final OpenAIAdapter adapter =
                new OpenAIAdapter(
                        fastClient,
                        new ObjectMapper(),
                        "gpt-4o",
                        "test-key",
                        256,
                        baseUrl()
                );

        assertThrows(
                LLMException.class,
                () -> adapter.call(new LLMRequest("s", "u", "r", 64))
        );
    }

    @Test
    void getProviderNameReturnsOpenai() {
        assertEquals("OPENAI", adapterFor(baseUrl()).getProviderName());
    }

    @Test
    void getModelNameReturnsConfiguredModel() {
        final OpenAIAdapter adapter =
                new OpenAIAdapter(
                        new OkHttpClient(),
                        new ObjectMapper(),
                        "gpt-4-turbo",
                        "key",
                        256
                );
        assertEquals("gpt-4-turbo", adapter.getModelName());
    }

    private String baseUrl() {
        return server.url("/v1/chat/completions").toString();
    }

    private OpenAIAdapter adapterFor(final String url) {
        return new OpenAIAdapter(
                new OkHttpClient(),
                new ObjectMapper(),
                "gpt-4o",
                "test-key",
                256,
                url
        );
    }
}
