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

package dev.orion.horizon.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Representa a execução de um crawl.
 *
 * <p>Este objeto modela o estado e os agregados de uma execução: configurações
 * usadas, contadores e resultado final.
 */
public final class CrawlJob {
    /** Identificador único do job. */
    public final UUID id;

    /** Consulta do usuário que deu origem ao job. */
    public final String userQuery;

    /** URL raiz do crawl. */
    public final String rootUrl;

    /** Parâmetros efetivos utilizados na execução (snapshot imutável). */
    public final CrawlParameters parameters;

    /** Status atual do job. */
    public final JobStatus status;

    /** Resposta final produzida pelo agente/crawler. */
    public final String finalAnswer;

    /** Motivo de parada, quando aplicável. */
    public final StopReason stopReason;

    /** Lista de erros ocorridos durante o job. */
    public final List<String> errors;

    /** Timestamp de criação do job. */
    public final Instant createdAt;

    /** Timestamp de início do job. */
    public final Instant startedAt;

    /** Timestamp de término do job. */
    public final Instant finishedAt;

    /** Total de páginas visitadas. */
    public final int totalPagesVisited;

    /** Total de resultados encontrados. */
    public final int totalResultsFound;

    /** Total de tokens consumidos. */
    public final long totalTokensConsumed;

    /**
     * Cria uma instância.
     *
     * @param id identificador do job
     * @param userQuery consulta do usuário
     * @param rootUrl URL raiz
     * @param parameters parâmetros efetivos do crawl
     * @param status status do job
     * @param finalAnswer resposta final (pode ser {@code null})
     * @param stopReason motivo de parada (pode ser {@code null})
     * @param errors lista de erros (pode ser {@code null})
     * @param createdAt criação
     * @param startedAt início (pode ser {@code null})
     * @param finishedAt fim (pode ser {@code null})
     * @param totalPagesVisited páginas visitadas
     * @param totalResultsFound resultados encontrados
     * @param totalTokensConsumed tokens consumidos
     */
    public CrawlJob(
            final UUID id,
            final String userQuery,
            final String rootUrl,
            final CrawlParameters parameters,
            final JobStatus status,
            final String finalAnswer,
            final StopReason stopReason,
            final List<String> errors,
            final Instant createdAt,
            final Instant startedAt,
            final Instant finishedAt,
            final int totalPagesVisited,
            final int totalResultsFound,
            final long totalTokensConsumed
    ) {
        this.id = id;
        this.userQuery = userQuery;
        this.rootUrl = rootUrl;
        this.parameters = parameters;
        this.status = status;
        this.finalAnswer = finalAnswer;
        this.stopReason = stopReason;
        this.errors = errors;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.totalPagesVisited = totalPagesVisited;
        this.totalResultsFound = totalResultsFound;
        this.totalTokensConsumed = totalTokensConsumed;
    }
}

