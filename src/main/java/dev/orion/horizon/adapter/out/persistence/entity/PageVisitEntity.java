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
 * Entidade JPA para a tabela {@code page_visits}.
 */
@Entity
@Table(name = "page_visits")
public class PageVisitEntity extends PanacheEntityBase {

    /** Identificador único da visita. */
    @Id
    public UUID id;

    /** Referência ao job de crawl. */
    @Column(name = "job_id", nullable = false)
    public UUID jobId;

    /** Identificador da thread que realizou a visita. */
    @Column(name = "thread_id")
    public String threadId;

    /** URL visitada. */
    @Column(nullable = false)
    public String url;

    /** URL de origem. */
    @Column(name = "origin_url")
    public String originUrl;

    /** Profundidade na árvore de navegação. */
    public Integer depth;

    /** Score do link recebido. */
    @Column(name = "link_score")
    public Double linkScore;

    /** Justificativa do ranker para o score. */
    @Column(name = "link_justification")
    public String rankerJustification;

    /** Status de processamento da página. */
    public String status;

    /** Método de extração de conteúdo utilizado. */
    @Column(name = "extraction_method")
    public String extractionMethod;

    /** Tamanho do conteúdo extraído. */
    @Column(name = "content_length")
    public Integer contentLength;

    /** Número de chunks gerados. */
    @Column(name = "chunk_count")
    public Integer chunkCount;

    /** Motivo de falha, se houver. */
    @Column(name = "failure_reason")
    public String failureReason;

    /** Timestamp da visita. */
    @Column(name = "visited_at", nullable = false)
    public Instant visitedAt;
}
