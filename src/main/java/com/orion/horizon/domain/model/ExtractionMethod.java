package com.orion.horizon.domain.model;

/**
 * Estratégia utilizada para extrair conteúdo de uma página.
 */
public enum ExtractionMethod {
    /** Extração usando heurísticas de legibilidade (ex.: Readability). */
    READABILITY,
    /** Extração usando parsing de HTML (ex.: Jsoup). */
    JSOUP,
    /** Extração "bruta" sem pós-processamento relevante. */
    RAW
}

