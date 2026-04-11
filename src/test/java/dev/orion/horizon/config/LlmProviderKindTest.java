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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LlmProviderKindTest {

    @Test
    void fromConfigMapsAnthropicCaseInsensitive() {
        assertEquals(
                LlmProviderKind.ANTHROPIC,
                LlmProviderKind.fromConfig("anthropic")
        );
        assertEquals(
                LlmProviderKind.ANTHROPIC,
                LlmProviderKind.fromConfig(" ANTHROPIC ")
        );
    }

    @Test
    void fromConfigMapsOpenAiCaseInsensitive() {
        assertEquals(
                LlmProviderKind.OPENAI,
                LlmProviderKind.fromConfig("openai")
        );
        assertEquals(
                LlmProviderKind.OPENAI,
                LlmProviderKind.fromConfig(" OpenAI ")
        );
    }

    @Test
    void fromConfigDefaultsToOllamaForUnknownOrEmpty() {
        assertEquals(LlmProviderKind.OLLAMA, LlmProviderKind.fromConfig(null));
        assertEquals(LlmProviderKind.OLLAMA, LlmProviderKind.fromConfig(""));
        assertEquals(LlmProviderKind.OLLAMA, LlmProviderKind.fromConfig("   "));
        assertEquals(
                LlmProviderKind.OLLAMA,
                LlmProviderKind.fromConfig("ollama")
        );
        assertEquals(LlmProviderKind.OLLAMA, LlmProviderKind.fromConfig("foo"));
    }
}
