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

package dev.orion.horizon.adapter.out.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.orion.horizon.config.AgentConsolidatorConfig;
import dev.orion.horizon.config.AgentExtractorConfig;
import dev.orion.horizon.config.AgentRankerConfig;
import dev.orion.horizon.config.AgentVerifierConfig;
import dev.orion.horizon.config.LlmProviderKind;
import dev.orion.horizon.domain.port.out.LLMProviderPort;
import java.util.Objects;
import okhttp3.OkHttpClient;

/**
 * Instancia o {@link LLMProviderPort} adequado conforme
 * {@link LlmProviderKind}.
 */
public final class LlmProviderFactory {

    private LlmProviderFactory() {
    }

    /**
     * Verificador de relevância.
     */
    public static LLMProviderPort forVerifier(
            final OkHttpClient httpClient,
            final ObjectMapper objectMapper,
            final AgentVerifierConfig config) {
        Objects.requireNonNull(config, "config");
        return switch (LlmProviderKind.fromConfig(config.provider())) {
            case ANTHROPIC -> new AnthropicAdapter(
                    httpClient, objectMapper, config
            );
            case OPENAI -> new OpenAIAdapter(
                    httpClient, objectMapper, config
            );
            case OLLAMA -> new OllamaAdapter(
                    httpClient, objectMapper, config
            );
        };
    }

    /**
     * Ranqueador de links.
     */
    public static LLMProviderPort forRanker(
            final OkHttpClient httpClient,
            final ObjectMapper objectMapper,
            final AgentRankerConfig config) {
        Objects.requireNonNull(config, "config");
        return switch (LlmProviderKind.fromConfig(config.provider())) {
            case ANTHROPIC -> new AnthropicAdapter(
                    httpClient, objectMapper, config
            );
            case OPENAI -> new OpenAIAdapter(
                    httpClient, objectMapper, config
            );
            case OLLAMA -> new OllamaAdapter(
                    httpClient, objectMapper, config
            );
        };
    }

    /**
     * Extrator de conteúdo.
     */
    public static LLMProviderPort forExtractor(
            final OkHttpClient httpClient,
            final ObjectMapper objectMapper,
            final AgentExtractorConfig config) {
        Objects.requireNonNull(config, "config");
        return switch (LlmProviderKind.fromConfig(config.provider())) {
            case ANTHROPIC -> new AnthropicAdapter(
                    httpClient, objectMapper, config
            );
            case OPENAI -> new OpenAIAdapter(
                    httpClient, objectMapper, config
            );
            case OLLAMA -> new OllamaAdapter(
                    httpClient, objectMapper, config
            );
        };
    }

    /**
     * Consolidador da resposta final.
     */
    public static LLMProviderPort forConsolidator(
            final OkHttpClient httpClient,
            final ObjectMapper objectMapper,
            final AgentConsolidatorConfig config) {
        Objects.requireNonNull(config, "config");
        return switch (LlmProviderKind.fromConfig(config.provider())) {
            case ANTHROPIC -> new AnthropicAdapter(
                    httpClient, objectMapper, config
            );
            case OPENAI -> new OpenAIAdapter(
                    httpClient, objectMapper, config
            );
            case OLLAMA -> new OllamaAdapter(
                    httpClient, objectMapper, config
            );
        };
    }
}
