package com.orion.horizon.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Log de uma chamada a um agente/LLM.
 *
 * <p>Este objeto é pensado para persistência (tabela {@code agent_calls}) e
 * para auditoria/diagnóstico em runtime.
 */
public final class AgentCallLog {
    private final UUID jobId;
    private final String threadId;
    private final String agentRole;
    private final String provider;
    private final String model;
    private final String pageUrl;
    private final Integer chunkIndex;
    private final Integer chunkTotal;
    private final String systemPrompt;
    private final String userPrompt;
    private final String rawResponse;
    private final Boolean parsedRelevant;
    private final Double confidence;
    private final Integer inputTokens;
    private final Integer outputTokens;
    private final Long latencyMs;
    private final Integer httpStatus;
    private final String errorMessage;
    private final Instant calledAt;

    /**
     * Cria um log de chamada do agente.
     *
     * @param jobId id do job ao qual a chamada pertence
     * @param threadId id lógico da thread
     * @param agentRole papel do agente (ex.: ranker, extractor)
     * @param provider provedor (ex.: openai, anthropic)
     * @param model modelo (ex.: gpt-4.1)
     * @param pageUrl url associada ao contexto da chamada
     * @param chunkIndex índice do chunk (0-based) quando aplicável
     * @param chunkTotal total de chunks quando aplicável
     * @param systemPrompt prompt de sistema utilizado
     * @param userPrompt prompt de usuário utilizado
     * @param rawResponse resposta bruta retornada pelo provedor
     * @param parsedRelevant valor parseado de relevância quando aplicável
     * @param confidence confiança parseada quando aplicável
     * @param inputTokens tokens de entrada consumidos
     * @param outputTokens tokens de saída gerados
     * @param latencyMs latência em milissegundos
     * @param httpStatus status HTTP observado quando aplicável
     * @param errorMessage mensagem de erro quando aplicável
     * @param calledAt instante em que a chamada ocorreu
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

    /** @return id do job. */
    public UUID jobId() {
        return jobId;
    }

    /** @return id lógico da thread. */
    public String threadId() {
        return threadId;
    }

    /** @return papel do agente. */
    public String agentRole() {
        return agentRole;
    }

    /** @return provedor. */
    public String provider() {
        return provider;
    }

    /** @return modelo. */
    public String model() {
        return model;
    }

    /** @return url de página associada. */
    public String pageUrl() {
        return pageUrl;
    }

    /** @return índice do chunk. */
    public Integer chunkIndex() {
        return chunkIndex;
    }

    /** @return total de chunks. */
    public Integer chunkTotal() {
        return chunkTotal;
    }

    /** @return prompt de sistema. */
    public String systemPrompt() {
        return systemPrompt;
    }

    /** @return prompt de usuário. */
    public String userPrompt() {
        return userPrompt;
    }

    /** @return resposta bruta. */
    public String rawResponse() {
        return rawResponse;
    }

    /** @return relevância parseada quando aplicável. */
    public Boolean parsedRelevant() {
        return parsedRelevant;
    }

    /** @return confiança parseada quando aplicável. */
    public Double confidence() {
        return confidence;
    }

    /** @return tokens de entrada. */
    public Integer inputTokens() {
        return inputTokens;
    }

    /** @return tokens de saída. */
    public Integer outputTokens() {
        return outputTokens;
    }

    /** @return latência em ms. */
    public Long latencyMs() {
        return latencyMs;
    }

    /** @return status HTTP quando aplicável. */
    public Integer httpStatus() {
        return httpStatus;
    }

    /** @return mensagem de erro quando aplicável. */
    public String errorMessage() {
        return errorMessage;
    }

    /** @return instante da chamada. */
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

