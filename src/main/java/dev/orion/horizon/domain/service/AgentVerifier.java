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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.orion.horizon.domain.model.AgentCallLog;
import dev.orion.horizon.domain.model.AgentResponse;
import dev.orion.horizon.domain.model.LLMRequest;
import dev.orion.horizon.domain.model.NavigationContext;
import dev.orion.horizon.domain.port.out.LLMProviderPort;
import dev.orion.horizon.domain.port.out.PersistencePort;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Agente 1 — verifica se um chunk contém informação relevante para a query.
 *
 * <p>Para cada chunk, chama o LLM e interpreta a resposta JSON:
 * {@code {"relevant": true|false, "confidence": 0.0-1.0,
 * "justification": "..."}}.
 * JSON inválido resulta em {@code relevant=false} sem lançar exceção.
 */
public final class AgentVerifier {

    /** Role do agente para logs. */
    public static final String AGENT_ROLE = "verifier";

    private static final Logger LOG =
            Logger.getLogger(AgentVerifier.class.getName());

    private static final String SYSTEM_PROMPT =
            "Você é um verificador de relevância de conteúdo web.\n"
            + "Determine se um trecho de texto contém informações relevantes\n"
            + "para responder à consulta do usuário.\n\n"
            + "Responda APENAS com JSON válido:\n"
            + "{\n"
            + "  \"relevant\":      true | false,\n"
            + "  \"confidence\":    0.0 a 1.0,\n"
            + "  \"justification\": \"Explicação em 1-2 frases\"\n"
            + "}";

    private final LLMProviderPort llmProvider;
    private final PersistencePort persistence;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;
    private final int maxTokens;

    /**
     * Cria um AgentVerifier.
     *
     * @param llmProvider provedor LLM para chamadas
     * @param persistence porta de persistência para AgentCallLog
     * @param objectMapper mapper JSON para parse da resposta
     * @param rateLimiter limitador de taxa compartilhado
     * @param maxTokens teto de tokens de saída
     */
    public AgentVerifier(
            final LLMProviderPort llmProvider,
            final PersistencePort persistence,
            final ObjectMapper objectMapper,
            final RateLimiter rateLimiter,
            final int maxTokens) {
        this.llmProvider =
                Objects.requireNonNull(llmProvider, "llmProvider");
        this.persistence =
                Objects.requireNonNull(persistence, "persistence");
        this.objectMapper =
                Objects.requireNonNull(objectMapper, "objectMapper");
        this.rateLimiter =
                Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.maxTokens = maxTokens;
    }

    /**
     * Verifica se um chunk é relevante para a query do contexto.
     *
     * @param jobId identificador do job de crawl
     * @param threadId identificador da thread
     * @param context contexto de navegação com query e URL
     * @param chunk trecho de texto a avaliar
     * @param chunkIndex índice do chunk (base zero)
     * @param chunkTotal total de chunks da página
     * @return {@code true} se o chunk for relevante; {@code false} em caso
     *         de irrelevância ou parse inválido
     */
    public boolean verify(
            final UUID jobId,
            final String threadId,
            final NavigationContext context,
            final String chunk,
            final int chunkIndex,
            final int chunkTotal) {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(chunk, "chunk");

        final String userPrompt = buildUserPrompt(
                context, chunk, chunkIndex, chunkTotal
        );
        final LLMRequest request = new LLMRequest(
                SYSTEM_PROMPT, userPrompt, AGENT_ROLE, maxTokens
        );

        AgentResponse response = null;
        String errorMsg = null;
        final Instant calledAt = Instant.now();

        try {
            rateLimiter.acquire();
            response = llmProvider.call(request);
            rateLimiter.onSuccess();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "AgentVerifier interrupted: {0}",
                    e.getMessage());
            persistLog(jobId, threadId, context.currentUrl(),
                    chunkIndex, chunkTotal, userPrompt,
                    null, false, null, calledAt, e.getMessage());
            return false;
        } catch (final Exception e) {
            errorMsg = e.getMessage();
            LOG.log(Level.WARNING,
                    "AgentVerifier LLM call failed for chunk {0}: {1}",
                    new Object[]{chunkIndex, errorMsg});
            persistLog(jobId, threadId, context.currentUrl(),
                    chunkIndex, chunkTotal, userPrompt,
                    null, false, null, calledAt, errorMsg);
            return false;
        }

