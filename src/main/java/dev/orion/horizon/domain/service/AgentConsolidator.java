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
import dev.orion.horizon.domain.model.LLMRequest;
import dev.orion.horizon.domain.model.StopReason;
import dev.orion.horizon.domain.port.out.LLMProviderPort;
import dev.orion.horizon.domain.port.out.PersistencePort;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Agente 4 — consolida todos os resultados parciais numa resposta final.
 *
 * <p>Chamado uma única vez ao final do crawl. Agrega todos os
 * {@link CrawlResult} parciais e produz um answer em markdown com referências
 * às URLs de origem.
 */
public final class AgentConsolidator {

    /** Role do agente para logs. */
    public static final String AGENT_ROLE = "consolidator";

    private static final Logger LOG =
            Logger.getLogger(AgentConsolidator.class.getName());

    private static final String SYSTEM_PROMPT =
            "Você é um consolidador de informações de múltiplas páginas web.\n"
            + "1. Analisar todos os fragmentos coletados\n"
            + "2. Eliminar duplicações e redundâncias\n"
            + "3. Identificar lacunas (fragmentos PARTIAL)\n"
            + "4. Produzir resposta coesa com markdown\n"
            + "5. Incluir referências às URLs de origem\n\n"
            + "Responda APENAS com JSON válido:\n"
            + "{\n"
            + "  \"answer\": \"Resposta completa em markdown\",\n"
            + "  \"fields\": {},\n"
            + "  \"sources\": [\n"
            + "    { \"url\": \"...\","
            + " \"found\": true,"
            + " \"confidence\": \"low|medium|high\","
            + " \"pagesVisited\": N }\n"
            + "  ],\n"
            + "  \"coverageAssessment\":"
            + " \"COMPLETE|PARTIAL|INSUFFICIENT\",\n"
            + "  \"missingAspects\": \"...ou null\"\n"
            + "}";

    private final LLMProviderPort llmProvider;
    private final PersistencePort persistence;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;
    private final int maxTokens;

    /**
     * Cria um AgentConsolidator.
     *
     * @param llmProvider provedor LLM
     * @param persistence porta de persistência
     * @param objectMapper mapper JSON
     * @param rateLimiter limitador de taxa compartilhado
     * @param maxTokens teto de tokens de saída
     */
    public AgentConsolidator(
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
     * Consolida os resultados parciais numa resposta final em markdown.
     *
     * @param jobId identificador do job de crawl
     * @param userQuery consulta original do usuário
     * @param rootUrl URL raiz do crawl
     * @param startedAt instante de início do crawl
     * @param finishedAt instante de fim do crawl
     * @param stopReason razão de parada do crawl
     * @param totalPagesVisited total de páginas visitadas
     * @param results todos os CrawlResult parciais coletados
     * @return resposta final em markdown; nunca {@code null}
     */
    public String consolidate(
            final UUID jobId,
            final String userQuery,
            final String rootUrl,
            final Instant startedAt,
            final Instant finishedAt,
            final StopReason stopReason,
            final int totalPagesVisited,
            final List<CrawlResult> results) {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(userQuery, "userQuery");
        Objects.requireNonNull(results, "results");

        final String userPrompt = buildUserPrompt(
                userQuery, rootUrl, startedAt, finishedAt,
                stopReason, totalPagesVisited, results
        );
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
                    "AgentConsolidator interrupted: {0}", errorMsg);
        } catch (final Exception e) {
            errorMsg = e.getMessage();
            LOG.log(Level.WARNING,
                    "AgentConsolidator LLM call failed: {0}", errorMsg);
        }

        persistLog(jobId, userPrompt, response, calledAt, errorMsg);

        if (response == null || response.rawContent == null) {
            return buildFallbackAnswer(results);
        }

        return parseAnswer(response.rawContent, results);
    }

    private String buildUserPrompt(
            final String userQuery,
            final String rootUrl,
            final Instant startedAt,
            final Instant finishedAt,
            final StopReason stopReason,
            final int totalPagesVisited,
            final List<CrawlResult> results) {
        final StringBuilder sb = new StringBuilder();
        sb.append("### Consulta Original\n").append(userQuery).append("\n\n");
        sb.append("### Metadados do Crawl\n");
        sb.append("URL raiz: ").append(rootUrl).append("\n");
        if (startedAt != null) {
            sb.append("Iniciado em: ").append(startedAt).append("\n");
        }
        if (finishedAt != null) {
            sb.append("Finalizado em: ").append(finishedAt).append("\n");
        }
        if (stopReason != null) {
            sb.append("Critério de parada: ").append(stopReason).append("\n");
        }
        sb.append("Total de páginas visitadas: ")
                .append(totalPagesVisited).append("\n\n");
        sb.append("### Resultados Parciais (")
                .append(results.size()).append(")\n");
        for (int i = 0; i < results.size(); i++) {
            final CrawlResult r = results.get(i);
            sb.append("\n--- Resultado ").append(i + 1).append(" ---\n");
            sb.append("URL: ").append(r.sourceUrl).append("\n");
            sb.append("Profundidade: ").append(r.foundAtDepth).append("\n");
            sb.append("Completude: ")
                    .append(r.completeness != null
                            ? r.completeness : "?").append("\n");
            if (r.missingAspects != null) {
                sb.append("Aspectos faltantes: ")
                        .append(r.missingAspects).append("\n");
            }
            sb.append("Conteúdo:\n").append(r.extractedContent).append("\n");
            if (r.keyFacts != null && !r.keyFacts.isEmpty()) {
                sb.append("Fatos-chave:\n");
                for (final String fact : r.keyFacts) {
                    sb.append("- ").append(fact).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String parseAnswer(
            final String rawContent,
            final List<CrawlResult> results) {
        try {
            final String json = extractJson(rawContent);
            final JsonNode node = objectMapper.readTree(json);
            final String answer = node.path("answer").asText(null);
            if (answer != null && !answer.isBlank()) {
                return answer;
            }
        } catch (final Exception e) {
            LOG.log(Level.FINE,
                    "AgentConsolidator JSON parse error: {0}",
                    e.getMessage());
        }
        return rawContent.isBlank() ? buildFallbackAnswer(results) : rawContent;
    }

    private static String buildFallbackAnswer(final List<CrawlResult> results) {
        final StringBuilder sb = new StringBuilder();
        sb.append("## Resultados do Crawl\n\n");
        for (final CrawlResult r : results) {
            sb.append("### ").append(r.sourceUrl).append("\n\n");
            if (r.extractedContent != null
                    && !r.extractedContent.isBlank()) {
                sb.append(r.extractedContent).append("\n\n");
            }
        }
        return sb.toString();
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
            final String userPrompt,
            final AgentResponse response,
            final Instant calledAt,
            final String errorMsg) {
        try {
            final AgentCallLog log = new AgentCallLog(
                    jobId,
                    "orchestrator",
                    AGENT_ROLE,
                    response != null
                            ? response.providerUsed
                            : llmProvider.getProviderName(),
                    response != null
                            ? response.modelUsed
                            : llmProvider.getModelName(),
                    null,
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
                    "Failed to persist AgentCallLog (consolidator): {0}",
                    e.getMessage());
        }
    }
}
