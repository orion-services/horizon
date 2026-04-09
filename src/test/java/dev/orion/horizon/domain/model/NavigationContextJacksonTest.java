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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Testes de serialização/deserialização Jackson para {@link NavigationContext}.
 */
@QuarkusTest
class NavigationContextJacksonTest {
    @Inject
    ObjectMapper objectMapper;

    @Test
    void serializaEDeserializa() throws Exception {
        final NavigationContext ctx =
                new NavigationContext(
                        "buscar preço de notebook",
                        "https://example.com/produtos",
                        "https://example.com",
                        2,
                        5,
                        0.75,
                        "link no menu principal",
                        ExtractionMethod.JSOUP,
                        "Produtos"
                );

        final String json = objectMapper.writeValueAsString(ctx);
        final NavigationContext roundTrip =
                objectMapper.readValue(json, NavigationContext.class);

        assertEquals(ctx, roundTrip);
    }
}

