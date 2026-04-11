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

package dev.orion.horizon.adapter.out.persistence;

import dev.orion.horizon.adapter.out.persistence.entity.AgentCallEntity;
import dev.orion.horizon.adapter.out.persistence.entity.CrawlJobEntity;
import dev.orion.horizon.adapter.out.persistence.entity.CrawlResultEntity;
import dev.orion.horizon.adapter.out.persistence.entity.LinkCandidateEntity;
import dev.orion.horizon.adapter.out.persistence.entity.PageVisitEntity;
import dev.orion.horizon.domain.model.AgentCallLog;
import dev.orion.horizon.domain.model.CrawlJob;
import dev.orion.horizon.domain.model.CrawlResult;
import dev.orion.horizon.domain.model.CrawlParameters;
import dev.orion.horizon.domain.model.LinkCandidate;
import dev.orion.horizon.domain.model.PageNode;
import dev.orion.horizon.domain.port.out.PersistencePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter de persistência usando Hibernate ORM + Panache.
 *
 * <p>Implementa {@link PersistencePort} para PostgreSQL em produção
 * e H2 em testes.
 */
@ApplicationScoped
public class PostgresPersistenceAdapter implements PersistencePort {

    /** Número de colunas na exportação CSV. */
    private static final int CSV_COLUMNS = 25;

    /**
     * Limite alinhado a {@link AgentCallEntity} (Hibernate
     * {@code VARCHAR(65535)} / colunas de texto longo).
     */
    private static final int MAX_AGENT_CALL_LOB_CHARS = 65535;

    /**
     * Hibernate usa {@code VARCHAR(255)} quando {@code error_message} não tem
     * {@code length} explícito; mensagens de API (ex.: JSON de erro) podem
     * ultrapassar.
     */
    private static final int MAX_AGENT_CALL_ERROR_CHARS = 255;

    private static String truncateToMaxChars(final String s, final int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        if (max <= 3) {
            return s.substring(0, max);
        }
        return s.substring(0, max - 3) + "...";
    }

    @Override
    @Transactional
    public void saveJob(final CrawlJob job) {
        final CrawlJobEntity entity = toEntity(job);
        entity.persist();
    }

    @Override
    @Transactional
    public void updateJob(final CrawlJob job) {
        final CrawlJobEntity entity =
                CrawlJobEntity.findById(job.id);
        if (entity == null) {
            final CrawlJobEntity newEntity = toEntity(job);
            newEntity.persist();
            return;
        }
        applyJobUpdate(entity, job);
        entity.persist();
    }

    @Override
    @Transactional
    public void savePageVisit(
            final UUID jobId,
            final PageNode node) {
        final PageVisitEntity entity = new PageVisitEntity();
        entity.id = UUID.randomUUID();
        entity.jobId = jobId;
        entity.url = node.url;
        entity.originUrl = node.originUrl;
        entity.depth = node.depth;
        entity.linkScore = node.scoreReceived;
        entity.rankerJustification = node.rankerJustification;
        entity.status =
                node.status != null ? node.status.name() : null;
        entity.extractionMethod =
                node.extractionMethod != null
                        ? node.extractionMethod.name()
                        : null;
        entity.contentLength =
                node.extractedContent != null
                        ? node.extractedContent.length()
                        : null;
        entity.chunkCount =
                node.chunks != null ? node.chunks.size() : null;
        entity.failureReason = node.failureReason;
        entity.visitedAt =
                node.visitedAt != null ? node.visitedAt : Instant.now();
        entity.persist();
    }

    @Override
    @Transactional
    public void saveLinkCandidate(
            final UUID jobId,
            final String sourceUrl,
            final LinkCandidate link) {
        final LinkCandidateEntity entity = new LinkCandidateEntity();
        entity.id = UUID.randomUUID();
        entity.jobId = jobId;
        entity.sourcePageUrl = sourceUrl;
        entity.url = link.url();
        entity.anchorText = link.anchorText();
        entity.domContext = link.domContext();
        entity.ariaLabel = link.ariaLabel();
        entity.pageTitle = link.pageTitle();
        entity.metaDescription = link.metaDescription();
        entity.enrichmentFailed = link.enrichmentFailed();
        entity.phase1Score = link.phase1Score();
        entity.phase1Justification = link.phase1Justification();
        entity.finalScore = link.finalScore();
        entity.finalJustification = link.finalJustification();
        entity.approved =
                link.finalScore() != null && link.finalScore() >= 0.60;
        entity.evaluatedAt = Instant.now();
        entity.persist();
    }

