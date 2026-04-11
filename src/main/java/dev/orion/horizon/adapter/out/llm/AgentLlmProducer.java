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
import dev.orion.horizon.domain.port.out.LLMProviderPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import okhttp3.OkHttpClient;

/**
 * Produz o {@link LLMProviderPort} de cada agente conforme
 * {@code horizon.agents.<agente>.provider}.
 */
@ApplicationScoped
public class AgentLlmProducer {

    /**
     * LLM do verificador de relevância.
     */
    @Produces
    @Named("verifierLlm")
    @ApplicationScoped
    public LLMProviderPort verifierLlm(
            final OkHttpClient httpClient,
            final ObjectMapper objectMapper,
            final AgentVerifierConfig config) {
        return LlmProviderFactory.forVerifier(
                httpClient, objectMapper, config
        );
    }

    /**
     * LLM do ranqueador de links.
     */
    @Produces
    @Named("rankerLlm")
    @ApplicationScoped
    public LLMProviderPort rankerLlm(
            final OkHttpClient httpClient,
            final ObjectMapper objectMapper,
            final AgentRankerConfig config) {
        return LlmProviderFactory.forRanker(
                httpClient, objectMapper, config
        );
    }

    /**
     * LLM do extrator.
     */
    @Produces
    @Named("extractorLlm")
    @ApplicationScoped
    public LLMProviderPort extractorLlm(
            final OkHttpClient httpClient,
            final ObjectMapper objectMapper,
            final AgentExtractorConfig config) {
        return LlmProviderFactory.forExtractor(
                httpClient, objectMapper, config
        );
    }

    /**
     * LLM do consolidador.
     */
    @Produces
    @Named("consolidatorLlm")
    @ApplicationScoped
    public LLMProviderPort consolidatorLlm(
            final OkHttpClient httpClient,
            final ObjectMapper objectMapper,
            final AgentConsolidatorConfig config) {
        return LlmProviderFactory.forConsolidator(
                httpClient, objectMapper, config
        );
    }
}
