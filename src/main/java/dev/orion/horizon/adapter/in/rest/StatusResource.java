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

package dev.orion.horizon.adapter.in.rest;

import dev.orion.horizon.adapter.in.rest.dto.JobResultDto;
import dev.orion.horizon.adapter.in.rest.dto.JobStatusDto;
import dev.orion.horizon.domain.model.CrawlJob;
import dev.orion.horizon.domain.model.JobStatus;
import dev.orion.horizon.domain.port.in.CrawlJobPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Endpoint REST para consulta de status de jobs.
 *
 * <p>GET /HorizOn/status/{jobId} — retorna o estado atual do job,
 * incluindo o resultado quando concluído.
 */
@Path("/HorizOn/status")
@ApplicationScoped
public class StatusResource {

    private final CrawlJobPort crawlJobPort;

    /**
     * Cria o resource com injeção CDI.
     *
     * @param crawlJobPort porta de entrada do domínio
     */
    @Inject
    public StatusResource(final CrawlJobPort crawlJobPort) {
        this.crawlJobPort = crawlJobPort;
    }

    /**
     * Consulta o status de um job pelo seu identificador.
     *
     * @param jobId identificador UUID do job (path param)
     * @return 200 OK com status e resultado (se concluído);
     *         404 Not Found se o jobId não existir;
     *         400 Bad Request se o UUID for malformado
     */
    @GET
    @Path("/{jobId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response status(
            @PathParam("jobId") final String jobId) {
        final UUID parsedId;
        try {
            parsedId = UUID.fromString(jobId);
        } catch (final IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "error",
                            "UUID malformado: " + jobId
                    ))
                    .build();
        }

        final Optional<CrawlJob> jobOpt =
                crawlJobPort.getStatus(parsedId);
        if (jobOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of(
                            "error",
                            "Job não encontrado: " + jobId
                    ))
                    .build();
        }

        final CrawlJob job = jobOpt.get();
        final JobResultDto result = buildResult(job);
        final JobStatusDto dto = new JobStatusDto(
                job.id,
                job.status != null ? job.status.name() : null,
                job.userQuery,
                result,
                job.errors != null ? job.errors : java.util.List.of(),
                job.createdAt,
                job.finishedAt
        );
        return Response.ok(dto).build();
    }

    private static JobResultDto buildResult(final CrawlJob job) {
        if (job.status == null) {
            return null;
        }
        if (job.status == JobStatus.COMPLETED
                && job.finalAnswer != null) {
            return new JobResultDto(
                    job.finalAnswer,
                    Map.of()
            );
        }
        return null;
    }
}