    @Override
    @Transactional
    public void saveCrawlResult(
            final UUID jobId,
            final CrawlResult result) {
        final CrawlResultEntity entity = new CrawlResultEntity();
        entity.id = UUID.randomUUID();
        entity.jobId = jobId;
        entity.threadId = result.threadId;
        entity.sourceUrl = result.sourceUrl;
        entity.originChain =
                result.originChain != null
                        ? result.originChain.toArray(new String[0])
                        : null;
        entity.foundAtDepth = result.foundAtDepth;
        entity.pageLinkScore = result.pageLinkScore;
        entity.extractedContent = result.extractedContent;
        entity.keyFacts =
                result.keyFacts != null
                        ? result.keyFacts.toArray(new String[0])
                        : null;
        entity.completeness = result.completeness;
        entity.missingAspects = result.missingAspects;
        entity.extractionMethod =
                result.extractionMethod != null
                        ? result.extractionMethod.name()
                        : null;
        entity.foundAt =
                result.foundAt != null ? result.foundAt : Instant.now();
        entity.persist();
    }

    @Override
    @Transactional
    public void saveAgentCall(
            final UUID jobId,
            final AgentCallLog log) {
        final AgentCallEntity entity = new AgentCallEntity();
        entity.id = UUID.randomUUID();
        entity.jobId = jobId;
        entity.threadId = log.threadId();
        entity.agentRole = log.agentRole();
        entity.provider = log.provider();
        entity.model = log.model();
        entity.pageUrl = log.pageUrl();
        entity.chunkIndex = log.chunkIndex();
        entity.chunkTotal = log.chunkTotal();
        entity.systemPrompt = truncateToMaxChars(
                log.systemPrompt(), MAX_AGENT_CALL_LOB_CHARS);
        entity.userPrompt =
                truncateToMaxChars(log.userPrompt(), MAX_AGENT_CALL_LOB_CHARS);
        entity.rawResponse =
                truncateToMaxChars(log.rawResponse(), MAX_AGENT_CALL_LOB_CHARS);
        entity.parsedRelevant =
                log.parsedRelevant() != null
                        ? log.parsedRelevant().toString()
                        : null;
        entity.confidence = log.confidence();
        entity.inputTokens = log.inputTokens();
        entity.outputTokens = log.outputTokens();
        entity.latencyMs = log.latencyMs();
        entity.httpStatus = log.httpStatus();
        entity.errorMessage =
                truncateToMaxChars(
                        log.errorMessage(), MAX_AGENT_CALL_ERROR_CHARS);
        entity.calledAt =
                log.calledAt() != null ? log.calledAt() : Instant.now();
        entity.persist();
    }

