package com.orion.horizon.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Contexto imutável de navegação usado para decidir quais páginas visitar
 * durante um crawling.
 *
 * <p>Este record é serializável/deserializável via Jackson.
 *
 * @param userQuery consulta do utilizador que originou o job
 * @param currentUrl URL atual sendo processada
 * @param originUrl URL de origem (de onde o link foi descoberto)
 * @param currentDepth profundidade atual a partir da raiz
 * @param maxDepth profundidade máxima permitida
 * @param linkScore score do link que levou a esta página
 * @param linkJustification justificativa para o score do link
 * @param extractionMethod método de extração de conteúdo selecionado
 * @param pageTitle título da página (quando conhecido)
 */
public record NavigationContext(
        String userQuery,
        String currentUrl,
        String originUrl,
        int currentDepth,
        int maxDepth,
        Double linkScore,
        String linkJustification,
        ExtractionMethod extractionMethod,
        String pageTitle
) {
    /**
     * Construtor compatível com Jackson.
     */
    @JsonCreator
    public NavigationContext(
            @JsonProperty("userQuery") final String userQuery,
            @JsonProperty("currentUrl") final String currentUrl,
            @JsonProperty("originUrl") final String originUrl,
            @JsonProperty("currentDepth") final int currentDepth,
            @JsonProperty("maxDepth") final int maxDepth,
            @JsonProperty("linkScore") final Double linkScore,
            @JsonProperty("linkJustification") final String linkJustification,
            @JsonProperty("extractionMethod")
            final ExtractionMethod extractionMethod,
            @JsonProperty("pageTitle") final String pageTitle
    ) {
        this.userQuery = userQuery;
        this.currentUrl = currentUrl;
        this.originUrl = originUrl;
        this.currentDepth = currentDepth;
        this.maxDepth = maxDepth;
        this.linkScore = linkScore;
        this.linkJustification = linkJustification;
        this.extractionMethod = extractionMethod;
        this.pageTitle = pageTitle;
        validate();
    }

    private void validate() {
        if (currentDepth < 0) {
            throw new IllegalArgumentException("currentDepth deve ser >= 0");
        }
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth deve ser >= 0");
        }
        if (maxDepth < currentDepth) {
            throw new IllegalArgumentException(
                    "maxDepth deve ser >= currentDepth"
            );
        }
        if (userQuery != null && userQuery.isBlank()) {
            throw new IllegalArgumentException("userQuery não pode ser vazio");
        }
        if (currentUrl != null && currentUrl.isBlank()) {
            throw new IllegalArgumentException("currentUrl não pode ser vazio");
        }
        if (originUrl != null && originUrl.isBlank()) {
            throw new IllegalArgumentException("originUrl não pode ser vazio");
        }
        Objects.requireNonNull(
                extractionMethod,
                "extractionMethod é obrigatório"
        );
    }
}

