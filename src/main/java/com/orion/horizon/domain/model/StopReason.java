package com.orion.horizon.domain.model;

/**
 * Razões para encerramento de um job de crawl.
 */
public enum StopReason {
    /** A fila de páginas/links foi exaurida sem resultados adicionais. */
    EXHAUSTED,
    /** Limite de passos atingido. */
    MAX_STEPS,
    /** Tempo máximo atingido. */
    TIMEOUT,
    /** Execução abortada manualmente ou por condição externa. */
    ABORTED
}

