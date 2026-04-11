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
import dev.orion.horizon.config.OllamaChatConfig;
import dev.orion.horizon.config.OllamaEndpoint;
import dev.orion.horizon.domain.model.AgentResponse;
import dev.orion.horizon.domain.model.LLMRequest;
import dev.orion.horizon.domain.port.out.LLMProviderPort;
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
 * Adapter para o Ollama local ({@link LLMProviderPort}).
 *
 * <p>Chama {@code POST {baseUrl}/api/chat} com {@code stream: false}.
 * Sem rate limiting externo — Ollama corre localmente.
 */
public class OllamaAdapter implements LLMProviderPort {

    /** Caminho do endpoint de chat do Ollama. */
    public static final String CHAT_PATH = "/api/chat";

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OllamaChatConfig config;

    /**
     * Cria o adapter com endpoint Ollama explícito (testes ou fábrica).
     *
     * @param httpClient cliente HTTP
     * @param objectMapper serializador JSON
     * @param config URL base e modelo
     */
    public OllamaAdapter(
            final OkHttpClient httpClient,
            final ObjectMapper objectMapper,
            final OllamaChatConfig config) {
        this.httpClient =
                Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper =
                Objects.requireNonNull(objectMapper, "objectMapper");
        this.config =
                Objects.requireNonNull(config, "config");
    }

    /**
     * Cria o adapter com parâmetros diretos (atalho para testes).
     *
     * @param httpClient cliente HTTP
     * @param objectMapper serializador JSON
     * @param baseUrl URL base do Ollama (ex.: http://localhost:11434)
     * @param model identificador do modelo
     */
    public OllamaAdapter(
            final OkHttpClient httpClient,
            final ObjectMapper objectMapper,
            final String baseUrl,
            final String model) {
        this(
                httpClient,
                objectMapper,
                new OllamaEndpoint(baseUrl, model)
        );
    }

    @Override
    public AgentResponse call(final LLMRequest request) {
        Objects.requireNonNull(request, "request");
        final long startNanos = System.nanoTime();

        final ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.model());
        body.put("stream", false);

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

        final String rawBase = config.baseUrl();
        final String normalizedBase =
                rawBase.endsWith("/")
                        ? rawBase.substring(0, rawBase.length() - 1)
                        : rawBase;
        final String url = normalizedBase + CHAT_PATH;

        final Request httpRequest =
                new Request.Builder()
                        .url(url)
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
                        "Ollama rate limit (HTTP 429)"
                );
            }

            if (!response.isSuccessful()) {
                throw new LLMException(
                        "Ollama error HTTP " + code + ": " + raw,
                        Integer.valueOf(code)
                );
            }

            final JsonNode root = objectMapper.readTree(raw);
            final String text = extractText(root);
            final int inputTok =
                    root.path("prompt_eval_count").isNumber()
                            ? root.path("prompt_eval_count").intValue()
                            : 0;
            final int outputTok =
                    root.path("eval_count").isNumber()
                            ? root.path("eval_count").intValue()
                            : 0;

            return AgentResponse.builder()
                    .rawContent(text)
                    .parsedJson(raw)
                    .providerUsed(getProviderName())
                    .modelUsed(config.model())
                    .inputTokens(Integer.valueOf(inputTok))
                    .outputTokens(Integer.valueOf(outputTok))
                    .latencyMs(Long.valueOf(latencyMs))
                    .httpStatus(Integer.valueOf(code))
                    .build();
        } catch (final IOException e) {
            throw new LLMException(
                    "Ollama request failed: " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public String getProviderName() {
        return "OLLAMA";
    }

    @Override
    public String getModelName() {
        return config.model();
    }

    private static String extractText(final JsonNode root) {
        final JsonNode message = root.path("message");
        if (!message.isMissingNode()) {
            final JsonNode content = message.path("content");
            if (content.isTextual()) {
                return content.asText();
            }
        }
        return "";
    }
}
