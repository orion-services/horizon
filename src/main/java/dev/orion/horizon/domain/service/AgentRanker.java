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
import java.util.regex.Pattern;

/**
 * Agente ranqueador de links — fase única com enriquecimento prévio.
 *
 * <p>Antes de chamar o LLM, enriquece <em>todos</em> os candidatos via
 * {@link LinkEnricherPort} (title + metaDescription) e aplica um
 * <em>pré-filtro léxico</em>: candidatos cujo URL, âncora ou título
 * contenham algum token significativo da query são aprovados imediatamente,
 * sem custo de tokens, com score sintético {@value #LEXICAL_SYNTHETIC_SCORE}.
 * Apenas os candidatos sem correspondência léxica são enviados ao LLM.
 *
 * <p>Se a resposta do LLM for truncada (JSON sem {@code ]}), o parser tenta
 * recuperar as entradas completas antes do ponto de corte.
 *
 * <p>Candidatos com {@code finalScore < finalThreshold} são descartados.
 * O resultado é uma {@link PriorityQueue} ordenada por score decrescente.
 */
public final class AgentRanker {

    /** Role do agente para logs. */
    public static final String AGENT_ROLE = "ranker";

    private static final Logger LOG =
            Logger.getLogger(AgentRanker.class.getName());

    /**
     * Score sintético atribuído a candidatos aprovados pelo pré-filtro léxico.
     * Deve ser maior que {@code finalThreshold} para garantir que eles sejam
     * enfileirados, mas menor que 1.0 para não suprimir scores reais do LLM.
     */
    static final double LEXICAL_SYNTHETIC_SCORE = 0.85;

    /** Comprimento mínimo de um token de query para ser usado no filtro. */
    private static final int LEXICAL_MIN_TOKEN_LENGTH = 3;

    /** Padrão para remover caracteres não-alfanuméricos dos tokens. */
    private static final Pattern NON_ALNUM =
            Pattern.compile("[^\\p{L}\\p{N}]");

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
     * @param finalThreshold limiar mínimo de score para aceitar um link
     * @param jsoupEnrichTimeoutMs timeout para enriquecimento Jsoup (ms)
     */
    public AgentRanker(
            final LLMProviderPort llmProvider,
            final LinkEnricherPort enricher,
            final PersistencePort persistence,
            final ObjectMapper objectMapper,
            final RateLimiter rateLimiter,
            final int maxTokens,
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
        this.finalThreshold = finalThreshold;
        this.jsoupEnrichTimeoutMs = jsoupEnrichTimeoutMs;
    }

    /**
     * Enriquece todos os candidatos, aplica pré-filtro léxico e executa
     * ranking via LLM nos candidatos restantes.
     *
     * <p>Candidatos cujo URL, âncora ou título contenha algum token da query
     * são aprovados imediatamente (pré-filtro léxico) sem chamar o LLM.
     * Os demais são enviados ao LLM para ranking.
     *
     * @param jobId identificador do job de crawl
     * @param threadId identificador da thread
     * @param userQuery consulta do usuário
     * @param currentUrl URL atual sendo processada
     * @param candidates lista de candidatos a ranquear
     * @return fila de prioridade com candidatos aprovados, ordenada por
     *         score decrescente
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

        enricher.enrich(candidates, jsoupEnrichTimeoutMs);

        final List<LinkCandidate> lexicalApproved = new ArrayList<>();
        final List<LinkCandidate> toRank =
                lexicalSplit(candidates, userQuery, lexicalApproved);

        if (!lexicalApproved.isEmpty()) {
            LOG.log(Level.INFO,
                    "Pré-filtro léxico: {0} aprovados sem LLM,"
                    + " {1} enviados ao LLM",
                    new Object[]{lexicalApproved.size(), toRank.size()});
        }

        final List<LinkCandidate> llmApproved;
        if (!toRank.isEmpty()) {
            llmApproved = runRanking(
                    jobId, threadId, userQuery, currentUrl, toRank);
        } else {
            llmApproved = List.of();
        }

        final PriorityQueue<LinkCandidate> queue = new PriorityQueue<>();
        queue.addAll(lexicalApproved);
        queue.addAll(llmApproved);
        return queue;
    }

    /**
     * Separa candidatos em dois grupos: os que possuem correspondência léxica
     * com a query (aprovados diretamente) e os que serão enviados ao LLM.
     *
     * <p>Visibilidade package-private para testes unitários.
     *
     * @param candidates candidatos a classificar
     * @param userQuery consulta do usuário
     * @param approved lista de saída para candidatos aprovados pelo pré-filtro
     * @return candidatos sem correspondência léxica (a enviar ao LLM)
     */
    static List<LinkCandidate> lexicalSplit(
            final List<LinkCandidate> candidates,
            final String userQuery,
            final List<LinkCandidate> approved) {
        final List<String> tokens = queryTokens(userQuery);
        final List<LinkCandidate> remaining = new ArrayList<>();
        for (final LinkCandidate c : candidates) {
            if (!tokens.isEmpty() && hasLexicalMatch(c, tokens)) {
                approved.add(new LinkCandidate(
                        c.url(), c.anchorText(), c.domContext(), c.ariaLabel(),
                        c.phase1Score(), c.phase1Justification(),
                        c.pageTitle(), c.metaDescription(),
                        c.enrichmentFailed(),
                        LEXICAL_SYNTHETIC_SCORE,
                        "pré-filtro léxico: token da query encontrado"
                ));
            } else {
                remaining.add(c);
            }
        }
        return remaining;
    }

