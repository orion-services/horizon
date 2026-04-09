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

import dev.orion.horizon.domain.model.AgentResponse;
import dev.orion.horizon.domain.model.LLMRequest;

/**
 * Porta de saída para chamadas a um provedor de modelo de linguagem.
 */
public interface LLMProviderPort {

    /**
     * Executa uma chamada ao modelo com os prompts e limites informados.
     *
     * @param request pedido com prompts e metadados; não deve ser
     *                {@code null}
     * @return resposta estruturada do provedor (erros podem constar no
     *         próprio {@link AgentResponse})
     * @throws IllegalArgumentException se {@code request} for {@code null}
     * @throws RuntimeException se a chamada de rede ou o protocolo do
     *                          provedor falharem de forma irrecuperável
     */
    AgentResponse call(LLMRequest request);

    /**
     * Nome lógico do provedor (ex.: {@code ANTHROPIC}, {@code OLLAMA}).
     *
     * @return identificador estável do provedor
     * @throws RuntimeException se o estado interno do adaptador estiver
     *                          inconsistente
     */
    String getProviderName();

    /**
     * Identificador do modelo configurado para este adaptador.
     *
     * @return nome do modelo (ex.: versão exposta pela API)
     * @throws RuntimeException se o estado interno do adaptador estiver
     *                          inconsistente
     */
    String getModelName();
}
