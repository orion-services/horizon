package com.orion.horizon;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class CrawlerConfigTest {

    @Inject
    CrawlerConfig config;

    @Test
    void defaultsAreApplied() {
        assertEquals(5, config.maxDepth());
        assertEquals(20, config.maxSteps());
        assertEquals(60_000L, config.sessionTimeoutMs());
        assertEquals(3, config.threadCount());
        assertTrue(config.restrictToDomain());
        assertEquals(200, config.minContentLength());
        assertEquals(3_000, config.chunkSize());
        assertEquals(300, config.chunkOverlap());
        assertEquals(15_000L, config.playwrightTimeoutMs());
        assertEquals(5_000L, config.jsoupEnrichTimeoutMs());
        assertEquals(0.40, config.preThreshold(), 0.000_001);
        assertEquals(0.60, config.finalThreshold(), 0.000_001);
        assertEquals(500, config.llmDelayMs());
        assertArrayEquals(new int[]{30, 60, 120}, config.backoff429Seconds());
        assertEquals(3, config.maxConsecutive429());
    }
}

