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
import dev.orion.horizon.domain.model.CrawlResult;
import dev.orion.horizon.domain.model.ExtractionMethod;
import dev.orion.horizon.domain.model.LLMRequest;
import dev.orion.horizon.domain.model.NavigationContext;
import dev.orion.horizon.domain.port.out.LLMProviderPort;
import dev.orion.horizon.domain.port.out.PersistencePort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Agente 3 — extrai conteúdo relevante dos chunks positivos identificados pelo
 * AgentVerifier.
 *
 * <p>Recebe todos os chunks positivos e produz um {@link CrawlResult} com
 * conteúdo extraído, fatos-chave e campos estruturados.
 * JSON inválido resulta em CrawlResult com conteúdo bruto da resposta.
 */
public final class AgentExtractor {

    /** Role do agente para logs. */
    public static final String AGENT_ROLE = "extractor";

    private static final Logger LOG =
            Logger.getLogger(AgentExtractor.class.getName());

    private static final String SYSTEM_PROMPT =
            "Você é um extrator de conteúdo especializado.\n"
            + "Extraia informações que respondem à consulta do usuário.\n"
            + "Preserve dados precisos: números, preços, datas, nomes.\n"
            + "Não invente informações.\n\n"
            + "Responda APENAS com JSON válido:\n"
            + "{\n"
            + "  \"extractedContent\": \"...\",\n"
            + "  \"keyFacts\":         [\"Fato 1\", \"Fato 2\"],\n"
            + "  \"fields\":           {},\n"
            + "  \"completeness\":     \"COMPLETE | PARTIAL\",\n"
            + "  \"missingAspects\":   \"...ou null\"\n"
            + "}";

    private final LLMProviderPort llmProvider;
    private final PersistencePort persistence;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;
    private final int maxTokens;

    /**
     * Cria um AgentExtractor.
     *
     * @param llmProvider provedor LLM
     * @param persistence porta de persistência
     * @param objectMapper mapper JSON
     * @param rateLimiter limitador de taxa compartilhado
     * @param maxTokens teto de tokens de saída
     */
    public AgentExtractor(
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
     * Extrai conteúdo relevante a partir dos chunks positivos.
     *
     * @param jobId identificador do job de crawl
     * @param threadId identificador da thread
     * @param context contexto de navegação
     * @param positiveChunks chunks considerados relevantes pelo AgentVerifier
     * @param originChain cadeia de URLs que levou a esta página
     * @param pageLinkScore score do link que levou à página
     * @return resultado da extração; nunca {@code null}
     */
    public CrawlResult extract(
            final UUID jobId,
            final String threadId,
            final NavigationContext context,
            final List<String> positiveChunks,
            final List<String> originChain,
            final double pageLinkScore) {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(positiveChunks, "positiveChunks");

        final String userPrompt =
                buildUserPrompt(context, positiveChunks);
        final LLMRequest request = new LLMRequest(
                SYSTEM_PROMPT, userPrompt, AGENT_ROLE, maxTokens
        );

        final Instant calledAt = Instant.now();
        AgentResponse response = null;
        String errorMsg = null;

        try {
            rateLimiter.acquire();
            response = llmProvider.call(request);
            rateLimiter.onSuccess();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            errorMsg = e.getMessage();
            LOG.log(Level.WARNING,
                    "AgentExtractor interrupted: {0}", errorMsg);
        } catch (final Exception e) {
            errorMsg = e.getMessage();
            LOG.log(Level.WARNING,
                    "AgentExtractor LLM call failed: {0}", errorMsg);
        }

        final CrawlResult result;
        if (response != null) {
            result = parseAndBuildResult(
                    response.rawContent, context, originChain,
                    pageLinkScore, threadId
            );
            persistLog(jobId, threadId, context.currentUrl(),
                    userPrompt, response, calledAt, null);
        } else {
            result = fallbackResult(
                    context, originChain, pageLinkScore, threadId,
                    String.join("\n\n", positiveChunks)
            );
            persistLog(jobId, threadId, context.currentUrl(),
                    userPrompt, null, calledAt, errorMsg);
        }

        return result;
    }

    private String buildUserPrompt(
            final NavigationContext context,
            final List<String> positiveChunks) {
        final StringBuilder sb = new StringBuilder();
        sb.append("### Contexto de Navegação\n");
        sb.append("Query: ").append(context.userQuery()).append("\n");
        sb.append("URL atual: ").append(context.currentUrl()).append("\n");
        if (context.pageTitle() != null) {
            sb.append("Título: ").append(context.pageTitle()).append("\n");
        }
        sb.append("\n### Chunks Relevantes (").append(positiveChunks.size())
                .append(")\n");
        for (int i = 0; i < positiveChunks.size(); i++) {
            sb.append("\n--- Chunk ").append(i + 1).append(" ---\n");
            sb.append(positiveChunks.get(i));
        }
        return sb.toString();
    }

    private CrawlResult parseAndBuildResult(
            final String rawContent,
            final NavigationContext context,
            final List<String> originChain,
            final double pageLinkScore,
            final String threadId) {
        if (rawContent == null || rawContent.isBlank()) {
            return fallbackResult(
                    context, originChain, pageLinkScore, threadId, rawContent
            );
        }
        try {
            final String json = extractJson(rawContent);
            final JsonNode node = objectMapper.readTree(json);

            final String extractedContent =
                    node.path("extractedContent").asText("");
            final List<String> keyFacts =
                    parseStringArray(node.path("keyFacts"));
            final String completenessStr =
                    node.path("completeness").asText("PARTIAL");
            final Double completeness =
                    "COMPLETE".equalsIgnoreCase(completenessStr) ? 1.0 : 0.5;
            final String missingAspects =
                    node.path("missingAspects").isNull()
                            ? null
                            : node.path("missingAspects").asText(null);

            return new CrawlResult(
                    context.currentUrl(),
                    originChain,
                    context.currentDepth(),
                    pageLinkScore,
                    extractedContent,
                    keyFacts,
                    Map.of(),
                    completeness,
                    missingAspects,
                    context.extractionMethod() != null
                            ? context.extractionMethod()
                            : ExtractionMethod.RAW,
                    Instant.now(),
                    threadId
            );
        } catch (final Exception e) {
            LOG.log(Level.FINE,
                    "AgentExtractor JSON parse error: {0}", e.getMessage());
            return fallbackResult(
                    context, originChain, pageLinkScore, threadId, rawContent
            );
        }
    }

    private static CrawlResult fallbackResult(
            final NavigationContext context,
            final List<String> originChain,
            final double pageLinkScore,
            final String threadId,
            final String rawContent) {
        return new CrawlResult(
                context.currentUrl(),
                originChain,
                context.currentDepth(),
                pageLinkScore,
                rawContent != null ? rawContent : "",
                List.of(),
                Map.of(),
                0.5,
                null,
                context.extractionMethod() != null
                        ? context.extractionMethod()
                        : ExtractionMethod.RAW,
                Instant.now(),
                threadId
        );
    }

    private static String extractJson(final String text) {
        final int start = text.indexOf('{');
        final int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private static List<String> parseStringArray(final JsonNode node) {
        final List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (final JsonNode item : node) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private void persistLog(
            final UUID jobId,
            final String threadId,
            final String pageUrl,
            final String userPrompt,
            final AgentResponse response,
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
                    null,
                    null,
                    SYSTEM_PROMPT,
                    userPrompt,
                    response != null ? response.rawContent : null,
                    null,
                    null,
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
                    "Failed to persist AgentCallLog (extractor): {0}",
                    e.getMessage());
        }
    }
}
