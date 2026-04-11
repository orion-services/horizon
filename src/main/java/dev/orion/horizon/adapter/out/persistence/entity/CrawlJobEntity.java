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

package dev.orion.horizon.adapter.out.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidade JPA para a tabela {@code crawl_jobs}.
 */
@Entity
@Table(name = "crawl_jobs")
public class CrawlJobEntity extends PanacheEntityBase {

    /** Identificador único do job. */
    @Id
    public UUID id;

    /** Status atual do job. */
    @Column(nullable = false)
    public String status;

    /** Consulta do usuário. */
    @Column(name = "user_query", nullable = false, columnDefinition = "TEXT")
    public String userQuery;

    /** URL raiz do crawl. */
    @Column(name = "root_url", nullable = false, columnDefinition = "TEXT")
    public String rootUrl;

    /** Profundidade máxima configurada. */
    @Column(name = "max_depth")
    public int maxDepth;

    /** Passos máximos configurados. */
    @Column(name = "max_steps")
    public int maxSteps;

    /** Timeout da sessão em ms. */
    @Column(name = "timeout_ms")
    public long timeoutMs;

    /** Número de threads. */
    @Column(name = "thread_count")
    public int threadCount;

    /** Resposta final gerada pelo consolidador. */
    @Column(name = "final_answer", columnDefinition = "TEXT")
    public String finalAnswer;

    /** Motivo de parada. */
    @Column(name = "stop_reason", columnDefinition = "TEXT")
    public String stopReason;

    /** Total de páginas visitadas. */
    @Column(name = "total_pages")
    public int totalPages;

    /** Total de resultados encontrados. */
    @Column(name = "total_results")
    public int totalResults;

    /** Total de tokens consumidos. */
    @Column(name = "total_tokens")
    public long totalTokens;

    /** Erros não-fatais ocorridos. */
    @Column(name = "errors")
    public String[] errors;

    /** Timestamp de criação. */
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /** Timestamp de início. */
    @Column(name = "started_at")
    public Instant startedAt;

    /** Timestamp de término. */
    @Column(name = "finished_at")
    public Instant finishedAt;
}
