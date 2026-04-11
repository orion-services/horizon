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

package dev.orion.horizon.adapter.out.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.LoadState;
import dev.orion.horizon.CrawlerConfig;
import dev.orion.horizon.domain.port.out.BrowserPort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adapter de automação de browser usando Playwright for Java.
 *
 * <p>Mantém um pool de pares {@link Playwright}/{@link Browser} — cada par é
 * usado por apenas uma thread por vez, eliminando a contenção que causa o erro
 * "Cannot find object to call __adopt__". Pares são criados sob demanda e
 * devolvidos ao pool ao final de cada requisição.
 *
 * <p>Se o servidor responder HTTP 429, aguarda o tempo configurado em
 * {@code horizon.crawler.backoff-429-seconds} e tenta novamente, até o limite
 * de {@code horizon.crawler.max-consecutive-429} tentativas.
 */
@ApplicationScoped
public class PlaywrightAdapter implements BrowserPort {

    private static final Logger LOG =
            Logger.getLogger(PlaywrightAdapter.class.getName());

    private static final String READABILITY_PATH =
            "/readability/Readability.js";

    /** Script JS que executa o Readability.js no DOM da página. */
    private static final String READABILITY_EVAL =
            "const clone = document.cloneNode(true);"
            + "const reader = new Readability(clone,"
            + " { charThreshold: 100, keepClasses: false });"
            + "const art = reader.parse();"
            + "if (!art) return null;"
            + "return { title: art.title,"
            + " textContent: art.textContent,"
            + " excerpt: art.excerpt,"
            + " siteName: art.siteName,"
            + " length: art.length };";

    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int DEFAULT_BACKOFF_SECONDS = 30;
    private static final long MS_PER_SECOND = 1000L;

    /** Pool de pares Playwright/Browser disponíveis. */
    private final ConcurrentLinkedQueue<BrowserPair> pool =
            new ConcurrentLinkedQueue<>();

    /** Todos os pares já criados, para cleanup no @PreDestroy. */
    private final Set<BrowserPair> allPairs =
            ConcurrentHashMap.newKeySet();

    private String readabilityJs;

    @Inject
    CrawlerConfig crawlerConfig;

    /**
     * Carrega o Readability.js; pares de browser são criados sob demanda.
     */
    @PostConstruct
    public void init() {
        readabilityJs = loadReadabilityJs();
    }

    /**
     * Fecha todos os browsers e instâncias Playwright criadas.
     */
    @PreDestroy
    public void destroy() {
        for (final BrowserPair pair : allPairs) {
            closePairQuietly(pair);
        }
        allPairs.clear();
        pool.clear();
    }

    @Override
    public Optional<String> loadPage(
            final String url,
            final long timeoutMs) {
        final BrowserPair pair = acquirePair();
        boolean healthy = true;
        try {
            return doLoad(pair, url, timeoutMs);
        } catch (final PlaywrightException e) {
            if (isContextError(e)) {
                healthy = false;
            }
            if (isTimeout(e)) {
                LOG.log(Level.WARNING,
                        "Playwright timeout loading {0}: {1}",
                        new Object[]{url, e.getMessage()});
            } else {
                LOG.log(Level.WARNING,
                        "Playwright error loading {0}: {1}",
                        new Object[]{url, e.getMessage()});
            }
            return Optional.empty();
        } finally {
            releasePair(pair, healthy);
        }
    }

