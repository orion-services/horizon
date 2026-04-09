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
import java.util.Map;

/**
 * Resultado extraído a partir de uma página visitada durante o crawl.
 *
 * <p>Representa o conteúdo consolidado que o sistema considera relevante para a
 * resposta final do job.
 */
public final class CrawlResult {
    /** URL de onde o resultado foi extraído. */
    public final String sourceUrl;

    /** Cadeia de origem que levou até {@link #sourceUrl}. */
    public final List<String> originChain;

    /** Profundidade em que o resultado foi encontrado. */
    public final int foundAtDepth;

    /** Score do link/página que originou este resultado. */
    public final double pageLinkScore;

    /** Conteúdo extraído (texto consolidado). */
    public final String extractedContent;

    /** Lista de fatos-chave extraídos. */
    public final List<String> keyFacts;

    /** Campos estruturados extraídos (ex.: JSON). */
    public final Map<String, Object> fields;

    /** Medida de completude do resultado (ex.: 0..1). */
    public final Double completeness;

    /** Aspectos faltantes identificados. */
    public final String missingAspects;

    /** Método de extração utilizado. */
    public final ExtractionMethod extractionMethod;

    /** Timestamp de quando o resultado foi encontrado. */
    public final Instant foundAt;

    /** Identificador lógico da thread/worker que produziu o resultado. */
    public final String threadId;

    /**
     * Cria um novo {@link CrawlResult}.
     *
     * @param sourceUrl URL de origem do resultado
     * @param originChain cadeia de URLs que levou ao resultado
     * @param foundAtDepth profundidade em que foi encontrado
     * @param pageLinkScore score associado ao link/página
     * @param extractedContent conteúdo extraído
     * @param keyFacts fatos-chave
     * @param fields campos estruturados
     * @param completeness completude
     * @param missingAspects aspectos faltantes
     * @param extractionMethod método de extração
     * @param foundAt instante em que foi encontrado
     * @param threadId thread/worker responsável
     */
    public CrawlResult(
            final String sourceUrl,
            final List<String> originChain,
            final int foundAtDepth,
            final double pageLinkScore,
            final String extractedContent,
            final List<String> keyFacts,
            final Map<String, Object> fields,
            final Double completeness,
            final String missingAspects,
            final ExtractionMethod extractionMethod,
            final Instant foundAt,
            final String threadId
    ) {
        this.sourceUrl = sourceUrl;
        this.originChain = originChain;
        this.foundAtDepth = foundAtDepth;
        this.pageLinkScore = pageLinkScore;
        this.extractedContent = extractedContent;
        this.keyFacts = keyFacts;
        this.fields = fields;
        this.completeness = completeness;
        this.missingAspects = missingAspects;
        this.extractionMethod = extractionMethod;
        this.foundAt = foundAt;
        this.threadId = threadId;
    }
}

