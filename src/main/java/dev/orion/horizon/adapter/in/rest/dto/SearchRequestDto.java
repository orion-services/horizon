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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Corpo da requisição de busca.
 *
 * @param query consulta do usuário (obrigatório, não vazio)
 * @param url URL inicial do crawl (deve começar com http:// ou https://)
 * @param options parâmetros opcionais de execução
 */
public record SearchRequestDto(
        @NotBlank(message = "query não pode ser vazio")
        String query,

        @NotBlank(message = "url não pode ser vazia")
        @Pattern(
                regexp = "https?://.*",
                message = "url deve começar com http:// ou https://"
        )
        String url,

        @Valid
        SearchOptionsDto options
) {}
