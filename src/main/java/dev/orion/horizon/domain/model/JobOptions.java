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

package dev.orion.horizon.domain.model;

/**
 * Sobrescritas opcionais de parâmetros de crawl para um job.
 *
 * <p>Campos {@code null} indicam que o serviço deve usar os valores padrão da
 * configuração da aplicação.
 *
 * @param maxDepth profundidade máxima na árvore de links; {@code null} para
 *                 usar o padrão
 * @param maxSteps limite de páginas visitadas; {@code null} para usar o padrão
 * @param timeoutMs tempo máximo de vida da sessão em milissegundos;
 *                  {@code null} para usar o padrão
 */
public record JobOptions(
        Integer maxDepth,
        Integer maxSteps,
        Long timeoutMs
) {}
