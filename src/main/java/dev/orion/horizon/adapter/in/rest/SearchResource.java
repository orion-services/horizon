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

import dev.orion.horizon.adapter.in.rest.dto.SearchOptionsDto;
import dev.orion.horizon.adapter.in.rest.dto.SearchRequestDto;
import dev.orion.horizon.adapter.in.rest.dto.SearchResponseDto;
import dev.orion.horizon.domain.model.JobOptions;
import dev.orion.horizon.domain.port.in.CrawlJobPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.UUID;

/**
 * Endpoint REST para submissão de jobs de busca/crawl.
 *
 * <p>POST /HorizOn/search — aceita uma query e URL, cria o job
 * assincronamente e retorna 202 Accepted com o jobId.
 */
@Path("/HorizOn/search")
@ApplicationScoped
public class SearchResource {

    private final CrawlJobPort crawlJobPort;

    /**
     * Cria o resource com injeção CDI.
     *
     * @param crawlJobPort porta de entrada do domínio
     */
    @Inject
    public SearchResource(final CrawlJobPort crawlJobPort) {
        this.crawlJobPort = crawlJobPort;
    }

    /**
     * Submete um novo job de busca/crawl.
     *
     * @param request corpo com query, url e opções opcionais
     * @return 202 Accepted com jobId e status PENDING;
     *         400 Bad Request se a validação falhar
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(@Valid final SearchRequestDto request) {
        final JobOptions options = toJobOptions(request.options());
        final UUID jobId =
                crawlJobPort.submitJob(
                        request.query(),
                        request.url(),
                        options
                );
        final SearchResponseDto resp = new SearchResponseDto(
                jobId, "PENDING", Instant.now()
        );
        return Response.accepted(resp).build();
    }

    private static JobOptions toJobOptions(
            final SearchOptionsDto dto) {
        if (dto == null) {
            return null;
        }
        return new JobOptions(
                dto.maxDepth(),
                dto.maxSteps(),
                dto.timeoutMs()
        );
    }
}
