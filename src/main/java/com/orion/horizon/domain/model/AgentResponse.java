package com.orion.horizon.domain.model;

/**
 * Resposta de um agente/LLM, incluindo a resposta bruta e os metadados de
 * execução (tokens, latência, status HTTP, etc).
 *
 * <p>O campo {@code parsedJson} deve conter o payload já parseado/normalizado
 * quando aplicável; em caso de erro de parse, {@code parseError} e/ou
 * {@code errorMessage} podem ser preenchidos.
 */
public final class AgentResponse {
    /** Conteúdo bruto retornado pelo provedor. */
    public final String rawContent;

    /** JSON parseado/normalizado (quando aplicável). */
    public final String parsedJson;

    /** Provedor utilizado para a chamada (ex.: "openai", "anthropic"). */
    public final String providerUsed;

    /** Modelo utilizado (ex.: "gpt-4.1"). */
    public final String modelUsed;

    /** Tokens de entrada consumidos. */
    public final Integer inputTokens;

    /** Tokens de saída consumidos. */
    public final Integer outputTokens;

    /** Latência total em milissegundos. */
    public final Long latencyMs;

    /** Status HTTP retornado pelo provedor (quando aplicável). */
    public final Integer httpStatus;

    /** Erro de parse (quando o parse falha). */
    public final String parseError;

    /** Mensagem de erro em caso de falha de chamada/execução. */
    public final String errorMessage;

    /** Indica se o conteúdo foi considerado relevante. */
    public final Boolean relevant;

    /** Confiança do agente sobre a relevância/completude (0..1). */
    public final Double confidence;

    /** Justificativa textual para a decisão de relevância. */
    public final String justification;

    private AgentResponse(final Builder builder) {
        this.rawContent = builder.rawContent;
        this.parsedJson = builder.parsedJson;
        this.providerUsed = builder.providerUsed;
        this.modelUsed = builder.modelUsed;
        this.inputTokens = builder.inputTokens;
        this.outputTokens = builder.outputTokens;
        this.latencyMs = builder.latencyMs;
        this.httpStatus = builder.httpStatus;
        this.parseError = builder.parseError;
        this.errorMessage = builder.errorMessage;
        this.relevant = builder.relevant;
        this.confidence = builder.confidence;
        this.justification = builder.justification;
    }

    /**
     * Cria um builder para {@link AgentResponse}.
     *
     * @return builder vazio
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder imutável por construção para {@link AgentResponse}.
     */
    public static final class Builder {
        private String rawContent;
        private String parsedJson;
        private String providerUsed;
        private String modelUsed;
        private Integer inputTokens;
        private Integer outputTokens;
        private Long latencyMs;
        private Integer httpStatus;
        private String parseError;
        private String errorMessage;
        private Boolean relevant;
        private Double confidence;
        private String justification;

        private Builder() {}

        /**
         * Define o conteúdo bruto.
         *
         * @param rawContent conteúdo bruto
         * @return este builder
         */
        public Builder rawContent(final String rawContent) {
            this.rawContent = rawContent;
            return this;
        }

        /**
         * Define o JSON parseado.
         *
         * @param parsedJson JSON parseado
         * @return este builder
         */
        public Builder parsedJson(final String parsedJson) {
            this.parsedJson = parsedJson;
            return this;
        }

        /**
         * Define o provedor utilizado.
         *
         * @param providerUsed provedor
         * @return este builder
         */
        public Builder providerUsed(final String providerUsed) {
            this.providerUsed = providerUsed;
            return this;
        }

        /**
         * Define o modelo utilizado.
         *
         * @param modelUsed modelo
         * @return este builder
         */
        public Builder modelUsed(final String modelUsed) {
            this.modelUsed = modelUsed;
            return this;
        }

        /**
         * Define tokens de entrada.
         *
         * @param inputTokens tokens de entrada
         * @return este builder
         */
        public Builder inputTokens(final Integer inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        /**
         * Define tokens de saída.
         *
         * @param outputTokens tokens de saída
         * @return este builder
         */
        public Builder outputTokens(final Integer outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        /**
         * Define latência em milissegundos.
         *
         * @param latencyMs latência
         * @return este builder
         */
        public Builder latencyMs(final Long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        /**
         * Define status HTTP.
         *
         * @param httpStatus status HTTP
         * @return este builder
         */
        public Builder httpStatus(final Integer httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        /**
         * Define erro de parse.
         *
         * @param parseError erro de parse
         * @return este builder
         */
        public Builder parseError(final String parseError) {
            this.parseError = parseError;
            return this;
        }

        /**
         * Define mensagem de erro.
         *
         * @param errorMessage mensagem de erro
         * @return este builder
         */
        public Builder errorMessage(final String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        /**
         * Define se a resposta é relevante.
         *
         * @param relevant relevante
         * @return este builder
         */
        public Builder relevant(final Boolean relevant) {
            this.relevant = relevant;
            return this;
        }

        /**
         * Define confiança.
         *
         * @param confidence confiança
         * @return este builder
         */
        public Builder confidence(final Double confidence) {
            this.confidence = confidence;
            return this;
        }

        /**
         * Define justificativa.
         *
         * @param justification justificativa
         * @return este builder
         */
        public Builder justification(final String justification) {
            this.justification = justification;
            return this;
        }

        /**
         * Constrói uma instância imutável.
         *
         * @return instância construída
         */
        public AgentResponse build() {
            return new AgentResponse(this);
        }
    }
}