    private Optional<String> doLoad(
            final BrowserPair pair,
            final String url,
            final long timeoutMs) {
        final int[] backoffSecs = backoffSeconds();
        final int maxRetries = crawlerConfig.maxConsecutive429();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            final Page page = pair.browser.newPage();
            try {
                page.setDefaultTimeout(timeoutMs);
                final Response response = page.navigate(url);

                if (response != null
                        && response.status() == HTTP_TOO_MANY_REQUESTS) {
                    if (attempt < maxRetries) {
                        final int waitSec = waitSeconds(backoffSecs, attempt);
                        LOG.log(Level.WARNING,
                                "HTTP 429 for {0} — aguardando {1}s "
                                + "(tentativa {2}/{3})",
                                new Object[]{
                                    url, waitSec,
                                    attempt + 1, maxRetries});
                        Thread.sleep(waitSec * MS_PER_SECOND);
                        continue;
                    }
                    LOG.log(Level.WARNING,
                            "HTTP 429 persistente para {0} após {1} "
                            + "tentativas — descartando",
                            new Object[]{url, maxRetries});
                    return Optional.empty();
                }

                page.waitForLoadState(LoadState.NETWORKIDLE);
                page.evaluate(
                        "window.scrollTo(0, document.body.scrollHeight)"
                );
                page.waitForTimeout(1000);
                expandCollapsedElements(page);
                page.waitForTimeout(500);

                if (readabilityJs != null && !readabilityJs.isEmpty()) {
                    page.evaluate(readabilityJs);
                }

                final String html =
                        (String) page.evaluate(
                                "document.documentElement.outerHTML"
                        );
                return Optional.ofNullable(html);

            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } finally {
                page.close();
            }
        }
        return Optional.empty();
    }

    private BrowserPair acquirePair() {
        BrowserPair pair = pool.poll();
        if (pair == null) {
            pair = createPair();
            allPairs.add(pair);
        }
        return pair;
    }

    private void releasePair(final BrowserPair pair, final boolean healthy) {
        if (healthy) {
            pool.offer(pair);
        } else {
            allPairs.remove(pair);
            closePairQuietly(pair);
        }
    }

    private static BrowserPair createPair() {
        final Playwright pw = Playwright.create();
        final Browser browser = pw.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
        );
        return new BrowserPair(pw, browser);
    }

    private static void closePairQuietly(final BrowserPair pair) {
        try {
            if (pair.browser != null) {
                pair.browser.close();
            }
        } catch (final Exception e) {
            LOG.log(Level.FINE, "Error closing browser: {0}", e.getMessage());
        }
        try {
            if (pair.playwright != null) {
                pair.playwright.close();
            }
        } catch (final Exception e) {
            LOG.log(Level.FINE,
                    "Error closing playwright: {0}", e.getMessage());
        }
    }

    private int[] backoffSeconds() {
        final int[] configured = crawlerConfig.backoff429Seconds();
        if (configured == null || configured.length == 0) {
            return new int[]{DEFAULT_BACKOFF_SECONDS};
        }
        return configured;
    }

    private static int waitSeconds(final int[] backoffSecs, final int attempt) {
        if (attempt < backoffSecs.length) {
            return backoffSecs[attempt];
        }
        return backoffSecs[backoffSecs.length - 1];
    }

    private void expandCollapsedElements(final Page page) {
        try {
            page.evaluate(
                    "document.querySelectorAll('details:not([open])')"
                    + ".forEach(d => {"
                    + "  const s = d.querySelector('summary');"
                    + "  if (s) s.click();"
                    + "});"
            );
            page.evaluate(
                    "document.querySelectorAll('[aria-expanded=\"false\"]')"
                    + ".forEach(el => {"
                    + "  const href = el.getAttribute('href');"
                    + "  const isExternal = href"
                    + "    && href.startsWith('http')"
                    + "    && !href.includes(location.hostname);"
                    + "  if (!isExternal) el.click();"
                    + "});"
            );
            page.evaluate(
                    "document.querySelectorAll('[data-toggle=\"collapse\"]')"
                    + ".forEach(el => el.click());"
            );
        } catch (final PlaywrightException e) {
            LOG.log(
                    Level.FINE,
                    "Could not expand elements: {0}",
                    e.getMessage()
            );
        }
    }

    private static boolean isTimeout(final PlaywrightException e) {
        final String msg = e.getMessage();
        return msg != null
                && (msg.contains("Timeout")
                        || msg.contains("timeout"));
    }

    private static boolean isContextError(final PlaywrightException e) {
        final String msg = e.getMessage();
        return msg != null
                && (msg.contains("__adopt__")
                        || msg.contains("browser-context")
                        || msg.contains("Target closed")
                        || msg.contains("Browser has been closed"));
    }

    private static String loadReadabilityJs() {
        try (InputStream is =
                PlaywrightAdapter.class.getResourceAsStream(
                        READABILITY_PATH)) {
            if (is == null) {
                LOG.warning(
                        "Readability.js not found at " + READABILITY_PATH
                );
                return "";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            LOG.log(
                    Level.WARNING,
                    "Failed to load Readability.js: {0}",
                    e.getMessage()
            );
            return "";
        }
    }

    /**
     * Par imutável de {@link Playwright} e {@link Browser} dedicados a uma
     * única thread por vez.
     */
    private static final class BrowserPair {
        private final Playwright playwright;
        private final Browser browser;

        private BrowserPair(
                final Playwright playwright,
                final Browser browser) {
            this.playwright = playwright;
            this.browser = browser;
        }
    }
}
