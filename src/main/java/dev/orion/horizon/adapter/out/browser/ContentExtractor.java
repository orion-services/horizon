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

import dev.orion.horizon.CrawlerConfig;
import dev.orion.horizon.domain.model.ExtractionMethod;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

/**
 * Pipeline de extração de texto com 3 camadas de fallback.
 *
 * <ol>
 *   <li>Readability.js — usa o {@code textContent} do Playwright</li>
 *   <li>Jsoup — remoção de ruído (nav, header, footer, etc.)</li>
 *   <li>RAW — corpo completo sem filtro</li>
 * </ol>
 */
@ApplicationScoped
public class ContentExtractor {

    private static final Logger LOG =
            Logger.getLogger(ContentExtractor.class.getName());

    /** Conteúdo mínimo (caracteres) para considerar a camada válida. */
    private final int minContentLength;

    /**
     * Construtor CDI — injeta configuração via {@link CrawlerConfig}.
     *
     * @param config configuração do crawler
     */
    @Inject
    public ContentExtractor(final CrawlerConfig config) {
        this.minContentLength = config.minContentLength();
    }

    /**
     * Construtor para testes unitários sem CDI.
     *
     * @param minContentLength limite mínimo em caracteres
     */
    public ContentExtractor(final int minContentLength) {
        this.minContentLength = minContentLength;
    }

    /**
     * Executa o pipeline de extração sobre o HTML fornecido.
     *
     * <p>O parâmetro {@code readabilityText} deve conter o
     * {@code textContent} extraído pelo Readability.js (pode ser
     * {@code null}).
     *
     * @param html HTML completo da página renderizada
     * @param readabilityText resultado do Readability.js, ou {@code null}
     * @param pageUrl URL da página (para log na camada RAW)
     * @return resultado com o texto extraído, o método e o tamanho
     */
    public ExtractionResult extract(
            final String html,
            final String readabilityText,
            final String pageUrl) {
        if (readabilityText != null
                && readabilityText.length() >= minContentLength) {
            return new ExtractionResult(
                    readabilityText,
                    ExtractionMethod.READABILITY,
                    readabilityText.length()
            );
        }

        final String jsoupText = extractWithJsoup(html);
        if (jsoupText.length() >= minContentLength) {
            return new ExtractionResult(
                    jsoupText,
                    ExtractionMethod.JSOUP,
                    jsoupText.length()
            );
        }

        LOG.log(
                Level.WARNING,
                "RAW extraction fallback used for URL: {0}",
                pageUrl
        );
        final String rawText = extractRaw(html);
        return new ExtractionResult(
                rawText,
                ExtractionMethod.RAW,
                rawText.length()
        );
    }

    private static String extractWithJsoup(final String html) {
        final Document doc = Jsoup.parse(html);
        final Elements noise = doc.select(
                "nav, header, footer, aside,"
                + " script, style, noscript,"
                + " [role=navigation],"
                + " [role=banner],"
                + " [role=complementary],"
                + " .cookie-banner, .popup, .modal,"
                + " .advertisement, .sidebar"
        );
        noise.remove();
        return doc.text();
    }

    private static String extractRaw(final String html) {
        return Jsoup.parse(html).body().text();
    }

    /**
     * Resultado imutável de uma extração de conteúdo.
     *
     * @param text texto extraído
     * @param method método de extração utilizado
     * @param length tamanho do texto extraído (caracteres)
     */
    public record ExtractionResult(
            String text,
            ExtractionMethod method,
            int length) {
    }
}
