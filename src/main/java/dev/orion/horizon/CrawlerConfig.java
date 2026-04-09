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

package dev.orion.horizon;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * Parâmetros do crawler injetados a partir do prefixo {@code horizon.crawler}
 * em {@code application.properties}.
 */
@ConfigMapping(prefix = "horizon.crawler")
public interface CrawlerConfig {
    /**
     * Profundidade máxima na árvore de links a partir da URL inicial.
     *
     * @return número de níveis de navegação permitidos
     */
    int maxDepth();

    /**
     * Limite de páginas visitadas numa execução do crawl.
     *
     * @return teto de páginas antes de encerrar
     */
    int maxSteps();

    /**
     * Tempo máximo de vida de uma sessão de crawl antes de timeout.
     *
     * @return duração em milissegundos
     */
    long sessionTimeoutMs();

    /**
     * Número de threads usadas para trabalho paralelo do crawler.
     *
     * @return paralelismo configurado
     */
    int threadCount();

    /**
     * Se {@code true}, só segue links do mesmo domínio da origem.
     *
     * @return se a navegação fica restrita ao domínio
     */
    boolean restrictToDomain();

    /**
     * Tamanho mínimo do texto extraído para considerar a página utilizável.
     *
     * @return limite em caracteres
     */
    int minContentLength();

    /**
     * Tamanho de cada fragmento ao fatiar o conteúdo para processamento.
     *
     * @return tamanho do chunk em caracteres
     */
    int chunkSize();

    /**
     * Sobreposição entre fragmentos consecutivos ao fatiar o texto.
     *
     * @return overlap em caracteres
     */
    int chunkOverlap();

    /**
     * Timeout para operações do Playwright (carga da página, esperas).
     *
     * @return duração em milissegundos
     */
    long playwrightTimeoutMs();

    /**
     * Timeout para enriquecimento ou parsing com Jsoup.
     *
     * @return duração em milissegundos
     */
    long jsoupEnrichTimeoutMs();

    /**
     * Limiar de score na etapa prévia (filtro antes de chamadas mais caras).
     *
     * @return valor entre 0 e 1
     */
    double preThreshold();

    /**
     * Limiar de score na etapa final de relevância ou aceitação do conteúdo.
     *
     * @return valor entre 0 e 1
     */
    double finalThreshold();

    /**
     * Intervalo mínimo entre chamadas ao LLM (rate limiting).
     *
     * @return atraso em milissegundos
     */
    int llmDelayMs();

    /**
     * Segundos de espera após cada resposta HTTP 429 (backoff progressivo).
     *
     * @return array de pausas, por tentativa
     */
    @WithName("backoff-429-seconds")
    int[] backoff429Seconds();

    /**
     * Quantas respostas 429 consecutivas tolerar antes de parar ou desistir.
     *
     * @return limite de tentativas com 429
     */
    @WithName("max-consecutive-429")
    int maxConsecutive429();
}
