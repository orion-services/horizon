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
import dev.orion.horizon.domain.model.AgentResponse;
import dev.orion.horizon.domain.model.LLMRequest;
import dev.orion.horizon.domain.port.out.LLMProviderPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
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
 * Adapter para a API de chat da OpenAI ({@link LLMProviderPort}).
 *
 * <p>Chama {@code POST https://api.openai.com/v1/chat/completions} com
 * cabeçalho {@code Authorization: Bearer {apiKey}}.
 */
@ApplicationScoped
@Named("openai")
public class OpenAIAdapter implements LLMProviderPort {

    /** Endpoint padrão da API de completions da OpenAI. */
    public static final String DEFAULT_OPENAI_URL =
            "https://api.openai.com/v1/chat/completions";

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String apiKey;
    private final int defaultMaxTokens;
    private String openAiUrl;

    /**
     * Cria o adapter com parâmetros diretos (para uso e testes).
     *
     * @param httpClient cliente HTTP compartilhado
     * @param objectMapper serializador JSON
     * @param model identificador do modelo (ex.: gpt-4o)
     * @param apiKey chave de API da OpenAI
     * @param defaultMaxTokens teto padrão de tokens de saída
     */
    public OpenAIAdapter(
            final OkHttpClient httpClient,
            final ObjectMapper objectMapper,
            final String model,
            final String apiKey,
            final int defaultMaxTokens) {
        this(
                httpClient,
                objectMapper,
                model,
                apiKey,
                defaultMaxTokens,
                DEFAULT_OPENAI_URL
        );
    }

    /**
     * Cria o adapter com URL customizada (para testes unitários).
     *
     * @param httpClient cliente HTTP
     * @param objectMapper serializador JSON
     * @param model identificador do modelo
     * @param apiKey chave de API
     * @param defaultMaxTokens teto de tokens
     * @param apiUrl URL do endpoint (sobrescreve o padrão)
     */
    public OpenAIAdapter(
            final OkHttpClient httpClient,
            final ObjectMapper objectMapper,
            final String model,
            final String apiKey,
            final int defaultMaxTokens,
            final String apiUrl) {
        this.httpClient =
                Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper =
                Objects.requireNonNull(objectMapper, "objectMapper");
        this.model = Objects.requireNonNull(model, "model");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.defaultMaxTokens = defaultMaxTokens;
        this.openAiUrl =
                apiUrl != null ? apiUrl : DEFAULT_OPENAI_URL;
    }

    @Override
    public AgentResponse call(final LLMRequest request) {
        Objects.requireNonNull(request, "request");
        final long startNanos = System.nanoTime();

        final int maxTokens =
                request.maxTokens() > 0
                        ? request.maxTokens()
                        : defaultMaxTokens;

        final ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);

        final ArrayNode messages = body.putArray("messages");
        if (request.systemPrompt() != null
                && !request.systemPrompt().isEmpty()) {
            final ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", request.systemPrompt());
        }
        final ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put(
                "content",
                request.userPrompt() == null ? "" : request.userPrompt()
        );

        final Request httpRequest =
                new Request.Builder()
                        .url(openAiUrl)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();

        try (Response response =
                httpClient.newCall(httpRequest).execute()) {
            final long elapsedNanos = System.nanoTime() - startNanos;
            final long latencyMs =
                    TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
            final int code = response.code();
            final ResponseBody responseBody = response.body();
            final String raw =
                    responseBody != null ? responseBody.string() : "";

            if (code == 429) {
                throw new LLMRateLimitException(
                        "OpenAI rate limit (HTTP 429)"
                );
            }

            if (!response.isSuccessful()) {
                throw new LLMException(
                        "OpenAI error HTTP " + code + ": " + raw,
                        Integer.valueOf(code)
                );
            }

            final JsonNode root = objectMapper.readTree(raw);
            final String text = extractText(root);
            final JsonNode usage = root.path("usage");
            final int inputTok =
                    usage.path("prompt_tokens").isNumber()
                            ? usage.path("prompt_tokens").intValue()
                            : 0;
            final int outputTok =
                    usage.path("completion_tokens").isNumber()
                            ? usage.path("completion_tokens").intValue()
                            : 0;

            return AgentResponse.builder()
                    .rawContent(text)
                    .parsedJson(raw)
                    .providerUsed(getProviderName())
                    .modelUsed(model)
                    .inputTokens(Integer.valueOf(inputTok))
                    .outputTokens(Integer.valueOf(outputTok))
                    .latencyMs(Long.valueOf(latencyMs))
                    .httpStatus(Integer.valueOf(code))
                    .build();
        } catch (final IOException e) {
            throw new LLMException(
                    "OpenAI request failed: " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public String getProviderName() {
        return "OPENAI";
    }

    @Override
    public String getModelName() {
        return model;
    }

    private static String extractText(final JsonNode root) {
        final JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            final JsonNode first = choices.get(0);
            final JsonNode message = first.path("message");
            final JsonNode content = message.path("content");
            if (content.isTextual()) {
                return content.asText();
            }
        }
        return "";
    }
}
