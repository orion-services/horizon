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

package dev.orion.horizon.domain.port.out;

import dev.orion.horizon.domain.model.LinkCandidate;
import java.util.List;

/**
 * Porta de saída para enriquecimento de metadados de candidatos a link.
 */
public interface LinkEnricherPort {

    /**
     * Enriquece a lista de candidatos no próprio lugar (mutação in-place).
     *
     * <p>Falhas por item devem marcar {@link LinkCandidate#enrichmentFailed()}
     * sem interromper os demais.
     *
     * @param candidates lista mutável de candidatos; não deve ser
     *                   {@code null}
     * @param timeoutMs tempo máximo para a operação de enriquecimento
     * @throws IllegalArgumentException se {@code candidates} for {@code null}
     * @throws RuntimeException se o adaptador estiver indisponível de forma
     *                          irrecuperável
     */
    void enrich(List<LinkCandidate> candidates, long timeoutMs);
}
