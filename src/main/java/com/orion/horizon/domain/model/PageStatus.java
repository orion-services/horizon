package com.orion.horizon.domain.model;

/**
 * Estado de processamento de uma página dentro de um job de crawl.
 */
public enum PageStatus {
    /** A página ainda não foi visitada. */
    PENDING,
    /** A página está em processamento (fetch/render/extraction). */
    PROCESSING,
    /** A página foi processada sem gerar resultado final. */
    DONE,
    /** Um resultado relevante foi encontrado a partir desta página. */
    RESULT_FOUND,
    /**
     * A página foi descartada (p.ex. baixa pontuação, fora do domínio, etc.).
     */
    DISCARDED
}

