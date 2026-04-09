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
import java.util.Objects;
import java.util.UUID;

/**
 * Registro imutável de uma chamada a um agente ou a um modelo LLM.
 *
 * <p>Destina-se à persistência (tabela {@code agent_calls}) e à auditoria ou ao
 * diagnóstico em tempo de execução.
 */
public final class AgentCallLog {
    /**
     * Identificador do job de crawl ao qual esta chamada está associada.
     */
    private final UUID jobId;
    /**
     * Identificador lógico da thread (conversação ou correlação no provedor).
     */
    private final String threadId;
    /**
     * Função do agente na pipeline (ex.: {@code ranker}, {@code extractor}).
     */
    private final String agentRole;
    /**
     * Nome do provedor da API (ex.: {@code openai}, {@code anthropic}).
     */
    private final String provider;
    /**
     * Identificador do modelo (ex.: {@code gpt-4.1}).
     */
    private final String model;
    /**
     * URL da página que forneceu o contexto desta chamada.
     */
    private final String pageUrl;
    /**
     * Índice do fragmento de texto na página (base zero); {@code null} se não
     * aplicável.
     */
    private final Integer chunkIndex;
    /**
     * Quantidade total de fragmentos da página; {@code null} se não aplicável.
     */
    private final Integer chunkTotal;
    /**
     * Conteúdo do prompt de sistema enviado ao modelo.
     */
    private final String systemPrompt;
    /**
     * Conteúdo do prompt de usuário enviado ao modelo.
     */
    private final String userPrompt;
    /**
     * Resposta textual bruta do provedor, antes de parsing estruturado.
     */
    private final String rawResponse;
    /**
     * Relevância obtida pelo parsing da resposta; {@code null} se ausente ou
     * não aplicável.
     */
    private final Boolean parsedRelevant;
    /**
     * Confiança associada ao resultado parseado; {@code null} se ausente ou não
     * aplicável.
     */
    private final Double confidence;
    /**
     * Tokens de entrada reportados pela API; {@code null} se desconhecido.
     */
    private final Integer inputTokens;
    /**
     * Tokens de saída reportados pela API; {@code null} se desconhecido.
     */
    private final Integer outputTokens;
    /**
     * Tempo entre o envio da requisição e a resposta completa, em
     * milissegundos.
     */
    private final Long latencyMs;
    /**
     * Código de status HTTP da resposta; {@code null} se não aplicável.
     */
    private final Integer httpStatus;
    /**
     * Detalhe do erro quando a chamada falha; {@code null} se não houve falha.
     */
    private final String errorMessage;
    /**
     * Instantâneo em que a chamada foi efetuada ou registrada.
     */
    private final Instant calledAt;

    /**
     * Cria um registro de chamada ao agente ou ao modelo.
     *
     * @param jobId identificador do job de crawl
     * @param threadId identificador lógico da thread
     * @param agentRole função do agente na pipeline
     * @param provider nome do provedor da API
     * @param model identificador do modelo
     * @param pageUrl URL da página de contexto
     * @param chunkIndex índice do fragmento (base zero), ou {@code null}
     * @param chunkTotal total de fragmentos, ou {@code null}
     * @param systemPrompt prompt de sistema enviado ao modelo
     * @param userPrompt prompt de usuário enviado ao modelo
     * @param rawResponse resposta bruta do provedor
     * @param parsedRelevant relevância parseada, ou {@code null}
     * @param confidence confiança parseada, ou {@code null}
     * @param inputTokens tokens de entrada, ou {@code null}
     * @param outputTokens tokens de saída, ou {@code null}
     * @param latencyMs latência em milissegundos
     * @param httpStatus código HTTP da resposta, ou {@code null}
     * @param errorMessage mensagem de erro, ou {@code null}
     * @param calledAt instante da chamada
     */
    public AgentCallLog(
            final UUID jobId,
            final String threadId,
            final String agentRole,
            final String provider,
            final String model,
            final String pageUrl,
            final Integer chunkIndex,
            final Integer chunkTotal,
            final String systemPrompt,
            final String userPrompt,
            final String rawResponse,
            final Boolean parsedRelevant,
            final Double confidence,
            final Integer inputTokens,
            final Integer outputTokens,
            final Long latencyMs,
            final Integer httpStatus,
            final String errorMessage,
            final Instant calledAt
    ) {
        this.jobId = jobId;
        this.threadId = threadId;
        this.agentRole = agentRole;
        this.provider = provider;
        this.model = model;
        this.pageUrl = pageUrl;
        this.chunkIndex = chunkIndex;
        this.chunkTotal = chunkTotal;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.rawResponse = rawResponse;
        this.parsedRelevant = parsedRelevant;
        this.confidence = confidence;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.httpStatus = httpStatus;
        this.errorMessage = errorMessage;
        this.calledAt = calledAt;
    }

