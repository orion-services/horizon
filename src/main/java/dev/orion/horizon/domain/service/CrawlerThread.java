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
import dev.orion.horizon.domain.model.CrawlParameters;
import dev.orion.horizon.domain.model.CrawlResult;
import dev.orion.horizon.domain.model.ExtractionMethod;
import dev.orion.horizon.domain.model.LinkCandidate;
import dev.orion.horizon.domain.model.NavigationContext;
import dev.orion.horizon.domain.model.PageNode;
import dev.orion.horizon.domain.model.PageStatus;
import dev.orion.horizon.domain.port.out.BrowserPort;
import dev.orion.horizon.domain.port.out.PersistencePort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread de crawl — implementa DFS recursivo com backtracking.
 *
 * <p>Cada instância opera sobre o grafo compartilhado representado por
 * {@link VisitRegistry} e {@link CopyOnWriteArrayList} de resultados.
 * Guards de parada são verificados antes de cada visita.
 */
public final class CrawlerThread implements Runnable {

    private static final Logger LOG =
            Logger.getLogger(CrawlerThread.class.getName());

    private final UUID jobId;
    private final String threadId;
    private final PageNode rootNode;
    private final CrawlParameters parameters;
    private final BrowserPort browser;
    private final ContentExtractor contentExtractor;
    private final TextChunker chunker;
    private final AgentVerifier agentVerifier;
    private final AgentExtractor agentExtractor;
    private final AgentRanker agentRanker;
    private final VisitRegistry visitRegistry;
    private final PersistencePort persistence;
    private final AtomicBoolean aborted;
    private final AtomicBoolean timedOut;
    private final AtomicInteger pagesVisited;
    private final CopyOnWriteArrayList<CrawlResult> results;

