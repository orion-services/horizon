package com.orion.horizon.domain.model;

import java.util.Objects;

/**
 * Candidato a link a ser visitado/evaluado durante o crawl.
 *
 * <p>Esta entidade agrega dados do DOM (âncora, contexto), enriquecimento
 * (título, descrição) e as pontuações/justificativas de ranking em duas fases.
 *
 * <p>A ordenação padrão ({@link #compareTo(LinkCandidate)}) é por
 * {@link #finalScore()} em ordem decrescente, para uso direto em
 * {@link java.util.PriorityQueue}.
 */
public final class LinkCandidate implements Comparable<LinkCandidate> {
    private final String url;
    private final String anchorText;
    private final String domContext;
    private final String ariaLabel;

    private final Double phase1Score;
    private final String phase1Justification;

    private final String pageTitle;
    private final String metaDescription;
    private final boolean enrichmentFailed;

    private final Double finalScore;
    private final String finalJustification;

    /**
     * Cria um candidato de link.
     *
     * @param url URL do candidato
     * @param anchorText texto âncora (pode ser {@code null})
     * @param domContext contexto do DOM (pode ser {@code null})
     * @param ariaLabel aria-label (pode ser {@code null})
     * @param phase1Score score da fase 1 (pode ser {@code null})
     * @param phase1Justification justificativa da fase 1
     *                           (pode ser {@code null})
     * @param pageTitle título da página (pode ser {@code null})
     * @param metaDescription meta description (pode ser {@code null})
     * @param enrichmentFailed se enriquecimento falhou
     * @param finalScore score final (pode ser {@code null})
     * @param finalJustification justificativa final (pode ser {@code null})
     */
    public LinkCandidate(
            final String url,
            final String anchorText,
            final String domContext,
            final String ariaLabel,
            final Double phase1Score,
            final String phase1Justification,
            final String pageTitle,
            final String metaDescription,
            final boolean enrichmentFailed,
            final Double finalScore,
            final String finalJustification
    ) {
        this.url = Objects.requireNonNull(url, "url");
        this.anchorText = anchorText;
        this.domContext = domContext;
        this.ariaLabel = ariaLabel;
        this.phase1Score = phase1Score;
        this.phase1Justification = phase1Justification;
        this.pageTitle = pageTitle;
        this.metaDescription = metaDescription;
        this.enrichmentFailed = enrichmentFailed;
        this.finalScore = finalScore;
        this.finalJustification = finalJustification;
    }

    /** @return URL do candidato */
    public String url() {
        return url;
    }

    /** @return texto âncora, ou {@code null} */
    public String anchorText() {
        return anchorText;
    }

    /** @return contexto do DOM, ou {@code null} */
    public String domContext() {
        return domContext;
    }

    /** @return aria-label, ou {@code null} */
    public String ariaLabel() {
        return ariaLabel;
    }

    /** @return score da fase 1, ou {@code null} */
    public Double phase1Score() {
        return phase1Score;
    }

    /** @return justificativa da fase 1, ou {@code null} */
    public String phase1Justification() {
        return phase1Justification;
    }

    /** @return título da página, ou {@code null} */
    public String pageTitle() {
        return pageTitle;
    }

    /** @return meta description, ou {@code null} */
    public String metaDescription() {
        return metaDescription;
    }

    /** @return {@code true} se o enriquecimento falhou */
    public boolean enrichmentFailed() {
        return enrichmentFailed;
    }

    /** @return score final, ou {@code null} */
    public Double finalScore() {
        return finalScore;
    }

    /** @return justificativa final, ou {@code null} */
    public String finalJustification() {
        return finalJustification;
    }

    @Override
    public int compareTo(final LinkCandidate other) {
        Objects.requireNonNull(other, "other");
        final double thisScore =
                this.finalScore == null
                        ? Double.NEGATIVE_INFINITY
                        : this.finalScore;
        final double otherScore =
                other.finalScore == null
                        ? Double.NEGATIVE_INFINITY
                        : other.finalScore;

        final int scoreOrder = Double.compare(otherScore, thisScore);
        if (scoreOrder != 0) {
            return scoreOrder;
        }

        return this.url.compareTo(other.url);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LinkCandidate that)) {
            return false;
        }
        return enrichmentFailed == that.enrichmentFailed
                && url.equals(that.url)
                && Objects.equals(anchorText, that.anchorText)
                && Objects.equals(domContext, that.domContext)
                && Objects.equals(ariaLabel, that.ariaLabel)
                && Objects.equals(phase1Score, that.phase1Score)
                && Objects.equals(phase1Justification, that.phase1Justification)
                && Objects.equals(pageTitle, that.pageTitle)
                && Objects.equals(metaDescription, that.metaDescription)
                && Objects.equals(finalScore, that.finalScore)
                && Objects.equals(finalJustification, that.finalJustification);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                url,
                anchorText,
                domContext,
                ariaLabel,
                phase1Score,
                phase1Justification,
                pageTitle,
                metaDescription,
                enrichmentFailed,
                finalScore,
                finalJustification
        );
    }
}

