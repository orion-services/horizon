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
 * Testes unitários do {@link OllamaAdapter} com {@link MockWebServer}.
 */
final class OllamaAdapterTest {

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
    void successExtractsMessageContent() throws Exception {
        final String body =
                "{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"Olá!\"},"
                + "\"prompt_eval_count\":8,"
                + "\"eval_count\":3}";
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(body)
                        .addHeader("content-type", "application/json")
        );

        final OllamaAdapter adapter = adapterFor(baseUrl());
        final LLMRequest req =
                new LLMRequest("sys", "user", "verifier", 256);

        final AgentResponse resp = adapter.call(req);

        assertEquals("Olá!", resp.rawContent);
        assertEquals("OLLAMA", resp.providerUsed);
        assertEquals(Integer.valueOf(8), resp.inputTokens);
        assertEquals(Integer.valueOf(3), resp.outputTokens);
        assertEquals(Integer.valueOf(200), resp.httpStatus);
        assertNotNull(resp.latencyMs);
    }

    @Test
    void requestBodyContainsStreamFalse() throws Exception {
        final String body =
                "{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"ok\"}}";
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(body)
        );

        final OllamaAdapter adapter = adapterFor(baseUrl());
        adapter.call(new LLMRequest("s", "u", "r", 64));

        final RecordedRequest recorded = server.takeRequest();
        final String requestBody = recorded.getBody().readUtf8();
        assertEquals(true, requestBody.contains("\"stream\":false"));
    }

    @Test
    void http429ThrowsLLMRateLimitException() {
        server.enqueue(
                new MockResponse().setResponseCode(429).setBody("{}")
        );

        final OllamaAdapter adapter = adapterFor(baseUrl());
        final LLMRequest req =
                new LLMRequest("s", "u", "r", 64);

        assertThrows(
                LLMRateLimitException.class,
                () -> adapter.call(req)
        );
    }

    @Test
    void httpErrorThrowsLLMException() {
        server.enqueue(
                new MockResponse().setResponseCode(500).setBody("{}")
        );

        final OllamaAdapter adapter = adapterFor(baseUrl());
        assertThrows(
                LLMException.class,
                () -> adapter.call(new LLMRequest("s", "u", "r", 64))
        );
    }

    @Test
    void timeoutThrowsLLMException() throws Exception {
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
        final OllamaAdapter adapter =
                new OllamaAdapter(
                        fastClient,
                        new ObjectMapper(),
                        baseUrl(),
                        "llama3.1"
                );

        assertThrows(
                LLMException.class,
                () -> adapter.call(new LLMRequest("s", "u", "r", 64))
        );
    }

    @Test
    void getProviderNameReturnsOllama() {
        final OllamaAdapter adapter = adapterFor(baseUrl());
        assertEquals("OLLAMA", adapter.getProviderName());
    }

    @Test
    void getModelNameReturnsConfiguredModel() {
        final OllamaAdapter adapter =
                new OllamaAdapter(
                        new OkHttpClient(),
                        new ObjectMapper(),
                        baseUrl(),
                        "mistral"
                );
        assertEquals("mistral", adapter.getModelName());
    }

    private String baseUrl() {
        return server.url("/").toString();
    }

    private OllamaAdapter adapterFor(final String url) {
        return new OllamaAdapter(
                new OkHttpClient(),
                new ObjectMapper(),
                url,
                "llama3.1"
        );
    }
}
