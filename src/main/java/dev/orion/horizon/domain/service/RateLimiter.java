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

package dev.orion.horizon.domain.service;

import dev.orion.horizon.domain.model.CrawlParameters;
import java.util.List;
import java.util.Objects;

/**
 * Limitador centralizado de taxa para chamadas LLM: intervalo mínimo entre
 * chamadas e backoff após HTTP 429.
 *
 * <p>Todos os métodos públicos são {@code synchronized} no mesmo monitor,
 * garantindo ausência de condições de corrida entre threads.
 */
public final class RateLimiter {

    private final int llmDelayMs;
    private final List<Integer> backoff429Seconds;
    private final int maxConsecutive429;
    private final MillisSleeper millisSleeper;

    private long lastAcquireEndNanos;
    private int consecutive429;
    private boolean aborted;

    /**
     * Cria um limiter a partir de um snapshot de parâmetros de crawl.
     *
     * @param parameters parâmetros (delay LLM, backoff 429, limite de 429)
     */
    public RateLimiter(final CrawlParameters parameters) {
        this(parameters, MillisSleeper.threadDefault());
    }

    /**
     * Cria um limiter com sleeper injetável (ex.: testes sem dormir de fato).
     *
     * @param parameters parâmetros (delay LLM, backoff 429, limite de 429)
     * @param millisSleeper implementação de pausa para backoff 429
     */
    public RateLimiter(
            final CrawlParameters parameters,
            final MillisSleeper millisSleeper) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(millisSleeper, "millisSleeper");
        this.llmDelayMs = parameters.llmDelayMs();
        this.backoff429Seconds = parameters.backoff429Seconds();
        this.maxConsecutive429 = parameters.maxConsecutive429();
        this.millisSleeper = millisSleeper;
    }

    /**
     * Construtor de teste com valores explícitos (backoff em segundos por
     * tentativa).
     *
     * @param llmDelayMs intervalo mínimo entre chamadas (ms)
     * @param backoff429Seconds pausas após cada 429, por ordem de ocorrência
     * @param maxConsecutive429 após quantos 429 consecutivos abortar
     * @param millisSleeper implementação de pausa para backoff 429
     */
    RateLimiter(
            final int llmDelayMs,
            final List<Integer> backoff429Seconds,
            final int maxConsecutive429,
            final MillisSleeper millisSleeper) {
        Objects.requireNonNull(millisSleeper, "millisSleeper");
        this.llmDelayMs = llmDelayMs;
        this.backoff429Seconds =
                List.copyOf(
                        backoff429Seconds == null
                                ? List.of()
                                : backoff429Seconds);
        this.maxConsecutive429 = maxConsecutive429;
        this.millisSleeper = millisSleeper;
    }

    /**
     * Bloqueia até que o intervalo mínimo desde a última aquisição tenha
     * decorrido.
     *
     * @throws InterruptedException se a espera for interrompida
     */
    public synchronized void acquire() throws InterruptedException {
        final long now = System.nanoTime();
        if (lastAcquireEndNanos != 0) {
            final long minIntervalNs = (long) llmDelayMs * 1_000_000L;
            final long elapsed = now - lastAcquireEndNanos;
            final long waitNs = minIntervalNs - elapsed;
            if (waitNs > 0) {
                final long waitMs = (waitNs + 999_999L) / 1_000_000L;
                millisSleeper.sleep(waitMs);
            }
        }
        lastAcquireEndNanos = System.nanoTime();
    }

    /**
     * Registra um HTTP 429: incrementa o contador, aplica pausa configurada ou
     * aborta se o limite for excedido.
     *
     * @throws InterruptedException se a espera for interrompida
     */
    public synchronized void on429() throws InterruptedException {
        consecutive429++;
        if (consecutive429 > maxConsecutive429) {
            aborted = true;
            return;
        }
        final long sleepMs = backoffMillisForAttempt(consecutive429 - 1);
        if (sleepMs > 0) {
            millisSleeper.sleep(sleepMs);
        }
    }

    /**
     * Registra uma resposta bem-sucedida (não 429), zerando o contador de 429
     * consecutivos.
     */
    public synchronized void onSuccess() {
        consecutive429 = 0;
    }

    /**
     * Indica se o fluxo deve ser interrompido por excesso de 429 consecutivos.
     *
     * @return {@code true} após exceder o limite configurado de 429 seguidos
     */
    public synchronized boolean isAborted() {
        return aborted;
    }

    private long backoffMillisForAttempt(final int zeroBasedIndex) {
        if (backoff429Seconds.isEmpty() || zeroBasedIndex < 0) {
            return 0L;
        }
        final int idx =
                Math.min(zeroBasedIndex, backoff429Seconds.size() - 1);
        return backoff429Seconds.get(idx).longValue() * 1000L;
    }

}
