package com.orion.horizon.domain.model;

/**
 * Estados possíveis de um {@link CrawlJob} durante seu ciclo de vida.
 */
public enum JobStatus {
    /** Job criado, mas ainda não iniciado. */
    PENDING,
    /** Job em execução. */
    RUNNING,
    /** Job finalizado com sucesso. */
    COMPLETED,
    /** Job finalizado com falha. */
    FAILED,
    /** Job interrompido por timeout. */
    TIMEOUT,
    /** Job abortado explicitamente. */
    ABORTED
}

