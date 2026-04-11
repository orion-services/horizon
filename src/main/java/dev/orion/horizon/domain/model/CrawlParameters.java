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

import java.util.List;

/**
 * Snapshot imutável dos parâmetros efetivos de uma execução de crawl.
 *
 * <p>Espelha semanticamente a configuração da aplicação, sem depender de
 * injeção ou mapeamento de framework.
 *
 * @param maxDepth profundidade máxima na árvore de links
 * @param maxSteps limite de páginas visitadas
 * @param sessionTimeoutMs tempo máximo de vida da sessão (ms)
 * @param threadCount paralelismo do crawler
 * @param restrictToDomain se a navegação fica restrita ao domínio
 * @param minContentLength tamanho mínimo do texto extraído (caracteres)
 * @param chunkSize tamanho de cada fragmento ao fatiar conteúdo
 * @param chunkOverlap sobreposição entre fragmentos consecutivos
 * @param playwrightTimeoutMs timeout Playwright (ms)
 * @param jsoupEnrichTimeoutMs timeout Jsoup (ms)
 * @param finalThreshold limiar de score para aceitar um link (0–1)
 * @param llmDelayMs intervalo mínimo entre chamadas ao LLM (ms)
 * @param backoff429Seconds pausas após HTTP 429 (segundos por tentativa)
 * @param maxConsecutive429 tolerância a 429 consecutivos
 */
public record CrawlParameters(
        int maxDepth,
        int maxSteps,
        long sessionTimeoutMs,
        int threadCount,
        boolean restrictToDomain,
        int minContentLength,
        int chunkSize,
        int chunkOverlap,
        long playwrightTimeoutMs,
        long jsoupEnrichTimeoutMs,
        double finalThreshold,
        int llmDelayMs,
        List<Integer> backoff429Seconds,
        int maxConsecutive429
) {
    public CrawlParameters {
        backoff429Seconds =
                List.copyOf(
                        backoff429Seconds == null
                                ? List.of()
                                : backoff429Seconds);
    }
}
