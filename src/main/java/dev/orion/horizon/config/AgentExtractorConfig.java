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
import java.util.Optional;

/**
 * Configuração do agente extrator ({@code OLLAMA}, {@code ANTHROPIC} ou
 * {@code OPENAI}).
 */
@ConfigMapping(prefix = "horizon.agents.extractor")
public interface AgentExtractorConfig extends AnthropicMessagesConfig {

    /**
     * {@inheritDoc}
     */
    @Override
    @WithDefault("https://api.anthropic.com/v1/messages")
    @WithName("base-url")
    String baseUrl();

    /**
     * {@inheritDoc}
     */
    @Override
    @WithDefault("ANTHROPIC")
    String provider();

    /**
     * {@inheritDoc}
     */
    @Override
    @WithDefault("claude-haiku-4-5")
    String model();

    /**
     * {@inheritDoc}
     */
    @Override
    @WithName("api-key")
    Optional<String> apiKey();

    /**
     * {@inheritDoc}
     */
    @Override
    @WithName("max-tokens")
    @WithDefault("1000")
    int maxTokens();
}