    /**
     * @return o identificador do job de crawl associado a esta chamada
     */
    public UUID jobId() {
        return jobId;
    }

    /**
     * @return o identificador lógico da thread
     */
    public String threadId() {
        return threadId;
    }

    /**
     * @return a função do agente na pipeline
     */
    public String agentRole() {
        return agentRole;
    }

    /**
     * @return o nome do provedor da API
     */
    public String provider() {
        return provider;
    }

    /**
     * @return o identificador do modelo
     */
    public String model() {
        return model;
    }

    /**
     * @return a URL da página de contexto
     */
    public String pageUrl() {
        return pageUrl;
    }

    /**
     * @return o índice do fragmento de texto (base zero), ou {@code null}
     */
    public Integer chunkIndex() {
        return chunkIndex;
    }

    /**
     * @return o total de fragmentos da página, ou {@code null}
     */
    public Integer chunkTotal() {
        return chunkTotal;
    }

    /**
     * @return o prompt de sistema enviado ao modelo
     */
    public String systemPrompt() {
        return systemPrompt;
    }

    /**
     * @return o prompt de usuário enviado ao modelo
     */
    public String userPrompt() {
        return userPrompt;
    }

    /**
     * @return a resposta bruta do provedor
     */
    public String rawResponse() {
        return rawResponse;
    }

    /**
     * @return a relevância parseada, ou {@code null}
     */
    public Boolean parsedRelevant() {
        return parsedRelevant;
    }

    /**
     * @return a confiança parseada, ou {@code null}
     */
    public Double confidence() {
        return confidence;
    }

    /**
     * @return os tokens de entrada reportados pela API, ou {@code null}
     */
    public Integer inputTokens() {
        return inputTokens;
    }

    /**
     * @return os tokens de saída reportados pela API, ou {@code null}
     */
    public Integer outputTokens() {
        return outputTokens;
    }

    /**
     * @return a latência em milissegundos
     */
    public Long latencyMs() {
        return latencyMs;
    }

    /**
     * @return o código de status HTTP, ou {@code null}
     */
    public Integer httpStatus() {
        return httpStatus;
    }

    /**
     * @return a mensagem de erro, ou {@code null}
     */
    public String errorMessage() {
        return errorMessage;
    }

    /**
     * @return o instante em que a chamada foi efetuada ou registrada
     */
    public Instant calledAt() {
        return calledAt;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentCallLog that)) {
            return false;
        }
        return Objects.equals(jobId, that.jobId)
                && Objects.equals(threadId, that.threadId)
                && Objects.equals(agentRole, that.agentRole)
                && Objects.equals(provider, that.provider)
                && Objects.equals(model, that.model)
                && Objects.equals(pageUrl, that.pageUrl)
                && Objects.equals(chunkIndex, that.chunkIndex)
                && Objects.equals(chunkTotal, that.chunkTotal)
                && Objects.equals(systemPrompt, that.systemPrompt)
                && Objects.equals(userPrompt, that.userPrompt)
                && Objects.equals(rawResponse, that.rawResponse)
                && Objects.equals(parsedRelevant, that.parsedRelevant)
                && Objects.equals(confidence, that.confidence)
                && Objects.equals(inputTokens, that.inputTokens)
                && Objects.equals(outputTokens, that.outputTokens)
                && Objects.equals(latencyMs, that.latencyMs)
                && Objects.equals(httpStatus, that.httpStatus)
                && Objects.equals(errorMessage, that.errorMessage)
                && Objects.equals(calledAt, that.calledAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                jobId,
                threadId,
                agentRole,
                provider,
                model,
                pageUrl,
                chunkIndex,
                chunkTotal,
                systemPrompt,
                userPrompt,
                rawResponse,
                parsedRelevant,
                confidence,
                inputTokens,
                outputTokens,
                latencyMs,
                httpStatus,
                errorMessage,
                calledAt
        );
    }
}

