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

import dev.orion.horizon.domain.model.LinkCandidate;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sintetiza URLs de busca nativa do site-alvo a partir da query do usuário.
 *
 * <p>Muitos e-commerces e portais possuem endpoints de busca com padrões
 * previsíveis ({@code /busca/}, {@code /search?q=}, {@code /s?k=}, etc.).
 * Esta classe gera {@link LinkCandidate}s com essas URLs para que o
 * ranker possa avaliá-las junto com os links extraídos do HTML.
 *
 * <p>Os candidatos são criados com metadados sintéticos (âncora e contexto
 * DOM) para que o LLM entenda a intenção por trás da URL.
 */
public final class SearchUrlSynthesizer {

    private static final Logger LOG =
            Logger.getLogger(SearchUrlSynthesizer.class.getName());

    private SearchUrlSynthesizer() { }

    /**
     * Gera candidatos de busca para o domínio/rootUrl e a query fornecidos.
     *
     * @param rootUrl URL raiz do site (ex.: {@code https://www.magazineluiza.com.br})
     * @param userQuery consulta do usuário em linguagem natural
     * @return lista de {@link LinkCandidate} sintéticos (pode ser vazia se
     *         a URL raiz for inválida)
     */
    public static List<LinkCandidate> synthesize(
            final String rootUrl, final String userQuery) {
        if (rootUrl == null || userQuery == null
                || userQuery.isBlank()) {
            return List.of();
        }

        final String baseUrl = extractBaseUrl(rootUrl);
        if (baseUrl == null) {
            return List.of();
        }

        final String queryPlus =
                userQuery.trim().replace(" ", "+");
        final String queryEncoded =
                URLEncoder.encode(
                        userQuery.trim(), StandardCharsets.UTF_8);

        final List<SearchPattern> patterns = List.of(
                new SearchPattern(
                        "/busca/%s", queryPlus,
                        "Busca interna (path /busca/)"),
                new SearchPattern(
                        "/search?q=%s", queryEncoded,
                        "Busca interna (path /search)"),
                new SearchPattern(
                        "/s?k=%s", queryEncoded,
                        "Busca interna (path /s — Amazon-style)"),
                new SearchPattern(
                        "/pesquisa?q=%s", queryEncoded,
                        "Busca interna (path /pesquisa)"),
                new SearchPattern(
                        "/catalogsearch/result/?q=%s", queryEncoded,
                        "Busca interna (Magento)")
        );

        final List<LinkCandidate> candidates = new ArrayList<>();
        for (final SearchPattern pattern : patterns) {
            final String url = baseUrl
                    + String.format(pattern.pathTemplate, pattern.query);
            final String anchor =
                    "Busca por: " + userQuery.trim();
            final String context =
                    "[URL sintética — " + pattern.description + "] "
                    + "Gerada a partir da query do usuário para "
                    + "atingir diretamente a página de resultados "
                    + "de busca do site.";

            candidates.add(new LinkCandidate(
                    url,
                    anchor,
                    context,
                    null,
                    null, null,
                    null, null,
                    false,
                    null, null
            ));
        }

        return candidates;
    }

    /**
     * Extrai {@code scheme://host[:port]} da URL fornecida.
     *
     * @param url URL completa
     * @return base URL sem path, ou {@code null} se a URL for inválida
     */
    static String extractBaseUrl(final String url) {
        try {
            final URI uri = new URI(url);
            final String scheme = uri.getScheme();
            final String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            final int port = uri.getPort();
            if (port > 0 && !isDefaultPort(scheme, port)) {
                return scheme + "://" + host + ":" + port;
            }
            return scheme + "://" + host;
        } catch (final Exception e) {
            LOG.log(Level.FINE,
                    "Failed to extract base URL from {0}: {1}",
                    new Object[]{url, e.getMessage()});
            return null;
        }
    }

    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;

    private static boolean isDefaultPort(
            final String scheme, final int port) {
        return ("http".equalsIgnoreCase(scheme) && port == HTTP_PORT)
                || ("https".equalsIgnoreCase(scheme)
                    && port == HTTPS_PORT);
    }

    private record SearchPattern(
            String pathTemplate,
            String query,
            String description) { }
}
