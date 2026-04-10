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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.orion.horizon.config.AgentExtractorConfig;
import dev.orion.horizon.domain.model.AgentResponse;
import dev.orion.horizon.domain.model.LLMRequest;
import dev.orion.horizon.domain.port.out.LLMProviderPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Cliente Anthropic Messages API ({@code LLMProviderPort}).
 */
@ApplicationScoped
public class AnthropicAdapter implements LLMProviderPort {

    /** Versão da API exigida pelo header {@code anthropic-version}. */
    public static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AgentExtractorConfig config;

    /**
     * @param httpClient cliente HTTP (timeout configurável)
     * @param objectMapper serialização JSON
     * @param config credenciais e modelo
     */
    @Inject
    public AnthropicAdapter(
            final OkHttpClient httpClient,
            final ObjectMapper objectMapper,
            final AgentExtractorConfig config) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper =
                Objects.requireNonNull(objectMapper, "objectMapper");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public AgentResponse call(final LLMRequest request) {
        Objects.requireNonNull(request, "request");
        final long startNanos = System.nanoTime();
        final ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.model());
        final int maxTokens =
                request.maxTokens() > 0
                        ? request.maxTokens()
                        : config.maxTokens();
        body.put("max_tokens", maxTokens);
        final String systemPrompt =
                request.systemPrompt() == null ? "" : request.systemPrompt();
        body.put("system", systemPrompt);
        final ArrayNode messages = body.putArray("messages");
        final ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put(
                "content",
                request.userPrompt() == null ? "" : request.userPrompt());

        final Request httpRequest =
                new Request.Builder()
                        .url(config.baseUrl())
                        .header("x-api-key", config.apiKey())
                        .header("anthropic-version", ANTHROPIC_VERSION)
                        .header("content-type", "application/json")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            final long elapsedNanos = System.nanoTime() - startNanos;
            final long latencyMs =
                    TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
            final int code = response.code();
            final ResponseBody responseBody = response.body();
            final String raw =
                    responseBody != null ? responseBody.string() : "";

            if (code == 429) {
                throw new LLMRateLimitException(
                        "Anthropic API rate limit (HTTP 429)"
                );
            }

            if (!response.isSuccessful()) {
                throw new LLMException(
                        "Anthropic API error HTTP " + code + ": " + raw,
                        Integer.valueOf(code)
                );
            }

            final JsonNode root = objectMapper.readTree(raw);
            final String text = extractText(root);
            final JsonNode usage = root.path("usage");
            final Integer inputTokens =
                    usage.path("input_tokens").isNumber()
                            ? usage.path("input_tokens").intValue()
                            : null;
            final Integer outputTokens =
                    usage.path("output_tokens").isNumber()
                            ? usage.path("output_tokens").intValue()
                            : null;

            return AgentResponse.builder()
                    .rawContent(text)
                    .parsedJson(raw)
                    .providerUsed(getProviderName())
                    .modelUsed(config.model())
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .latencyMs(Long.valueOf(latencyMs))
                    .httpStatus(Integer.valueOf(code))
                    .build();
        } catch (final IOException e) {
            throw new LLMException(
                    "Anthropic request failed: " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public String getProviderName() {
        return config.provider();
    }

    @Override
    public String getModelName() {
        return config.model();
    }

    private static String extractText(final JsonNode root) {
        final JsonNode content = root.path("content");
        if (content.isArray() && content.size() > 0) {
            final JsonNode first = content.get(0);
            final JsonNode textNode = first.path("text");
            if (textNode.isTextual()) {
                return textNode.asText();
            }
        }
        return "";
    }
}
