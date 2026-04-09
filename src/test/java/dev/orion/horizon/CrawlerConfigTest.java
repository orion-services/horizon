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

package dev.orion.horizon;

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

