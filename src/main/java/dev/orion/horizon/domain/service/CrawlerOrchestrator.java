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

import dev.orion.horizon.adapter.out.browser.ContentExtractor;
import dev.orion.horizon.domain.model.CrawlJob;
import dev.orion.horizon.domain.model.CrawlParameters;
import dev.orion.horizon.domain.model.CrawlResult;
import dev.orion.horizon.domain.model.JobStatus;
import dev.orion.horizon.domain.model.PageNode;
import dev.orion.horizon.domain.model.PageStatus;
import dev.orion.horizon.domain.model.StopReason;
import dev.orion.horizon.domain.port.out.BrowserPort;
import dev.orion.horizon.domain.port.out.PersistencePort;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orquestrador do crawl — inicializa as estruturas globais, gerencia threads,
 * aplica timeout e aciona o AgentConsolidator ao final.
 *
 * <p>Cria {@link CrawlerThread} com o número de threads configurado e aguarda
 * o término de todas antes de consolidar os resultados.
 */
public final class CrawlerOrchestrator {

    private static final Logger LOG =
            Logger.getLogger(CrawlerOrchestrator.class.getName());

    /** Margem extra além do sessionTimeout para aguardar as threads. */
    private static final long EXTRA_WAIT_SECONDS = 30L;

    private final CrawlParameters parameters;
    private final BrowserPort browser;
    private final ContentExtractor contentExtractor;
    private final AgentVerifier agentVerifier;
    private final AgentExtractor agentExtractor;
    private final AgentRanker agentRanker;
    private final AgentConsolidator agentConsolidator;
    private final PersistencePort persistence;
    private final RateLimiter rateLimiter;

    /**
     * Cria um CrawlerOrchestrator.
     *
     * @param parameters parâmetros do crawl
     * @param browser adaptador de browser
     * @param contentExtractor extrator de conteúdo
     * @param agentVerifier agente verificador
     * @param agentExtractor agente extrator
     * @param agentRanker agente ranqueador
     * @param agentConsolidator agente consolidador
     * @param persistence porta de persistência
     * @param rateLimiter limitador de taxa compartilhado
     */
    public CrawlerOrchestrator(
            final CrawlParameters parameters,
            final BrowserPort browser,
            final ContentExtractor contentExtractor,
            final AgentVerifier agentVerifier,
            final AgentExtractor agentExtractor,
            final AgentRanker agentRanker,
            final AgentConsolidator agentConsolidator,
            final PersistencePort persistence,
            final RateLimiter rateLimiter) {
        this.parameters =
                Objects.requireNonNull(parameters, "parameters");
        this.browser =
                Objects.requireNonNull(browser, "browser");
        this.contentExtractor =
                Objects.requireNonNull(contentExtractor, "contentExtractor");
        this.agentVerifier =
                Objects.requireNonNull(agentVerifier, "agentVerifier");
        this.agentExtractor =
                Objects.requireNonNull(agentExtractor, "agentExtractor");
        this.agentRanker =
                Objects.requireNonNull(agentRanker, "agentRanker");
        this.agentConsolidator =
                Objects.requireNonNull(agentConsolidator, "agentConsolidator");
        this.persistence =
                Objects.requireNonNull(persistence, "persistence");
        this.rateLimiter =
                Objects.requireNonNull(rateLimiter, "rateLimiter");
    }

    /**
     * Executa o crawl completo para o job fornecido.
     *
     * @param job job de crawl a executar (deve estar em status RUNNING)
     * @return job atualizado com resultados, stopReason e métricas
     */
    public CrawlJob run(final CrawlJob job) {
        Objects.requireNonNull(job, "job");

        final AtomicBoolean aborted = new AtomicBoolean(false);
        final AtomicBoolean timedOut = new AtomicBoolean(false);
        final AtomicInteger pagesVisited = new AtomicInteger(0);
        final AtomicInteger activeWorkers = new AtomicInteger(0);
        final CopyOnWriteArrayList<CrawlResult> results =
                new CopyOnWriteArrayList<>();
        final VisitRegistry visitRegistry = new VisitRegistry();

        final Instant startedAt = Instant.now();

        final PageNode rootNode = new PageNode(
                job.rootUrl,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                PageStatus.PENDING,
                null,
                null,
                null
        );

        final BlockingQueue<PageNode> workQueue = new LinkedBlockingQueue<>();
        workQueue.add(rootNode);

        final ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(
                () -> timedOut.set(true),
                parameters.sessionTimeoutMs(),
                TimeUnit.MILLISECONDS
        );

        final TextChunker chunker = new TextChunker(
                parameters.chunkSize(), parameters.chunkOverlap()
        );

        final ExecutorService executor =
                Executors.newFixedThreadPool(parameters.threadCount());

        try {
            for (int i = 0; i < parameters.threadCount(); i++) {
                final String threadId = job.id + "-thread-" + i;
                final CrawlerThread thread = new CrawlerThread(
                        job.id,
                        threadId,
                        job.userQuery,
                        parameters,
                        browser,
                        contentExtractor,
                        chunker,
                        agentVerifier,
                        agentExtractor,
                        agentRanker,
                        visitRegistry,
                        persistence,
                        aborted,
                        timedOut,
                        pagesVisited,
                        results,
                        workQueue,
                        activeWorkers
                );
                executor.submit(thread);
            }

            executor.shutdown();
            final long totalWaitMs = parameters.sessionTimeoutMs()
                    + TimeUnit.SECONDS.toMillis(EXTRA_WAIT_SECONDS);
            final boolean terminated = executor.awaitTermination(
                    totalWaitMs, TimeUnit.MILLISECONDS
            );
            if (!terminated) {
                executor.shutdownNow();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            aborted.set(true);
            executor.shutdownNow();
        } finally {
            scheduler.shutdownNow();
        }

        final Instant finishedAt = Instant.now();
        final StopReason stopReason =
                determineStopReason(
                        aborted, timedOut,
                        pagesVisited.get(), results.size()
                );

        String finalAnswer = null;
        if (!results.isEmpty()) {
            try {
                finalAnswer = agentConsolidator.consolidate(
                        job.id,
                        job.userQuery,
                        job.rootUrl,
                        startedAt,
                        finishedAt,
                        stopReason,
                        pagesVisited.get(),
                        List.copyOf(results)
                );
            } catch (final Exception e) {
                LOG.log(Level.WARNING,
                        "AgentConsolidator failed: {0}", e.getMessage());
            }
        }

        final CrawlJob updatedJob = new CrawlJob(
                job.id,
                job.userQuery,
                job.rootUrl,
                job.parameters,
                JobStatus.COMPLETED,
                finalAnswer,
                stopReason,
                job.errors,
                job.createdAt,
                startedAt,
                finishedAt,
                pagesVisited.get(),
                results.size(),
                job.totalTokensConsumed
        );

        try {
            persistence.updateJob(updatedJob);
        } catch (final Exception e) {
            LOG.log(Level.WARNING,
                    "Failed to persist updated CrawlJob: {0}",
                    e.getMessage());
        }

        return updatedJob;
    }

    private StopReason determineStopReason(
            final AtomicBoolean aborted,
            final AtomicBoolean timedOut,
            final int pagesCount,
            final int resultsCount) {
        if (aborted.get()) {
            return StopReason.ABORTED;
        }
        if (timedOut.get()) {
            return StopReason.TIMEOUT;
        }
        if (pagesCount >= parameters.maxSteps()) {
            return StopReason.MAX_STEPS;
        }
        return StopReason.EXHAUSTED;
    }
}
