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
 * Configuração do agente verificador (provider Ollama por padrão).
 */
@ConfigMapping(prefix = "horizon.agents.verifier")
public interface AgentVerifierConfig {

    /**
     * @return URL base da API do provider
     */
    @WithDefault("http://localhost:11434")
    @WithName("base-url")
    String baseUrl();

    /**
     * @return identificador do provedor (ex.: OLLAMA)
     */
    @WithDefault("OLLAMA")
    String provider();

    /**
     * @return modelo solicitado na API
     */
    @WithDefault("llama3.1")
    String model();

    /**
     * @return teto padrão de tokens de saída
     */
    @WithDefault("512")
    @WithName("max-tokens")
    int maxTokens();
}
