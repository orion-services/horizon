package com.orion.horizon.domain.model;

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

