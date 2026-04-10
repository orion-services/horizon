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

package dev.orion.horizon.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuração do agente extrator (provedor Anthropic e credenciais).
 */
@ConfigMapping(prefix = "horizon.agents.extractor")
public interface AgentExtractorConfig {

    /**
     * @return URL base da API Messages (permite mock em testes)
     */
    @WithDefault("https://api.anthropic.com/v1/messages")
    @WithName("base-url")
    String baseUrl();

    /**
     * @return identificador do provedor (ex.: ANTHROPIC)
     */
    String provider();

    /**
     * @return modelo solicitado na API
     */
    String model();

    /**
     * @return chave de API
     */
    @WithName("api-key")
    String apiKey();

    /**
     * @return teto padrão de tokens de saída
     */
    @WithName("max-tokens")
    int maxTokens();
}
