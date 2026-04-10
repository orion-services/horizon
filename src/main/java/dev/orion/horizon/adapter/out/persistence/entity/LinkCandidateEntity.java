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
 * Entidade JPA para a tabela {@code link_candidates}.
 */
@Entity
@Table(name = "link_candidates")
public class LinkCandidateEntity extends PanacheEntityBase {

    /** Identificador único do candidato. */
    @Id
    public UUID id;

    /** Referência ao job de crawl. */
    @Column(name = "job_id", nullable = false)
    public UUID jobId;

    /** URL da página de origem do link. */
    @Column(name = "source_page_url")
    public String sourcePageUrl;

    /** URL do candidato. */
    @Column(nullable = false)
    public String url;

    /** Texto âncora do link. */
    @Column(name = "anchor_text")
    public String anchorText;

    /** Contexto do elemento no DOM. */
    @Column(name = "dom_context")
    public String domContext;

    /** Atributo aria-label. */
    @Column(name = "aria_label")
    public String ariaLabel;

    /** Título da página do link. */
    @Column(name = "page_title")
    public String pageTitle;

    /** Meta description da página do link. */
    @Column(name = "meta_description")
    public String metaDescription;

    /** Indica se o enriquecimento falhou. */
    @Column(name = "enrichment_failed", nullable = false)
    public boolean enrichmentFailed;

    /** Score da fase 1. */
    @Column(name = "phase1_score")
    public Double phase1Score;

    /** Justificativa da fase 1. */
    @Column(name = "phase1_justification")
    public String phase1Justification;

    /** Score final (fase 2). */
    @Column(name = "final_score")
    public Double finalScore;

    /** Justificativa final. */
    @Column(name = "final_justification")
    public String finalJustification;

    /** Se o link foi aprovado para visita. */
    public Boolean approved;

    /** Timestamp de avaliação. */
    @Column(name = "evaluated_at")
    public Instant evaluatedAt;
}
