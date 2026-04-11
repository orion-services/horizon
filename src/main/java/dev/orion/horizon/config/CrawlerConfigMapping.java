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

package dev.orion.horizon.config;

import dev.orion.horizon.CrawlerConfig;
import dev.orion.horizon.domain.model.CrawlParameters;
import dev.orion.horizon.domain.model.JobOptions;
import java.util.Arrays;
import java.util.List;

/**
 * Converte {@link CrawlerConfig} da aplicação em {@link CrawlParameters} de
 * domínio, incluindo merge com {@link JobOptions}.
 */
public final class CrawlerConfigMapping {

    private CrawlerConfigMapping() { }

    /**
     * Copia todos os valores da configuração injetável para um snapshot de
     * domínio.
     *
     * @param defaults origem dos valores padrão
     * @return snapshot imutável
     */
    public static CrawlParameters from(final CrawlerConfig defaults) {
        return new CrawlParameters(
                defaults.maxDepth(),
                defaults.maxSteps(),
                defaults.sessionTimeoutMs(),
                defaults.threadCount(),
                defaults.restrictToDomain(),
                defaults.minContentLength(),
                defaults.chunkSize(),
                defaults.chunkOverlap(),
                defaults.playwrightTimeoutMs(),
                defaults.jsoupEnrichTimeoutMs(),
                defaults.finalThreshold(),
                defaults.llmDelayMs(),
                toBackoffList(defaults.backoff429Seconds()),
                defaults.maxConsecutive429());
    }

    /**
     * Aplica sobrescritas opcionais; campos {@code null} em {@code overrides}
     * mantêm o valor vindo de {@code defaults}.
     *
     * @param defaults origem dos valores padrão
     * @param overrides sobrescritas opcionais (pode ser {@code null})
     * @return snapshot imutável após merge
     */
    public static CrawlParameters merge(
            final CrawlerConfig defaults,
            final JobOptions overrides) {
        final CrawlParameters base = from(defaults);
        if (overrides == null) {
            return base;
        }
        return new CrawlParameters(
                overrides.maxDepth() != null
                        ? overrides.maxDepth()
                        : base.maxDepth(),
                overrides.maxSteps() != null
                        ? overrides.maxSteps()
                        : base.maxSteps(),
                overrides.timeoutMs() != null
                        ? overrides.timeoutMs()
                        : base.sessionTimeoutMs(),
                base.threadCount(),
                base.restrictToDomain(),
                base.minContentLength(),
                base.chunkSize(),
                base.chunkOverlap(),
                base.playwrightTimeoutMs(),
                base.jsoupEnrichTimeoutMs(),
                base.finalThreshold(),
                base.llmDelayMs(),
                base.backoff429Seconds(),
                base.maxConsecutive429());
    }

    private static List<Integer> toBackoffList(final int[] raw) {
        if (raw == null || raw.length == 0) {
            return List.of();
        }
        return Arrays.stream(raw).boxed().toList();
    }
}