        final VerifyResult result = parseResponse(response.rawContent);
        persistLog(jobId, threadId, context.currentUrl(),
                chunkIndex, chunkTotal, userPrompt,
                response, result.relevant, result.confidence,
                calledAt, result.parseError);
        return result.relevant;
    }

    private String buildUserPrompt(
            final NavigationContext context,
            final String chunk,
            final int chunkIndex,
            final int chunkTotal) {
        final StringBuilder sb = new StringBuilder();
        sb.append("### Contexto de Navegação\n");
        sb.append("Query: ").append(context.userQuery()).append("\n");
        sb.append("URL atual: ").append(context.currentUrl()).append("\n");
        sb.append("Profundidade: ").append(context.currentDepth())
                .append("/").append(context.maxDepth()).append("\n");
        if (context.pageTitle() != null) {
            sb.append("Título: ").append(context.pageTitle()).append("\n");
        }
        sb.append("\n### Chunk ").append(chunkIndex + 1)
                .append(" de ").append(chunkTotal).append("\n");
        sb.append("Método de extração: ")
                .append(context.extractionMethod()).append("\n\n");
        sb.append(chunk);
        return sb.toString();
    }

    private VerifyResult parseResponse(final String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return new VerifyResult(false, null, "empty response");
        }
        try {
            final String json = extractJson(rawContent);
            final JsonNode node = objectMapper.readTree(json);
            final boolean relevant =
                    node.path("relevant").asBoolean(false);
            final Double confidence =
                    node.has("confidence")
                            ? node.path("confidence").asDouble()
                            : null;
            return new VerifyResult(relevant, confidence, null);
        } catch (final Exception e) {
            LOG.log(Level.FINE,
                    "AgentVerifier JSON parse error: {0}", e.getMessage());
            return new VerifyResult(false, null, e.getMessage());
        }
    }

    private static String extractJson(final String text) {
        final int start = text.indexOf('{');
        final int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private void persistLog(
            final UUID jobId,
            final String threadId,
            final String pageUrl,
            final int chunkIndex,
            final int chunkTotal,
            final String userPrompt,
            final AgentResponse response,
            final boolean relevant,
            final Double confidence,
            final Instant calledAt,
            final String errorMsg) {
        try {
            final AgentCallLog log = new AgentCallLog(
                    jobId,
                    threadId,
                    AGENT_ROLE,
                    response != null
                            ? response.providerUsed
                            : llmProvider.getProviderName(),
                    response != null
                            ? response.modelUsed
                            : llmProvider.getModelName(),
                    pageUrl,
                    chunkIndex,
                    chunkTotal,
                    SYSTEM_PROMPT,
                    userPrompt,
                    response != null ? response.rawContent : null,
                    relevant,
                    confidence,
                    response != null ? response.inputTokens : null,
                    response != null ? response.outputTokens : null,
                    response != null ? response.latencyMs : null,
                    response != null ? response.httpStatus : null,
                    errorMsg,
                    calledAt
            );
            persistence.saveAgentCall(jobId, log);
        } catch (final Exception e) {
            LOG.log(Level.WARNING,
                    "Failed to persist AgentCallLog: {0}",
                    e.getMessage());
        }
    }

    /**
     * Resultado interno do parse da resposta do verificador.
     */
    private static final class VerifyResult {
        private final boolean relevant;
        private final Double confidence;
        private final String parseError;

        private VerifyResult(
                final boolean relevant,
                final Double confidence,
                final String parseError) {
            this.relevant = relevant;
            this.confidence = confidence;
            this.parseError = parseError;
        }
    }
}
