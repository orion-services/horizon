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
import dev.orion.horizon.domain.model.LinkCandidate;
import dev.orion.horizon.domain.model.LLMRequest;
import dev.orion.horizon.domain.port.out.LinkEnricherPort;
import dev.orion.horizon.domain.port.out.LLMProviderPort;
import dev.orion.horizon.domain.port.out.PersistencePort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Agente 2 — ranqueia links em duas fases com enriquecimento Jsoup entre elas.
 *
 * <p>Fase 1: score inicial a partir de texto âncora e contexto DOM.
 * Links abaixo de {@code preThreshold} são descartados.
 * Fase 2: após enriquecimento (title, metaDescription), score final.
 * Links abaixo de {@code finalThreshold} são descartados.
 * O resultado é uma {@link PriorityQueue} ordenada por score final decrescente.
 */
public final class AgentRanker {

    /** Role do agente para logs. */
    public static final String AGENT_ROLE = "ranker";

    private static final Logger LOG =
            Logger.getLogger(AgentRanker.class.getName());

    private static final String SYSTEM_PROMPT =
            "Você é um ranqueador de links de navegação web.\n"
            + "Avalie links e atribua scores de relevância (0.0 a 1.0).\n"
            + "Avalie comparativamente entre os links.\n\n"
            + "Responda APENAS com array JSON:\n"
            + "[{ \"url\": \"...\","
            + " \"score\": 0.0-1.0,"
            + " \"justification\": \"...\" }]";

    private final LLMProviderPort llmProvider;
    private final LinkEnricherPort enricher;
    private final PersistencePort persistence;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;
    private final int maxTokens;
    private final double preThreshold;
    private final double finalThreshold;
    private final long jsoupEnrichTimeoutMs;

    /**
     * Cria um AgentRanker.
     *
     * @param llmProvider provedor LLM
     * @param enricher adaptador de enriquecimento de links
     * @param persistence porta de persistência
     * @param objectMapper mapper JSON
     * @param rateLimiter limitador de taxa compartilhado
     * @param maxTokens teto de tokens de saída
     * @param preThreshold limiar para filtro após fase 1
     * @param finalThreshold limiar para filtro após fase 2
     * @param jsoupEnrichTimeoutMs timeout para enriquecimento Jsoup (ms)
     */
    public AgentRanker(
            final LLMProviderPort llmProvider,
            final LinkEnricherPort enricher,
            final PersistencePort persistence,
            final ObjectMapper objectMapper,
            final RateLimiter rateLimiter,
            final int maxTokens,
            final double preThreshold,
            final double finalThreshold,
            final long jsoupEnrichTimeoutMs) {
        this.llmProvider =
                Objects.requireNonNull(llmProvider, "llmProvider");
        this.enricher =
                Objects.requireNonNull(enricher, "enricher");
        this.persistence =
                Objects.requireNonNull(persistence, "persistence");
        this.objectMapper =
                Objects.requireNonNull(objectMapper, "objectMapper");
        this.rateLimiter =
                Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.maxTokens = maxTokens;
        this.preThreshold = preThreshold;
        this.finalThreshold = finalThreshold;
        this.jsoupEnrichTimeoutMs = jsoupEnrichTimeoutMs;
    }

    /**
     * Executa o ranking de duas fases para os links fornecidos.
     *
     * @param jobId identificador do job de crawl
     * @param threadId identificador da thread
     * @param userQuery consulta do usuário
     * @param currentUrl URL atual sendo processada
     * @param candidates lista de candidatos a ranquear
     * @return fila de prioridade com candidatos aprovados, ordenada por
     *         score final decrescente
     */
    public PriorityQueue<LinkCandidate> rank(
            final UUID jobId,
            final String threadId,
            final String userQuery,
            final String currentUrl,
            final List<LinkCandidate> candidates) {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(userQuery, "userQuery");
        Objects.requireNonNull(currentUrl, "currentUrl");
        Objects.requireNonNull(candidates, "candidates");

        if (candidates.isEmpty()) {
            return new PriorityQueue<>();
        }

        final List<LinkCandidate> afterPhase1 =
                runPhase1(jobId, threadId, userQuery, currentUrl, candidates);

        enricher.enrich(afterPhase1, jsoupEnrichTimeoutMs);

        final List<LinkCandidate> afterPhase2 =
                runPhase2(jobId, threadId, userQuery, currentUrl, afterPhase1);

        final PriorityQueue<LinkCandidate> queue = new PriorityQueue<>();
        queue.addAll(afterPhase2);
        return queue;
    }

