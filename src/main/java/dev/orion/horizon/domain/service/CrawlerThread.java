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
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread de crawl — consumer de uma fila compartilhada de {@link PageNode}.
 *
 * <p>Cada instância compete com as demais threads pelo mesmo
 * {@link BlockingQueue}: retira um nó, processa a página e recoloca os filhos
 * aprovados pelo ranker. A terminação ocorre quando a fila está vazia
 * <em>e</em> nenhum worker está ativo ({@code activeWorkers == 0}).
 */
public final class CrawlerThread implements Runnable {

    private static final Logger LOG =
            Logger.getLogger(CrawlerThread.class.getName());

    private static final int DOM_CONTEXT_MAX_LENGTH = 200;

    /** Tempo máximo de espera por um nó na fila antes de checar terminação. */
    private static final long POLL_TIMEOUT_MS = 500L;

    private final UUID jobId;
    private final String threadId;
    private final String userQuery;
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
    private final BlockingQueue<PageNode> workQueue;
    private final AtomicInteger activeWorkers;

    /**
     * Cria uma instância de CrawlerThread.
     *
     * @param jobId identificador do job de crawl
     * @param threadId identificador lógico desta thread
     * @param userQuery consulta em linguagem natural do usuário
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
     * @param workQueue fila compartilhada de nós a processar
     * @param activeWorkers contador de workers ativos (usado para detectar
     *        término)
     */
    public CrawlerThread(
            final UUID jobId,
            final String threadId,
            final String userQuery,
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
            final CopyOnWriteArrayList<CrawlResult> results,
            final BlockingQueue<PageNode> workQueue,
            final AtomicInteger activeWorkers) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.threadId =
                Objects.requireNonNull(threadId, "threadId");
        this.userQuery =
                Objects.requireNonNull(userQuery, "userQuery");
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
        this.workQueue =
                Objects.requireNonNull(workQueue, "workQueue");
        this.activeWorkers =
                Objects.requireNonNull(activeWorkers, "activeWorkers");
    }

    /**
     * Loop principal: retira nós da fila, processa e recoloca filhos.
     *
     * <p>Termina quando a fila está vazia e nenhum worker está ativo, ou
     * quando {@link #shouldStop()} retorna {@code true}.
     */
    @Override
    public void run() {
        while (!shouldStop()) {
            final PageNode node;
            try {
                node = workQueue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (node == null) {
                if (activeWorkers.get() == 0) {
                    break;
                }
                continue;
            }

            if (visitRegistry.markVisited(node.url, node) != null) {
                continue;
            }

            final int visited = pagesVisited.incrementAndGet();
            if (visited > parameters.maxSteps()) {
                break;
            }

            activeWorkers.incrementAndGet();
            try {
                final PageNode processed = processPage(node);
                if (processed != null) {
                    enqueueChildren(processed);
                }
            } finally {
                activeWorkers.decrementAndGet();
            }
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

    private void enqueueChildren(final PageNode processed) {
        final PriorityQueue<LinkCandidate> queue = processed.candidateQueue;
        if (queue == null) {
            return;
        }
        final int nextDepth = processed.depth + 1;
        if (nextDepth > parameters.maxDepth()) {
            return;
        }
        while (!queue.isEmpty()) {
            final LinkCandidate candidate = queue.poll();
            if (visitRegistry.isVisited(candidate.url())) {
                continue;
            }
            final PageNode child = new PageNode(
                    candidate.url(),
                    processed.url,
                    nextDepth,
                    candidate.finalScore(),
                    candidate.finalJustification(),
                    null, null, null, null, null,
                    PageStatus.PENDING,
                    null, null, null
            );
            workQueue.offer(child);
        }
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
                userQuery,
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

        final List<LinkCandidate> initialCandidates =
                extractLinkCandidates(html, node.url);

        final PriorityQueue<LinkCandidate> rankedQueue;
        if (!initialCandidates.isEmpty() && !shouldStop()) {
            rankedQueue = agentRanker.rank(
                    jobId, threadId,
                    userQuery,
                    node.url,
                    initialCandidates
            );
        } else {
            rankedQueue = new PriorityQueue<>();
        }

        final List<String> allLinkUrls = initialCandidates.stream()
                .map(LinkCandidate::url)
                .toList();

        final PageNode processed = new PageNode(
                node.url, node.originUrl, node.depth,
                node.scoreReceived, node.rankerJustification,
                extraction.text(), extraction.method(),
                chunks, allLinkUrls, rankedQueue,
                PageStatus.DONE, Instant.now(),
                null, partialResult
        );
        persistPageVisit(processed);
        return processed;
    }

    private List<String> buildOriginChain(final PageNode node) {
        final List<String> chain = new ArrayList<>();
        if (node.originUrl != null) {
            chain.add(node.originUrl);
        }
        chain.add(node.url);
        return chain;
    }

    /**
     * Extrai links do HTML com metadados ricos (âncora, aria-label,
     * contexto DOM) para alimentar o ranker com informação semântica.
     */
    private List<LinkCandidate> extractLinkCandidates(
            final String html, final String baseUrl) {
        final List<LinkCandidate> candidates = new ArrayList<>();
        try {
            final org.jsoup.nodes.Document doc =
                    org.jsoup.Jsoup.parse(html, baseUrl);
            for (final org.jsoup.nodes.Element anchor
                    : doc.select("a[href]")) {
                final String abs = anchor.absUrl("href");
                if (abs.isBlank()
                        || (!abs.startsWith("http://")
                            && !abs.startsWith("https://"))) {
                    continue;
                }
                if (isSameDocument(abs, baseUrl)) {
                    continue;
                }
                if (parameters.restrictToDomain()
                        && !sameDomain(abs, baseUrl)) {
                    continue;
                }
                if (visitRegistry.isVisited(abs)) {
                    continue;
                }

                final String anchorText =
                        normalizeText(anchor.text());
                final String ariaLabel =
                        normalizeText(anchor.attr("aria-label"));
                final String domContext =
                        extractDomContext(anchor);

                candidates.add(new LinkCandidate(
                        abs,
                        anchorText,
                        domContext,
                        ariaLabel,
                        null, null,
                        null, null,
                        false,
                        null, null
                ));
            }
        } catch (final Exception e) {
            LOG.log(Level.FINE,
                    "Link extraction error for {0}: {1}",
                    new Object[]{baseUrl, e.getMessage()});
        }
        return candidates;
    }

    private static String normalizeText(final String text) {
        if (text == null) {
            return null;
        }
        final String trimmed = text
                .replaceAll("\\s+", " ")
                .strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Extrai contexto semântico do DOM ao redor do link:
     * texto do pai direto, breadcrumbs ({@code nav}, {@code ol}),
     * e cabeçalhos ({@code h1}–{@code h6}) ancestrais.
     */
    private static String extractDomContext(
            final org.jsoup.nodes.Element anchor) {
        final StringBuilder ctx = new StringBuilder();

        final org.jsoup.nodes.Element parent = anchor.parent();
        if (parent != null) {
            final String parentTag = parent.tagName();
            if ("li".equals(parentTag) || "div".equals(parentTag)
                    || "span".equals(parentTag)
                    || "p".equals(parentTag)) {
                final String parentText =
                        normalizeText(parent.ownText());
                if (parentText != null) {
                    ctx.append(parentText);
                }
            }
        }

        final org.jsoup.nodes.Element nav =
                anchor.closest("nav, [role=navigation]");
        if (nav != null) {
            final String navLabel =
                    normalizeText(nav.attr("aria-label"));
            if (navLabel != null) {
                if (!ctx.isEmpty()) {
                    ctx.append(" | ");
                }
                ctx.append("nav: ").append(navLabel);
            }
        }

        org.jsoup.nodes.Element current = anchor.parent();
        while (current != null) {
            final String tag = current.tagName();
            if (tag.length() == 2 && tag.charAt(0) == 'h'
                    && tag.charAt(1) >= '1'
                    && tag.charAt(1) <= '6') {
                if (!ctx.isEmpty()) {
                    ctx.append(" | ");
                }
                ctx.append(normalizeText(current.text()));
                break;
            }
            current = current.parent();
        }

        if (ctx.isEmpty()) {
            return null;
        }
        final String result = ctx.toString();
        if (result.length() > DOM_CONTEXT_MAX_LENGTH) {
            return result.substring(0, DOM_CONTEXT_MAX_LENGTH);
        }
        return result;
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

    /** Porta HTTP implícita quando ausente na URL. */
    private static final int HTTP_DEFAULT_PORT = 80;
    /** Porta HTTPS implícita quando ausente na URL. */
    private static final int HTTPS_DEFAULT_PORT = 443;

    /**
     * Indica se duas URLs referem o mesmo documento (ex.: com e sem {@code /}
     * final na raiz), para não ranquear a própria página como próximo passo.
     *
     * @param urlA primeira URL
     * @param urlB segunda URL
     * @return {@code true} se apontarem para o mesmo recurso (mesmo host,
     *         caminho normalizado e mesma query)
     */
    static boolean isSameDocument(final String urlA, final String urlB) {
        if (urlA == null || urlB == null) {
            return false;
        }
        if (urlA.equals(urlB)) {
            return true;
        }
        try {
            final URI a = new URI(urlA).normalize();
            final URI b = new URI(urlB).normalize();
            if (!equalsIgnoreCase(a.getScheme(), b.getScheme())) {
                return false;
            }
            if (!equalsIgnoreCase(a.getHost(), b.getHost())) {
                return false;
            }
            if (effectivePort(a) != effectivePort(b)) {
                return false;
            }
            if (!normalizePathForComparison(a.getPath())
                    .equals(normalizePathForComparison(b.getPath()))) {
                return false;
            }
            return Objects.equals(a.getQuery(), b.getQuery());
        } catch (final URISyntaxException e) {
            return false;
        }
    }

    private static boolean equalsIgnoreCase(
            final String x, final String y) {
        if (x == null || y == null) {
            return Objects.equals(x, y);
        }
        return x.equalsIgnoreCase(y);
    }

    private static int effectivePort(final URI u) {
        final int explicit = u.getPort();
        if (explicit != -1) {
            return explicit;
        }
        final String s = u.getScheme();
        if ("http".equalsIgnoreCase(s)) {
            return HTTP_DEFAULT_PORT;
        }
        if ("https".equalsIgnoreCase(s)) {
            return HTTPS_DEFAULT_PORT;
        }
        return -1;
    }

    private static String normalizePathForComparison(final String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
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
