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
 * Estado de processamento de uma página dentro de um job de crawl.
 */
public enum PageStatus {
    /** A página ainda não foi visitada. */
    PENDING,
    /** A página está em processamento (fetch/render/extraction). */
    PROCESSING,
    /** A página foi processada sem gerar resultado final. */
    DONE,
    /** Um resultado relevante foi encontrado a partir desta página. */
    RESULT_FOUND,
    /**
     * A página foi descartada (p.ex. baixa pontuação, fora do domínio, etc.).
     */
    DISCARDED
}