    private List<LinkCandidate> runPhase1(
            final UUID jobId,
            final String threadId,
            final String userQuery,
            final String currentUrl,
            final List<LinkCandidate> candidates) {
        final String userPrompt =
                buildPhase1Prompt(userQuery, currentUrl, candidates);
        final LLMRequest request = new LLMRequest(
                SYSTEM_PROMPT, userPrompt, AGENT_ROLE + "-phase1", maxTokens
        );

        final Instant calledAt = Instant.now();
        AgentResponse response = null;
        try {
            rateLimiter.acquire();
            response = llmProvider.call(request);
            rateLimiter.onSuccess();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING,
                    "AgentRanker phase1 interrupted: {0}", e.getMessage());
            persistLog(jobId, threadId, currentUrl, userPrompt,
                    null, calledAt, e.getMessage());
            return new ArrayList<>(candidates);
        } catch (final Exception e) {
            LOG.log(Level.WARNING,
                    "AgentRanker phase1 failed: {0}", e.getMessage());
            persistLog(jobId, threadId, currentUrl, userPrompt,
                    null, calledAt, e.getMessage());
            return new ArrayList<>(candidates);
        }

        persistLog(jobId, threadId, currentUrl, userPrompt,
                response, calledAt, null);

        final List<RankEntry> entries =
                parseRankArray(response.rawContent);
        return applyPhase1Scores(candidates, entries);
    }

    private List<LinkCandidate> applyPhase1Scores(
            final List<LinkCandidate> candidates,
            final List<RankEntry> entries) {
        final List<LinkCandidate> result = new ArrayList<>();
        for (final LinkCandidate candidate : candidates) {
            double score = 0.0;
            String justification = null;
            for (final RankEntry entry : entries) {
                if (candidate.url().equals(entry.url)) {
                    score = entry.score;
                    justification = entry.justification;
                    break;
                }
            }
            if (score >= preThreshold) {
                result.add(new LinkCandidate(
                        candidate.url(),
                        candidate.anchorText(),
                        candidate.domContext(),
                        candidate.ariaLabel(),
                        score,
                        justification,
                        candidate.pageTitle(),
                        candidate.metaDescription(),
                        candidate.enrichmentFailed(),
                        null,
                        null
                ));
            }
        }
        return result;
    }

    private List<LinkCandidate> runPhase2(
            final UUID jobId,
            final String threadId,
            final String userQuery,
            final String currentUrl,
            final List<LinkCandidate> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        final String userPrompt =
                buildPhase2Prompt(userQuery, currentUrl, candidates);
        final LLMRequest request = new LLMRequest(
                SYSTEM_PROMPT, userPrompt, AGENT_ROLE + "-phase2", maxTokens
        );

        final Instant calledAt = Instant.now();
        AgentResponse response = null;
        try {
            rateLimiter.acquire();
            response = llmProvider.call(request);
            rateLimiter.onSuccess();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING,
                    "AgentRanker phase2 interrupted: {0}", e.getMessage());
            persistLog(jobId, threadId, currentUrl, userPrompt,
                    null, calledAt, e.getMessage());
            return applyFinalScoresFromPhase1(candidates);
        } catch (final Exception e) {
            LOG.log(Level.WARNING,
                    "AgentRanker phase2 failed: {0}", e.getMessage());
            persistLog(jobId, threadId, currentUrl, userPrompt,
                    null, calledAt, e.getMessage());
            return applyFinalScoresFromPhase1(candidates);
        }

        persistLog(jobId, threadId, currentUrl, userPrompt,
                response, calledAt, null);

        final List<RankEntry> entries =
                parseRankArray(response.rawContent);
        return applyPhase2Scores(candidates, entries);
    }

    private List<LinkCandidate> applyPhase2Scores(
            final List<LinkCandidate> candidates,
            final List<RankEntry> entries) {
        final List<LinkCandidate> result = new ArrayList<>();
        for (final LinkCandidate candidate : candidates) {
            double score = candidate.phase1Score() != null
                    ? candidate.phase1Score() : 0.0;
            String justification = candidate.phase1Justification();
            for (final RankEntry entry : entries) {
                if (candidate.url().equals(entry.url)) {
                    score = entry.score;
                    justification = entry.justification;
                    break;
                }
            }
            if (score >= finalThreshold) {
                result.add(new LinkCandidate(
                        candidate.url(),
                        candidate.anchorText(),
                        candidate.domContext(),
                        candidate.ariaLabel(),
                        candidate.phase1Score(),
                        candidate.phase1Justification(),
                        candidate.pageTitle(),
                        candidate.metaDescription(),
                        candidate.enrichmentFailed(),
                        score,
                        justification
                ));
            }
        }
        return result;
    }

    private List<LinkCandidate> applyFinalScoresFromPhase1(
            final List<LinkCandidate> candidates) {
        final List<LinkCandidate> result = new ArrayList<>();
        for (final LinkCandidate c : candidates) {
            final double score =
                    c.phase1Score() != null ? c.phase1Score() : 0.0;
            if (score >= finalThreshold) {
                result.add(new LinkCandidate(
                        c.url(), c.anchorText(), c.domContext(),
                        c.ariaLabel(), c.phase1Score(),
                        c.phase1Justification(), c.pageTitle(),
                        c.metaDescription(), c.enrichmentFailed(),
                        score, c.phase1Justification()
                ));
            }
        }
        return result;
    }

    private static String buildPhase1Prompt(
            final String userQuery,
            final String currentUrl,
            final List<LinkCandidate> candidates) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Query: ").append(userQuery).append("\n");
        sb.append("URL atual: ").append(currentUrl).append("\n\n");
        sb.append("Links a avaliar:\n");
        for (final LinkCandidate c : candidates) {
            sb.append("- URL: ").append(c.url()).append("\n");
            if (c.anchorText() != null && !c.anchorText().isBlank()) {
                sb.append("  Âncora: ").append(c.anchorText()).append("\n");
            }
            if (c.domContext() != null && !c.domContext().isBlank()) {
                sb.append("  Contexto DOM: ")
                        .append(c.domContext()).append("\n");
            }
        }
        return sb.toString();
    }

    private static String buildPhase2Prompt(
            final String userQuery,
            final String currentUrl,
            final List<LinkCandidate> candidates) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Query: ").append(userQuery).append("\n");
        sb.append("URL atual: ").append(currentUrl).append("\n\n");
        sb.append("Links enriquecidos a avaliar:\n");
        for (final LinkCandidate c : candidates) {
            sb.append("- URL: ").append(c.url()).append("\n");
            if (c.pageTitle() != null && !c.pageTitle().isBlank()) {
                sb.append("  Título: ").append(c.pageTitle()).append("\n");
            }
            if (c.metaDescription() != null
                    && !c.metaDescription().isBlank()) {
                sb.append("  Descrição: ")
                        .append(c.metaDescription()).append("\n");
            }
            if (c.phase1Score() != null) {
                sb.append("  Score fase 1: ")
                        .append(c.phase1Score()).append("\n");
            }
        }
        return sb.toString();
    }

    private List<RankEntry> parseRankArray(final String rawContent) {
        final List<RankEntry> result = new ArrayList<>();
        if (rawContent == null || rawContent.isBlank()) {
            return result;
        }
        try {
            final String json = extractJsonArray(rawContent);
            final JsonNode array = objectMapper.readTree(json);
            if (array.isArray()) {
                for (final JsonNode item : array) {
                    final String url =
                            item.path("url").asText(null);
                    final double score =
                            item.path("score").asDouble(0.0);
                    final String justification =
                            item.path("justification").asText(null);
                    if (url != null) {
                        result.add(new RankEntry(url, score, justification));
                    }
                }
            }
        } catch (final Exception e) {
            LOG.log(Level.FINE,
                    "AgentRanker JSON parse error: {0}", e.getMessage());
        }
        return result;
    }

    private static String extractJsonArray(final String text) {
        final int start = text.indexOf('[');
        final int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
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
                    "Failed to persist AgentCallLog (ranker): {0}",
                    e.getMessage());
        }
    }

    /**
     * Entrada de ranking retornada pelo LLM.
     */
    private static final class RankEntry {
        private final String url;
        private final double score;
        private final String justification;

        private RankEntry(
                final String url,
                final double score,
                final String justification) {
            this.url = url;
            this.score = score;
            this.justification = justification;
        }
    }
}
