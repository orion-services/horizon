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

package dev.orion.horizon.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resposta de consulta de status de um job.
 *
 * @param jobId identificador do job
 * @param status status atual do job
 * @param query consulta original do usuário
 * @param result resultado consolidado; {@code null} enquanto não concluído
 * @param errors lista de erros não-fatais
 * @param createdAt instante de criação
 * @param finishedAt instante de término; {@code null} enquanto em andamento
 */
public record JobStatusDto(
        @JsonProperty("job_id")
        UUID jobId,

        String status,

        String query,

        JobResultDto result,

        List<String> errors,

        @JsonProperty("created_at")
        Instant createdAt,

        @JsonProperty("finished_at")
        Instant finishedAt
) {}
