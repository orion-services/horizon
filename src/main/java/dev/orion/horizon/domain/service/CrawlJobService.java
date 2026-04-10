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

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.orion.horizon.CrawlerConfig;
import dev.orion.horizon.adapter.out.browser.ContentExtractor;
import dev.orion.horizon.config.AgentConsolidatorConfig;
import dev.orion.horizon.config.AgentExtractorConfig;
import dev.orion.horizon.config.AgentRankerConfig;
import dev.orion.horizon.config.AgentVerifierConfig;
import dev.orion.horizon.config.CrawlerConfigMapping;
import dev.orion.horizon.domain.model.CrawlJob;
import dev.orion.horizon.domain.model.CrawlParameters;
import dev.orion.horizon.domain.model.JobOptions;
import dev.orion.horizon.domain.model.JobStatus;
import dev.orion.horizon.domain.port.in.CrawlJobPort;
import dev.orion.horizon.domain.port.out.BrowserPort;
import dev.orion.horizon.domain.port.out.LinkEnricherPort;
import dev.orion.horizon.domain.port.out.LLMProviderPort;
import dev.orion.horizon.domain.port.out.PersistencePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serviço de aplicação que implementa {@link CrawlJobPort}.
 *
 * <p>Cria o job, persiste em estado PENDING e submete execução
 * assíncrona ao {@link CrawlerOrchestrator} num thread pool virtual.
 */
@ApplicationScoped
public class CrawlJobService implements CrawlJobPort {

    private static final Logger LOG =
            Logger.getLogger(CrawlJobService.class.getName());

    private static final ExecutorService ASYNC_POOL =
            Executors.newVirtualThreadPerTaskExecutor();

    private final CrawlerConfig crawlerConfig;
    private final AgentVerifierConfig verifierConfig;
    private final AgentRankerConfig rankerConfig;
    private final AgentExtractorConfig extractorConfig;
    private final AgentConsolidatorConfig consolidatorConfig;
    private final BrowserPort browser;
    private final ContentExtractor contentExtractor;
    private final LinkEnricherPort linkEnricher;
    private final PersistencePort persistence;
    private final ObjectMapper objectMapper;
    private final LLMProviderPort verifierLlm;
    private final LLMProviderPort rankerLlm;
    private final LLMProviderPort extractorLlm;
    private final LLMProviderPort consolidatorLlm;

    /**
     * Cria o serviço injetando todas as dependências via CDI.
     *
     * @param crawlerConfig configuração do crawler
     * @param verifierConfig configuração do agente verificador
     * @param rankerConfig configuração do agente ranqueador
     * @param extractorConfig configuração do agente extrator
     * @param consolidatorConfig configuração do agente consolidador
     * @param browser adaptador de browser
     * @param contentExtractor extrator de conteúdo
     * @param linkEnricher enriquecedor de links
     * @param persistence porta de persistência
     * @param objectMapper mapper JSON
     * @param verifierLlm provedor LLM do verificador
     * @param rankerLlm provedor LLM do ranqueador
     * @param extractorLlm provedor LLM do extrator
     * @param consolidatorLlm provedor LLM do consolidador
     */
    @Inject
    public CrawlJobService(
            final CrawlerConfig crawlerConfig,
            final AgentVerifierConfig verifierConfig,
            final AgentRankerConfig rankerConfig,
            final AgentExtractorConfig extractorConfig,
            final AgentConsolidatorConfig consolidatorConfig,
            final BrowserPort browser,
            final ContentExtractor contentExtractor,
            final LinkEnricherPort linkEnricher,
            final PersistencePort persistence,
            final ObjectMapper objectMapper,
            @Named("ollama") final LLMProviderPort verifierLlm,
            @Named("ollama") final LLMProviderPort rankerLlm,
            @Named("anthropic") final LLMProviderPort extractorLlm,
            @Named("anthropic") final LLMProviderPort consolidatorLlm) {
        this.crawlerConfig =
                Objects.requireNonNull(crawlerConfig);
        this.verifierConfig =
                Objects.requireNonNull(verifierConfig);
        this.rankerConfig =
                Objects.requireNonNull(rankerConfig);
        this.extractorConfig =
                Objects.requireNonNull(extractorConfig);
        this.consolidatorConfig =
                Objects.requireNonNull(consolidatorConfig);
        this.browser =
                Objects.requireNonNull(browser);
        this.contentExtractor =
                Objects.requireNonNull(contentExtractor);
        this.linkEnricher =
                Objects.requireNonNull(linkEnricher);
        this.persistence =
                Objects.requireNonNull(persistence);
        this.objectMapper =
                Objects.requireNonNull(objectMapper);
        this.verifierLlm =
                Objects.requireNonNull(verifierLlm);
        this.rankerLlm =
                Objects.requireNonNull(rankerLlm);
        this.extractorLlm =
                Objects.requireNonNull(extractorLlm);
        this.consolidatorLlm =
                Objects.requireNonNull(consolidatorLlm);
    }

