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
import com.microsoft.playwright.options.LoadState;
import dev.orion.horizon.domain.port.out.BrowserPort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adapter de automação de browser usando Playwright for Java.
 *
 * <p>O {@link Browser} é singleton {@code @ApplicationScoped} — nunca
 * recriado por requisição. Cada carregamento de página abre uma nova
 * {@link Page} e a fecha ao final.
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

    private Playwright playwright;
    private Browser browser;
    private String readabilityJs;

    /**
     * Inicializa o browser singleton ao iniciar a aplicação.
     */
    @PostConstruct
    public void init() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
        );
        readabilityJs = loadReadabilityJs();
    }

    /**
     * Fecha o browser e o Playwright ao destruir o bean.
     */
    @PreDestroy
    public void destroy() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @Override
    public Optional<String> loadPage(
            final String url,
            final long timeoutMs) {
        try (Page page = browser.newPage()) {
            page.setDefaultTimeout(timeoutMs);
            page.navigate(url);
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
        } catch (final PlaywrightException e) {
            if (isTimeout(e)) {
                LOG.log(
                        Level.WARNING,
                        "Playwright timeout loading {0}: {1}",
                        new Object[]{url, e.getMessage()}
                );
            } else {
                LOG.log(
                        Level.WARNING,
                        "Playwright error loading {0}: {1}",
                        new Object[]{url, e.getMessage()}
                );
            }
            return Optional.empty();
        }
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
}
