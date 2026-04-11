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
 * Entidade JPA para a tabela {@code crawl_results}.
 */
@Entity
@Table(name = "crawl_results")
public class CrawlResultEntity extends PanacheEntityBase {

    /** Identificador único do resultado. */
    @Id
    public UUID id;

    /** Referência ao job de crawl. */
    @Column(name = "job_id", nullable = false)
    public UUID jobId;

    /** Identificador da thread que produziu o resultado. */
    @Column(name = "thread_id")
    public String threadId;

    /** URL de origem do resultado. */
    @Column(name = "source_url", nullable = false, columnDefinition = "TEXT")
    public String sourceUrl;

    /** Cadeia de URLs que levou ao resultado. */
    @Column(name = "origin_chain")
    public String[] originChain;

    /** Profundidade em que o resultado foi encontrado. */
    @Column(name = "found_at_depth")
    public Integer foundAtDepth;

    /** Score do link/página de origem. */
    @Column(name = "page_link_score")
    public Double pageLinkScore;

    /** Conteúdo extraído. */
    @Column(name = "extracted_content", columnDefinition = "TEXT")
    public String extractedContent;

    /** Fatos-chave extraídos. */
    @Column(name = "key_facts")
    public String[] keyFacts;

    /** Completude do resultado. */
    public Double completeness;

    /** Aspectos faltantes. */
    @Column(name = "missing_aspects", columnDefinition = "TEXT")
    public String missingAspects;

    /** Método de extração utilizado. */
    @Column(name = "extraction_method", columnDefinition = "TEXT")
    public String extractionMethod;

    /** Timestamp em que o resultado foi encontrado. */
    @Column(name = "found_at", nullable = false)
    public Instant foundAt;
}
