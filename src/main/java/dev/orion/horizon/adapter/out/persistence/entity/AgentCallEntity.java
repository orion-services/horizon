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
 * Entidade JPA para a tabela {@code agent_calls}.
 */
@Entity
@Table(name = "agent_calls")
public class AgentCallEntity extends PanacheEntityBase {

    /** Identificador único da chamada. */
    @Id
    public UUID id;

    /** Referência ao job de crawl. */
    @Column(name = "job_id", nullable = false)
    public UUID jobId;

    /** Identificador lógico da thread. */
    @Column(name = "thread_id")
    public String threadId;

    /** Papel do agente (VERIFIER, EXTRACTOR, RANKER, CONSOLIDATOR). */
    @Column(name = "agent_role")
    public String agentRole;

    /** Nome do provedor (ANTHROPIC, OLLAMA, OPENAI). */
    public String provider;

    /** Identificador do modelo. */
    public String model;

    /** URL da página de contexto. */
    @Column(name = "page_url")
    public String pageUrl;

    /** Índice do chunk (-1 se não aplicável). */
    @Column(name = "chunk_index")
    public Integer chunkIndex;

    /** Total de chunks da página. */
    @Column(name = "chunk_total")
    public Integer chunkTotal;

    /** Prompt de sistema enviado. */
    @Column(name = "system_prompt", length = 65535)
    public String systemPrompt;

    /** Prompt de usuário enviado. */
    @Column(name = "user_prompt", length = 65535)
    public String userPrompt;

    /** Resposta bruta do provedor. */
    @Column(name = "raw_response", length = 65535)
    public String rawResponse;

    /** Relevância parseada (como texto boolean). */
    @Column(name = "parsed_relevant")
    public String parsedRelevant;

    /** Confiança parseada. */
    public Double confidence;

    /** Tokens de entrada. */
    @Column(name = "input_tokens")
    public Integer inputTokens;

    /** Tokens de saída. */
    @Column(name = "output_tokens")
    public Integer outputTokens;

    /** Latência em milissegundos. */
    @Column(name = "latency_ms")
    public Long latencyMs;

    /** Status HTTP da resposta. */
    @Column(name = "http_status")
    public Integer httpStatus;

    /**
     * Mensagem de erro, se houver (Hibernate mapeia para VARCHAR(255) sem
     * length explícito).
     */
    @Column(name = "error_message", length = 255)
    public String errorMessage;

    /** Timestamp da chamada. */
    @Column(name = "called_at", nullable = false)
    public Instant calledAt;
}