    /**
     * Extrai tokens significativos da query (lowercase, sem pontuação,
     * comprimento mínimo {@value #LEXICAL_MIN_TOKEN_LENGTH}).
     *
     * <p>Visibilidade package-private para testes unitários.
     *
     * @param query consulta do usuário em linguagem natural
     * @return lista de tokens normalizados com comprimento adequado
     */
    static List<String> queryTokens(final String query) {
        final List<String> tokens = new ArrayList<>();
        for (final String raw : query.split("\\s+")) {
            final String token =
                    NON_ALNUM.matcher(raw).replaceAll("").toLowerCase();
            if (token.length() >= LEXICAL_MIN_TOKEN_LENGTH) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * Verifica se algum token da query aparece no URL, âncora ou título do
     * candidato.
     *
     * @param c candidato a verificar
     * @param tokens tokens normalizados da query
     * @return {@code true} se houver correspondência léxica
     */
    private static boolean hasLexicalMatch(
            final LinkCandidate c, final List<String> tokens) {
        final String urlLower = c.url().toLowerCase();
        final String anchorLower =
                c.anchorText() != null ? c.anchorText().toLowerCase() : "";
        final String titleLower =
                c.pageTitle() != null ? c.pageTitle().toLowerCase() : "";
        for (final String token : tokens) {
            if (urlLower.contains(token)
                    || anchorLower.contains(token)
                    || titleLower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private List<LinkCandidate> runRanking(
            final UUID jobId,
            final String threadId,
            final String userQuery,
            final String currentUrl,
            final List<LinkCandidate> candidates) {
        final String userPrompt =
                buildRankingPrompt(userQuery, currentUrl, candidates);
        final LLMRequest request = new LLMRequest(
                SYSTEM_PROMPT, userPrompt, AGENT_ROLE, maxTokens
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
                    "AgentRanker interrupted: {0}", e.getMessage());
            persistLog(jobId, threadId, currentUrl, userPrompt,
                    null, calledAt, e.getMessage());
            return new ArrayList<>(candidates);
        } catch (final Exception e) {
            LOG.log(Level.WARNING,
                    "AgentRanker failed: {0}", e.getMessage());
            persistLog(jobId, threadId, currentUrl, userPrompt,
                    null, calledAt, e.getMessage());
            return new ArrayList<>(candidates);
        }

        persistLog(jobId, threadId, currentUrl, userPrompt,
                response, calledAt, null);

        final List<RankEntry> entries =
                parseRankArray(response.rawContent);
        return applyScores(candidates, entries);
    }

    private List<LinkCandidate> applyScores(
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
            if (score >= finalThreshold) {
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
                        score,
                        justification
                ));
            }
        }
        return result;
    }

    private static String buildRankingPrompt(
            final String userQuery,
            final String currentUrl,
            final List<LinkCandidate> candidates) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Query: ").append(userQuery).append("\n");
        sb.append("URL atual: ").append(currentUrl).append("\n\n");
        sb.append("Links a avaliar:\n");
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
            LOG.log(Level.WARNING,
                    "AgentRanker JSON parse error (possível truncamento da "
                    + "resposta do LLM — aumente max-tokens do ranker): {0}",
                    e.getMessage());
        }
        return result;
    }

    /**
     * Extrai o array JSON da resposta do LLM.
     *
     * <p>Primeiro tenta localizar {@code [...]} completo. Se o array estiver
     * truncado (sem {@code ]} final, indicando que o LLM atingiu o limite de
     * tokens), tenta recuperar as entradas completas localizando o último
     * {@code \}} antes do ponto de corte e fechando o array manualmente.
     *
     * <p>Visibilidade package-private para testes unitários.
     *
     * @param text resposta bruta do LLM
     * @return string contendo o array JSON (possivelmente parcial, mas válido)
     */
    static String extractJsonArray(final String text) {
        final int start = text.indexOf('[');
        final int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        if (start >= 0) {
            final int lastClose = text.lastIndexOf('}');
            if (lastClose > start) {
                LOG.log(Level.WARNING,
                        "Resposta do ranker truncada — recuperando entradas "
                        + "completas até posição {0}",
                        lastClose);
                return text.substring(start, lastClose + 1) + "]";
            }
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
