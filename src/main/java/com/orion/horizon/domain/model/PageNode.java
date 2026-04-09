package com.orion.horizon.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Nó de navegação que representa uma página visitada (ou a visitar).
 *
 * <p>Guarda conteúdo extraído, metadados e estruturas auxiliares para ranking e
 * fila de candidatos.
 */
public final class PageNode {
    /** URL da página. */
    public final String url;

    /** URL de origem (de onde este link foi encontrado). */
    public final String originUrl;

    /** Profundidade atual na navegação. */
    public final int depth;

    /** Score recebido durante ranking/avaliação de links. */
    public final Double scoreReceived;

    /** Justificativa do ranker para o score. */
    public final String rankerJustification;

    /** Conteúdo extraído (texto principal). */
    public final String extractedContent;

    /** Método de extração do conteúdo. */
    public final ExtractionMethod extractionMethod;

    /** Lista de chunks produzidos a partir do conteúdo. */
    public final List<String> chunks;

    /** Todos os links extraídos da página. */
    public final List<String> allLinks;

    /** Fila de candidatos a visitar, ordenada por score final. */
    public final PriorityQueue<LinkCandidate> candidateQueue;

    /** Status de processamento desta página. */
    public final PageStatus status;

    /** Data/hora em que a página foi visitada. */
    public final Instant visitedAt;

    /** Motivo de falha, quando aplicável. */
    public final String failureReason;

    /** Resultado parcial associado à página, se existente. */
    public final CrawlResult partialResult;

    /**
     * Cria um {@link PageNode}.
     *
     * @param url URL da página
     * @param originUrl URL de origem
     * @param depth profundidade
     * @param scoreReceived score recebido
     * @param rankerJustification justificativa do ranker
     * @param extractedContent conteúdo extraído
     * @param extractionMethod método de extração
     * @param chunks chunks do conteúdo
     * @param allLinks todos os links encontrados
     * @param candidateQueue fila de candidatos
     * @param status status atual
     * @param visitedAt instante de visita
     * @param failureReason motivo de falha
     * @param partialResult resultado parcial
     */
    public PageNode(
            final String url,
            final String originUrl,
            final int depth,
            final Double scoreReceived,
            final String rankerJustification,
            final String extractedContent,
            final ExtractionMethod extractionMethod,
            final List<String> chunks,
            final List<String> allLinks,
            final PriorityQueue<LinkCandidate> candidateQueue,
            final PageStatus status,
            final Instant visitedAt,
            final String failureReason,
            final CrawlResult partialResult
    ) {
        this.url = Objects.requireNonNull(url, "url");
        this.originUrl = originUrl;
        this.depth = depth;
        this.scoreReceived = scoreReceived;
        this.rankerJustification = rankerJustification;
        this.extractedContent = extractedContent;
        this.extractionMethod = extractionMethod;
        this.chunks = chunks;
        this.allLinks = allLinks;
        this.candidateQueue =
                candidateQueue != null ? candidateQueue : new PriorityQueue<>();
        this.status = status != null ? status : PageStatus.PENDING;
        this.visitedAt = visitedAt;
        this.failureReason = failureReason;
        this.partialResult = partialResult;
    }
}

