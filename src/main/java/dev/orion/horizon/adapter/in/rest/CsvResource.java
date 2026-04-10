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

import dev.orion.horizon.domain.port.in.CrawlJobPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Endpoint REST para exportação CSV do log de execução de um job.
 *
 * <p>GET /HorizOn/csv/{jobId} — retorna o conteúdo CSV com os 25 campos
 * de log do job, incluindo prompts, respostas e métricas dos agentes.
 */
@Path("/HorizOn/csv")
@ApplicationScoped
public class CsvResource {

    /** Tipo MIME para CSV. */
    private static final String TEXT_CSV =
            "text/csv; charset=UTF-8";

    private final CrawlJobPort crawlJobPort;

    /**
     * Cria o resource com injeção CDI.
     *
     * @param crawlJobPort porta de entrada do domínio
     */
    @Inject
    public CsvResource(final CrawlJobPort crawlJobPort) {
        this.crawlJobPort = crawlJobPort;
    }

    /**
     * Exporta o log de execução de um job como arquivo CSV.
     *
     * @param jobId identificador UUID do job (path param)
     * @return 200 OK com CSV (Content-Disposition: attachment);
     *         404 Not Found se o jobId não existir;
     *         400 Bad Request se o UUID for malformado
     */
    @GET
    @Path("/{jobId}")
    @Produces("text/csv")
    public Response exportCsv(
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

        final Optional<String> csvOpt =
                crawlJobPort.exportCsv(parsedId);
        if (csvOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of(
                            "error",
                            "Job não encontrado: " + jobId
                    ))
                    .build();
        }

        final String filename =
                "horizon_" + jobId.substring(0, 8) + ".csv";
        return Response.ok(csvOpt.get(), TEXT_CSV)
                .header(
                        "Content-Disposition",
                        "attachment; filename=\"" + filename + "\""
                )
                .build();
    }
}