    @Override
    public Optional<CrawlJob> findJobById(final UUID jobId) {
        final CrawlJobEntity entity = CrawlJobEntity.findById(jobId);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(entity));
    }

    @Override
    public List<String[]> exportJobAsCsvRows(final UUID jobId) {
        final List<String[]> rows = new ArrayList<>();
        final List<AgentCallEntity> calls =
                AgentCallEntity.list("jobId", jobId);
        for (final AgentCallEntity call : calls) {
            final PageVisitEntity visit = findVisitForCall(call);
            final String[] row = buildCsvRow(jobId, call, visit);
            rows.add(row);
        }
        return rows;
    }

    private PageVisitEntity findVisitForCall(
            final AgentCallEntity call) {
        if (call.pageUrl == null) {
            return null;
        }
        return PageVisitEntity.find(
                "jobId = ?1 and url = ?2",
                call.jobId,
                call.pageUrl
        ).firstResult();
    }

    private String[] buildCsvRow(
            final UUID jobId,
            final AgentCallEntity call,
            final PageVisitEntity visit) {
        final String[] row = new String[CSV_COLUMNS];
        row[0] = jobId.toString();
        row[1] =
                call.calledAt != null ? call.calledAt.toString() : "";
        row[2] = "AGENT_CALL";
        row[3] = call.threadId != null ? call.threadId : "";
        row[4] = call.pageUrl != null ? call.pageUrl : "";
        row[5] = visit != null && visit.depth != null
                ? String.valueOf(visit.depth)
                : "";
        row[6] = visit != null && visit.extractionMethod != null
                ? visit.extractionMethod
                : "";
        row[7] = visit != null && visit.contentLength != null
                ? String.valueOf(visit.contentLength)
                : "";
        row[8] = call.chunkIndex != null
                ? String.valueOf(call.chunkIndex)
                : "";
        row[9] = call.chunkTotal != null
                ? String.valueOf(call.chunkTotal)
                : "";
        row[10] = call.agentRole != null ? call.agentRole : "";
        row[11] = call.provider != null ? call.provider : "";
        row[12] = call.model != null ? call.model : "";
        row[13] = call.inputTokens != null
                ? String.valueOf(call.inputTokens)
                : "";
        row[14] = call.outputTokens != null
                ? String.valueOf(call.outputTokens)
                : "";
        row[15] = call.latencyMs != null
                ? String.valueOf(call.latencyMs)
                : "";
        row[16] = call.userPrompt != null ? call.userPrompt : "";
        row[17] = call.rawResponse != null ? call.rawResponse : "";
        row[18] = "";
        row[19] = "";
        row[20] = "";
        row[21] = "";
        row[22] = "";
        row[23] = "";
        row[24] = call.errorMessage != null ? call.errorMessage : "";
        return row;
    }

    private static CrawlJobEntity toEntity(final CrawlJob job) {
        final CrawlJobEntity entity = new CrawlJobEntity();
        entity.id = job.id;
        entity.status = job.status != null ? job.status.name() : null;
        entity.userQuery = job.userQuery;
        entity.rootUrl = job.rootUrl;
        applyParameters(entity, job.parameters);
        entity.finalAnswer = job.finalAnswer;
        entity.stopReason =
                job.stopReason != null ? job.stopReason.name() : null;
        entity.totalPages = job.totalPagesVisited;
        entity.totalResults = job.totalResultsFound;
        entity.totalTokens = job.totalTokensConsumed;
        entity.errors =
                job.errors != null
                        ? job.errors.toArray(new String[0])
                        : null;
        entity.createdAt =
                job.createdAt != null ? job.createdAt : Instant.now();
        entity.startedAt = job.startedAt;
        entity.finishedAt = job.finishedAt;
        return entity;
    }

    private static void applyJobUpdate(
            final CrawlJobEntity entity,
            final CrawlJob job) {
        entity.status = job.status != null ? job.status.name() : null;
        entity.finalAnswer = job.finalAnswer;
        entity.stopReason =
                job.stopReason != null ? job.stopReason.name() : null;
        entity.totalPages = job.totalPagesVisited;
        entity.totalResults = job.totalResultsFound;
        entity.totalTokens = job.totalTokensConsumed;
        entity.errors =
                job.errors != null
                        ? job.errors.toArray(new String[0])
                        : null;
        entity.startedAt = job.startedAt;
        entity.finishedAt = job.finishedAt;
    }

    private static void applyParameters(
            final CrawlJobEntity entity,
            final CrawlParameters params) {
        if (params == null) {
            return;
        }
        entity.maxDepth = params.maxDepth();
        entity.maxSteps = params.maxSteps();
        entity.timeoutMs = params.sessionTimeoutMs();
        entity.threadCount = params.threadCount();
    }

    private static CrawlJob toDomain(final CrawlJobEntity entity) {
        final dev.orion.horizon.domain.model.JobStatus status =
                entity.status != null
                        ? dev.orion.horizon.domain.model.JobStatus
                                .valueOf(entity.status)
                        : null;
        final dev.orion.horizon.domain.model.StopReason stopReason =
                entity.stopReason != null
                        ? dev.orion.horizon.domain.model.StopReason
                                .valueOf(entity.stopReason)
                        : null;
        final List<String> errors =
                entity.errors != null
                        ? List.of(entity.errors)
                        : List.of();
        final CrawlParameters params = new CrawlParameters(
                entity.maxDepth,
                entity.maxSteps,
                entity.timeoutMs,
                entity.threadCount,
                true,
                200,
                3000,
                300,
                15000L,
                5000L,
                0.60,
                500,
                List.of(30, 60, 120),
                3
        );
        return new CrawlJob(
                entity.id,
                entity.userQuery,
                entity.rootUrl,
                params,
                status,
                entity.finalAnswer,
                stopReason,
                errors,
                entity.createdAt,
                entity.startedAt,
                entity.finishedAt,
                entity.totalPages,
                entity.totalResults,
                entity.totalTokens
        );
    }
}
