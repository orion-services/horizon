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

package dev.orion.horizon.adapter.out.enricher;

import dev.orion.horizon.domain.model.LinkCandidate;
import dev.orion.horizon.domain.port.out.LinkEnricherPort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Adapter que enriquece candidatos a link buscando {@code <title>} e
 * {@code <meta name="description">} de cada URL em paralelo com Jsoup.
 *
 * <p>Falhas por item marcam {@link LinkCandidate#enrichmentFailed()} sem
 * interromper os demais.
 */
@ApplicationScoped
public class JsoupLinkEnricherAdapter implements LinkEnricherPort {

    private static final Logger LOG =
            Logger.getLogger(JsoupLinkEnricherAdapter.class.getName());

    /** User-Agent realista para evitar bloqueios simples. */
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; HorizOn/1.0)";

    @Override
    public void enrich(
            final List<LinkCandidate> candidates,
            final long timeoutMs) {
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.isEmpty()) {
            return;
        }

        final ExecutorService pool =
                Executors.newFixedThreadPool(
                        Math.min(candidates.size(), 10)
                );

        try {
            final List<EnrichTask> tasks = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                final int idx = i;
                final LinkCandidate candidate = candidates.get(idx);
                final Future<EnrichedData> future = pool.submit(
                        () -> fetchMetadata(
                                candidate.url(),
                                (int) timeoutMs
                        )
                );
                tasks.add(new EnrichTask(idx, future));
            }

            for (final EnrichTask task : tasks) {
                try {
                    final EnrichedData data =
                            task.future().get(
                                    timeoutMs + 1000L,
                                    TimeUnit.MILLISECONDS
                            );
                    final LinkCandidate original =
                            candidates.get(task.index());
                    candidates.set(
                            task.index(),
                            withEnrichment(original, data)
                    );
                } catch (final Exception e) {
                    LOG.log(
                            Level.FINE,
                            "Enrichment failed for index {0}: {1}",
                            new Object[]{task.index(), e.getMessage()}
                    );
                    final LinkCandidate original =
                            candidates.get(task.index());
                    candidates.set(
                            task.index(),
                            withFailure(original)
                    );
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static EnrichedData fetchMetadata(
            final String url,
            final int timeoutMs) throws Exception {
        final Connection conn =
                Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(timeoutMs)
                        .followRedirects(true)
                        .ignoreHttpErrors(true);
        final Document doc = conn.get();
        final String title = doc.title();
        final String description =
                doc.selectFirst("meta[name=description]") != null
                        ? doc.selectFirst("meta[name=description]")
                                .attr("content")
                        : null;
        return new EnrichedData(
                title.isEmpty() ? null : title,
                description
        );
    }

    private static LinkCandidate withEnrichment(
            final LinkCandidate original,
            final EnrichedData data) {
        return new LinkCandidate(
                original.url(),
                original.anchorText(),
                original.domContext(),
                original.ariaLabel(),
                original.phase1Score(),
                original.phase1Justification(),
                data.title(),
                data.description(),
                false,
                original.finalScore(),
                original.finalJustification()
        );
    }

    private static LinkCandidate withFailure(
            final LinkCandidate original) {
        return new LinkCandidate(
                original.url(),
                original.anchorText(),
                original.domContext(),
                original.ariaLabel(),
                original.phase1Score(),
                original.phase1Justification(),
                original.pageTitle(),
                original.metaDescription(),
                true,
                original.finalScore(),
                original.finalJustification()
        );
    }

    /**
     * Dados de enriquecimento de um link.
     *
     * @param title título da página
     * @param description meta description
     */
    private record EnrichedData(String title, String description) {
    }

    /**
     * Tarefa de enriquecimento associando índice ao Future.
     *
     * @param index índice na lista de candidatos
     * @param future resultado assíncrono
     */
    private record EnrichTask(int index, Future<EnrichedData> future) {
    }
}