    /**
     * Cria uma instância de CrawlerThread.
     *
     * @param jobId identificador do job de crawl
     * @param threadId identificador lógico desta thread
     * @param rootNode nó inicial de navegação
     * @param parameters parâmetros do crawl
     * @param browser adaptador de browser
     * @param contentExtractor extrator de conteúdo
     * @param chunker divisor de texto em chunks
     * @param agentVerifier agente verificador de relevância
     * @param agentExtractor agente extrator de conteúdo
     * @param agentRanker agente ranqueador de links
     * @param visitRegistry registro compartilhado de URLs visitadas
     * @param persistence porta de persistência
     * @param aborted flag compartilhada de abortamento
     * @param timedOut flag compartilhada de timeout
     * @param pagesVisited contador compartilhado de páginas visitadas
     * @param results lista compartilhada de resultados
     */
    public CrawlerThread(
            final UUID jobId,
            final String threadId,
            final PageNode rootNode,
            final CrawlParameters parameters,
            final BrowserPort browser,
            final ContentExtractor contentExtractor,
            final TextChunker chunker,
            final AgentVerifier agentVerifier,
            final AgentExtractor agentExtractor,
            final AgentRanker agentRanker,
            final VisitRegistry visitRegistry,
            final PersistencePort persistence,
            final AtomicBoolean aborted,
            final AtomicBoolean timedOut,
            final AtomicInteger pagesVisited,
            final CopyOnWriteArrayList<CrawlResult> results) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.threadId =
                Objects.requireNonNull(threadId, "threadId");
        this.rootNode =
                Objects.requireNonNull(rootNode, "rootNode");
        this.parameters =
                Objects.requireNonNull(parameters, "parameters");
        this.browser =
                Objects.requireNonNull(browser, "browser");
        this.contentExtractor =
                Objects.requireNonNull(contentExtractor, "contentExtractor");
        this.chunker =
                Objects.requireNonNull(chunker, "chunker");
        this.agentVerifier =
                Objects.requireNonNull(agentVerifier, "agentVerifier");
        this.agentExtractor =
                Objects.requireNonNull(agentExtractor, "agentExtractor");
        this.agentRanker =
                Objects.requireNonNull(agentRanker, "agentRanker");
        this.visitRegistry =
                Objects.requireNonNull(visitRegistry, "visitRegistry");
        this.persistence =
                Objects.requireNonNull(persistence, "persistence");
        this.aborted =
                Objects.requireNonNull(aborted, "aborted");
        this.timedOut =
                Objects.requireNonNull(timedOut, "timedOut");
        this.pagesVisited =
                Objects.requireNonNull(pagesVisited, "pagesVisited");
        this.results =
                Objects.requireNonNull(results, "results");
    }

    @Override
    public void run() {
        crawl(rootNode);
    }

    /**
     * Executa DFS recursivo a partir do nó fornecido.
     *
     * @param node nó a visitar
     */
    public void crawl(final PageNode node) {
        if (shouldStop() || node.depth > parameters.maxDepth()) {
            return;
        }

        if (visitRegistry.markVisited(node.url, node) != null) {
            return;
        }

        final int visited = pagesVisited.incrementAndGet();
        if (visited > parameters.maxSteps()) {
            return;
        }

        final PageNode processedNode = processPage(node);
        if (processedNode == null) {
            return;
        }

        final PriorityQueue<LinkCandidate> queue =
                processedNode.candidateQueue;
        while (!queue.isEmpty() && !shouldStop()) {
            final LinkCandidate candidate = queue.poll();
            final PageNode childNode = new PageNode(
                    candidate.url(),
                    node.url,
                    node.depth + 1,
                    candidate.finalScore(),
                    candidate.finalJustification(),
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
            crawl(childNode);
        }
    }

    /**
     * Indica se o crawl deve parar.
     *
     * @return {@code true} se abortado, com timeout ou
     *         acima do limite de steps
     */
    public boolean shouldStop() {
        return aborted.get()
                || timedOut.get()
                || pagesVisited.get() > parameters.maxSteps();
    }

    private PageNode processPage(final PageNode node) {
        final java.util.Optional<String> htmlOpt =
                browser.loadPage(
                        node.url, parameters.playwrightTimeoutMs()
                );

        if (htmlOpt.isEmpty()) {
            final PageNode discarded = new PageNode(
                    node.url, node.originUrl, node.depth,
                    node.scoreReceived, node.rankerJustification,
                    null, null, null, null, null,
                    PageStatus.DISCARDED, Instant.now(),
                    "browser failed", null
            );
            persistPageVisit(discarded);
            return null;
        }

        final String html = htmlOpt.get();
        final ContentExtractor.ExtractionResult extraction =
                contentExtractor.extract(html, null, node.url);

        final List<String> chunks =
                chunker.chunk(extraction.text());
        if (chunks.isEmpty()) {
            final PageNode discarded = new PageNode(
                    node.url, node.originUrl, node.depth,
                    node.scoreReceived, node.rankerJustification,
                    extraction.text(), extraction.method(),
                    chunks, null, null,
                    PageStatus.DISCARDED, Instant.now(),
                    "no chunks", null
            );
            persistPageVisit(discarded);
            return null;
        }

        final NavigationContext context = new NavigationContext(
                buildQuery(node),
                node.url,
                node.originUrl,
                node.depth,
                parameters.maxDepth(),
                node.scoreReceived,
                node.rankerJustification,
                extraction.method() != null
                        ? extraction.method()
                        : ExtractionMethod.RAW,
                null
        );

        final List<String> positiveChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            if (shouldStop()) {
                break;
            }
            final boolean relevant = agentVerifier.verify(
                    jobId, threadId, context,
                    chunks.get(i), i, chunks.size()
            );
            if (relevant) {
                positiveChunks.add(chunks.get(i));
            }
        }

        CrawlResult partialResult = null;
        if (!positiveChunks.isEmpty()) {
            final List<String> originChain = buildOriginChain(node);
            final double linkScore =
                    node.scoreReceived != null ? node.scoreReceived : 0.0;
            partialResult = agentExtractor.extract(
                    jobId, threadId, context,
                    positiveChunks, originChain, linkScore
            );
            results.add(partialResult);
            persistence.saveCrawlResult(jobId, partialResult);
        }

        final List<String> allLinks = extractLinks(html, node.url);
        final List<LinkCandidate> initialCandidates =
                buildInitialCandidates(allLinks);

        final PriorityQueue<LinkCandidate> rankedQueue;
        if (!initialCandidates.isEmpty() && !shouldStop()) {
            rankedQueue = agentRanker.rank(
                    jobId, threadId,
                    buildQuery(node),
                    node.url,
                    initialCandidates
            );
        } else {
            rankedQueue = new PriorityQueue<>();
        }

        final PageNode processed = new PageNode(
                node.url, node.originUrl, node.depth,
                node.scoreReceived, node.rankerJustification,
                extraction.text(), extraction.method(),
                chunks, allLinks, rankedQueue,
                PageStatus.DONE, Instant.now(),
                null, partialResult
        );
        persistPageVisit(processed);
        return processed;
    }

    private String buildQuery(final PageNode node) {
        return node.originUrl != null ? node.originUrl : node.url;
    }

    private List<String> buildOriginChain(final PageNode node) {
        final List<String> chain = new ArrayList<>();
        if (node.originUrl != null) {
            chain.add(node.originUrl);
        }
        chain.add(node.url);
        return chain;
    }

    private List<LinkCandidate> buildInitialCandidates(
            final List<String> links) {
        final List<LinkCandidate> candidates = new ArrayList<>();
        for (final String link : links) {
            if (!visitRegistry.isVisited(link)) {
                candidates.add(new LinkCandidate(
                        link, null, null, null,
                        null, null, null, null, false, null, null
                ));
            }
        }
        return candidates;
    }

    private List<String> extractLinks(
            final String html, final String baseUrl) {
        final List<String> links = new ArrayList<>();
        try {
            final org.jsoup.nodes.Document doc =
                    org.jsoup.Jsoup.parse(html, baseUrl);
            for (final org.jsoup.nodes.Element anchor
                    : doc.select("a[href]")) {
                final String abs = anchor.absUrl("href");
                if (!abs.isBlank()
                        && (abs.startsWith("http://")
                            || abs.startsWith("https://"))) {
                    if (!parameters.restrictToDomain()
                            || sameDomain(abs, baseUrl)) {
                        links.add(abs);
                    }
                }
            }
        } catch (final Exception e) {
            LOG.log(Level.FINE,
                    "Link extraction error for {0}: {1}",
                    new Object[]{baseUrl, e.getMessage()});
        }
        return links;
    }

    private static boolean sameDomain(
            final String url, final String baseUrl) {
        try {
            final String host1 =
                    new java.net.URI(url).getHost();
            final String host2 =
                    new java.net.URI(baseUrl).getHost();
            return host1 != null && host1.equals(host2);
        } catch (final Exception e) {
            return false;
        }
    }

    private void persistPageVisit(final PageNode node) {
        try {
            persistence.savePageVisit(jobId, node);
        } catch (final Exception e) {
            LOG.log(Level.WARNING,
                    "Failed to persist PageNode for {0}: {1}",
                    new Object[]{node.url, e.getMessage()});
        }
    }
}
