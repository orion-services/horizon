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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.orion.horizon.domain.model.ExtractionMethod;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários do pipeline {@link ContentExtractor}.
 */
final class ContentExtractorTest {

    private static final int MIN_LENGTH = 50;

    @Test
    void readabilityLayerUsedWhenTextIsSufficient() {
        final ContentExtractor extractor =
                new ContentExtractor(MIN_LENGTH);
        final String readabilityText =
                "Conteúdo longo o suficiente para passar no filtro "
                + "mínimo configurado para este teste.";
        final ContentExtractor.ExtractionResult result =
                extractor.extract(
                        "<html><body><p>body</p></body></html>",
                        readabilityText,
                        "https://example.com"
                );

        assertEquals(ExtractionMethod.READABILITY, result.method());
        assertEquals(readabilityText, result.text());
        assertEquals(readabilityText.length(), result.length());
    }

    @Test
    void jsoupLayerUsedWhenReadabilityIsNull() {
        final ContentExtractor extractor =
                new ContentExtractor(MIN_LENGTH);
        final String html =
                "<html><body>"
                + "<nav>navegação ruído</nav>"
                + "<p>Texto principal longo o suficiente para passar"
                + " no limiar mínimo de conteúdo configurado.</p>"
                + "</body></html>";

        final ContentExtractor.ExtractionResult result =
                extractor.extract(html, null, "https://example.com");

        assertEquals(ExtractionMethod.JSOUP, result.method());
        assertNotNull(result.text());
        assertFalse(result.text().contains("navegação ruído"));
    }

    @Test
    void jsoupLayerUsedWhenReadabilityTooShort() {
        final ContentExtractor extractor =
                new ContentExtractor(MIN_LENGTH);
        final String html =
                "<html><body>"
                + "<p>Este parágrafo tem conteúdo suficiente para "
                + "o filtro jsoup ser ativado e retornar algo.</p>"
                + "</body></html>";

        final ContentExtractor.ExtractionResult result =
                extractor.extract(html, "curto", "https://example.com");

        assertEquals(ExtractionMethod.JSOUP, result.method());
    }

    @Test
    void rawLayerUsedWhenEverythingFails() {
        final ContentExtractor extractor =
                new ContentExtractor(10_000);
        final String html =
                "<html><body><p>Pouco texto.</p></body></html>";

        final ContentExtractor.ExtractionResult result =
                extractor.extract(html, null, "https://example.com");

        assertEquals(ExtractionMethod.RAW, result.method());
        assertNotNull(result.text());
    }

    @Test
    void noiseRemovedByJsoupLayer() {
        final ContentExtractor extractor =
                new ContentExtractor(MIN_LENGTH);
        final String html =
                "<html><body>"
                + "<header>cabeçalho ignorado</header>"
                + "<footer>rodapé ignorado</footer>"
                + "<div class=\"sidebar\">lateral ignorada</div>"
                + "<main>Conteúdo principal relevante que deve ser"
                + " preservado pelo pipeline de extração jsoup.</main>"
                + "</body></html>";

        final ContentExtractor.ExtractionResult result =
                extractor.extract(html, null, "https://example.com");

        assertFalse(result.text().contains("cabeçalho ignorado"));
        assertFalse(result.text().contains("rodapé ignorado"));
        assertFalse(result.text().contains("lateral ignorada"));
    }
}
