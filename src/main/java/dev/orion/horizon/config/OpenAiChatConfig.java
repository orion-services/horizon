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

import java.util.Optional;

/**
 * Parâmetros para a API de chat completions da OpenAI.
 *
 * <p>{@link #baseUrl()} deve ser a URL completa do endpoint (não só o host).
 */
public interface OpenAiChatConfig extends OllamaChatConfig {

    /**
     * @return chave de API (vazia se não configurada)
     */
    Optional<String> apiKey();

    /**
     * @return teto padrão de tokens de saída
     */
    int maxTokens();
}
