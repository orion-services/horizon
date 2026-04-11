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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.orion.horizon.domain.model.LinkCandidate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários do {@link SearchUrlSynthesizer}.
 */
final class SearchUrlSynthesizerTest {

    @Test
    void synthesizeGeneratesExpectedPatterns() {
        final List<LinkCandidate> result =
                SearchUrlSynthesizer.synthesize(
                        "https://www.magazineluiza.com.br",
                        "mac mini");

        assertFalse(result.isEmpty());

        final Set<String> urls = result.stream()
                .map(LinkCandidate::url)
                .collect(Collectors.toSet());

        assertTrue(urls.contains(
                "https://www.magazineluiza.com.br/busca/mac+mini"));
        assertTrue(urls.contains(
                "https://www.magazineluiza.com.br/search?q=mac+mini"));
        assertTrue(urls.contains(
                "https://www.magazineluiza.com.br/s?k=mac+mini"));
    }

    @Test
    void synthesizeHandlesUrlWithPath() {
        final List<LinkCandidate> result =
                SearchUrlSynthesizer.synthesize(
                        "https://www.example.com/loja/home",
                        "notebook");

        assertFalse(result.isEmpty());

        final boolean allStartWithBase = result.stream()
                .allMatch(c -> c.url()
                        .startsWith("https://www.example.com/"));
        assertTrue(allStartWithBase);
    }

    @Test
    void synthesizeEncodesSpecialCharacters() {
        final List<LinkCandidate> result =
                SearchUrlSynthesizer.synthesize(
                        "https://www.amazon.com.br",
                        "iPhone 15 Pro Max");

        final Set<String> urls = result.stream()
                .map(LinkCandidate::url)
                .collect(Collectors.toSet());

        assertTrue(urls.contains(
                "https://www.amazon.com.br"
                + "/busca/iPhone+15+Pro+Max"));
        assertTrue(urls.contains(
                "https://www.amazon.com.br"
                + "/s?k=iPhone+15+Pro+Max"));
    }

    @Test
    void synthesizeReturnsEmptyForNullInputs() {
        assertTrue(SearchUrlSynthesizer
                .synthesize(null, "query").isEmpty());
        assertTrue(SearchUrlSynthesizer
                .synthesize("https://example.com", null).isEmpty());
        assertTrue(SearchUrlSynthesizer
                .synthesize("https://example.com", "  ").isEmpty());
    }

    @Test
    void synthesizeReturnsEmptyForInvalidUrl() {
        assertTrue(SearchUrlSynthesizer
                .synthesize("not-a-url", "query").isEmpty());
    }

    @Test
    void candidatesHaveSyntheticMetadata() {
        final List<LinkCandidate> result =
                SearchUrlSynthesizer.synthesize(
                        "https://example.com", "mac mini");

        for (final LinkCandidate candidate : result) {
            assertNotNull(candidate.anchorText());
            assertTrue(candidate.anchorText()
                    .contains("mac mini"));
            assertNotNull(candidate.domContext());
            assertTrue(candidate.domContext()
                    .contains("sintética"));
            assertNull(candidate.phase1Score());
            assertNull(candidate.finalScore());
        }
    }

    @Test
    void extractBaseUrlStripsPathAndQuery() {
        assertEquals("https://www.example.com",
                SearchUrlSynthesizer.extractBaseUrl(
                        "https://www.example.com/foo/bar?x=1"));
    }

    @Test
    void extractBaseUrlPreservesNonDefaultPort() {
        assertEquals("https://localhost:8443",
                SearchUrlSynthesizer.extractBaseUrl(
                        "https://localhost:8443/api"));
    }

    @Test
    void extractBaseUrlOmitsDefaultPort() {
        assertEquals("https://example.com",
                SearchUrlSynthesizer.extractBaseUrl(
                        "https://example.com:443/path"));
    }

    @Test
    void synthesizeTrimsQueryWhitespace() {
        final List<LinkCandidate> result =
                SearchUrlSynthesizer.synthesize(
                        "https://example.com",
                        "  mac mini  ");

        final boolean hasBuscaUrl = result.stream()
                .anyMatch(c -> c.url().contains("/busca/mac+mini"));
        assertTrue(hasBuscaUrl);
    }
}