    @Override
    public UUID submitJob(
            final String query,
            final String url,
            final JobOptions options) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(url, "url");

        final CrawlParameters params =
                CrawlerConfigMapping.merge(crawlerConfig, options);
        final UUID jobId = UUID.randomUUID();
        final CrawlJob job = new CrawlJob(
                jobId,
                query,
                url,
                params,
                JobStatus.PENDING,
                null,
                null,
                List.of(),
                Instant.now(),
                null,
                null,
                0,
                0,
                0L
        );

        persistence.saveJob(job);

        ASYNC_POOL.submit(() -> runAsync(job));

        return jobId;
    }

    @Override
    public Optional<CrawlJob> getStatus(final UUID jobId) {
        Objects.requireNonNull(jobId, "jobId");
        return persistence.findJobById(jobId);
    }

    @Override
    public Optional<String> exportCsv(final UUID jobId) {
        Objects.requireNonNull(jobId, "jobId");
        final List<String[]> rows =
                persistence.exportJobAsCsvRows(jobId);
        if (rows.isEmpty()) {
            if (persistence.findJobById(jobId).isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(buildCsvHeader());
        }
        return Optional.of(buildCsv(rows));
    }

    private void runAsync(final CrawlJob job) {
        try {
            final CrawlJob running = new CrawlJob(
                    job.id, job.userQuery, job.rootUrl,
                    job.parameters, JobStatus.RUNNING,
                    null, null, job.errors,
                    job.createdAt, Instant.now(), null,
                    0, 0, 0L
            );
            persistence.updateJob(running);

            final CrawlParameters params = job.parameters;
            final RateLimiter rateLimiter =
                    new RateLimiter(params);

            final AgentVerifier agentVerifier =
                    new AgentVerifier(
                            verifierLlm, persistence, objectMapper,
                            rateLimiter, verifierConfig.maxTokens()
                    );
            final AgentExtractor agentExtractor =
                    new AgentExtractor(
                            extractorLlm, persistence, objectMapper,
                            rateLimiter, extractorConfig.maxTokens()
                    );
            final AgentRanker agentRanker =
                    new AgentRanker(
                            rankerLlm, linkEnricher, persistence,
                            objectMapper, rateLimiter,
                            rankerConfig.maxTokens(),
                            params.preThreshold(),
                            params.finalThreshold(),
                            params.jsoupEnrichTimeoutMs()
                    );
            final AgentConsolidator agentConsolidator =
                    new AgentConsolidator(
                            consolidatorLlm, persistence, objectMapper,
                            rateLimiter, consolidatorConfig.maxTokens()
                    );

            final CrawlerOrchestrator orchestrator =
                    new CrawlerOrchestrator(
                            params, browser, contentExtractor,
                            agentVerifier, agentExtractor,
                            agentRanker, agentConsolidator,
                            persistence, rateLimiter
                    );

            orchestrator.run(running);
        } catch (final Exception e) {
            LOG.log(Level.SEVERE,
                    "Job {0} failed: {1}",
                    new Object[]{job.id, e.getMessage()});
            final CrawlJob failed = new CrawlJob(
                    job.id, job.userQuery, job.rootUrl,
                    job.parameters, JobStatus.FAILED,
                    null, null,
                    List.of(e.getMessage() != null
                            ? e.getMessage() : "unknown error"),
                    job.createdAt, job.startedAt, Instant.now(),
                    0, 0, 0L
            );
            try {
                persistence.updateJob(failed);
            } catch (final Exception ex) {
                LOG.log(Level.WARNING,
                        "Failed to persist FAILED status: {0}",
                        ex.getMessage());
            }
        }
    }

    private static String buildCsvHeader() {
        return "job_id,called_at,event_type,thread_id,page_url,"
                + "depth,extraction_method,content_length,"
                + "chunk_index,chunk_total,agent_role,provider,"
                + "model,input_tokens,output_tokens,latency_ms,"
                + "agent_prompt,agent_response,col18,col19,"
                + "col20,col21,col22,col23,error_message\n";
    }

    private static String buildCsv(final List<String[]> rows) {
        final StringBuilder sb = new StringBuilder();
        sb.append(buildCsvHeader());
        for (final String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(escapeCsvField(row[i]));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String escapeCsvField(final String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.contains(",")
                || value.contains("\n")
                || value.contains("\"")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
