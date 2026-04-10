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

import dev.orion.horizon.CrawlerConfig;
import dev.orion.horizon.domain.model.CrawlParameters;
import dev.orion.horizon.domain.model.JobOptions;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrawlerConfigMappingTest {

    private static final CrawlerConfig DEFAULTS = new FixedCrawlerConfig();

    @Test
    void fromCopiesAllFieldsFromCrawlerConfig() {
        final CrawlParameters p = CrawlerConfigMapping.from(DEFAULTS);
        assertEquals(5, p.maxDepth());
        assertEquals(20, p.maxSteps());
        assertEquals(60_000L, p.sessionTimeoutMs());
        assertEquals(3, p.threadCount());
        assertEquals(true, p.restrictToDomain());
        assertEquals(200, p.minContentLength());
        assertEquals(3_000, p.chunkSize());
        assertEquals(300, p.chunkOverlap());
        assertEquals(15_000L, p.playwrightTimeoutMs());
        assertEquals(5_000L, p.jsoupEnrichTimeoutMs());
        assertEquals(0.40, p.preThreshold(), 0.000_001);
        assertEquals(0.60, p.finalThreshold(), 0.000_001);
        assertEquals(500, p.llmDelayMs());
        assertEquals(List.of(30, 60, 120), p.backoff429Seconds());
        assertEquals(3, p.maxConsecutive429());
    }

    @Test
    void mergeWithNullJobOptionsEqualsFrom() {
        final CrawlParameters merged =
                CrawlerConfigMapping.merge(DEFAULTS, null);
        assertEquals(CrawlerConfigMapping.from(DEFAULTS), merged);
    }

    @Test
    void mergeAppliesPartialOverrides() {
        final CrawlParameters p =
                CrawlerConfigMapping.merge(
                        DEFAULTS, new JobOptions(7, null, null));
        assertEquals(7, p.maxDepth());
        assertEquals(20, p.maxSteps());
        assertEquals(60_000L, p.sessionTimeoutMs());
        assertEquals(3, p.threadCount());
    }

    @Test
    void mergeAppliesAllJobOptions() {
        final CrawlParameters p =
                CrawlerConfigMapping.merge(
                        DEFAULTS, new JobOptions(9, 42, 12_000L));
        assertEquals(9, p.maxDepth());
        assertEquals(42, p.maxSteps());
        assertEquals(12_000L, p.sessionTimeoutMs());
    }

    @Test
    void backoff429SecondsListIsImmutable() {
        final CrawlParameters p = CrawlerConfigMapping.from(DEFAULTS);
        assertThrows(
                UnsupportedOperationException.class,
                () -> p.backoff429Seconds().add(0));
    }

    /**
     * Valores alinhados aos defaults de teste em
     * {@link dev.orion.horizon.CrawlerConfigTest}.
     */
    private static final class FixedCrawlerConfig implements CrawlerConfig {

        @Override
        public int maxDepth() {
            return 5;
        }

        @Override
        public int maxSteps() {
            return 20;
        }

        @Override
        public long sessionTimeoutMs() {
            return 60_000L;
        }

        @Override
        public int threadCount() {
            return 3;
        }

        @Override
        public boolean restrictToDomain() {
            return true;
        }

        @Override
        public int minContentLength() {
            return 200;
        }

        @Override
        public int chunkSize() {
            return 3_000;
        }

        @Override
        public int chunkOverlap() {
            return 300;
        }

        @Override
        public long playwrightTimeoutMs() {
            return 15_000L;
        }

        @Override
        public long jsoupEnrichTimeoutMs() {
            return 5_000L;
        }

        @Override
        public double preThreshold() {
            return 0.40;
        }

        @Override
        public double finalThreshold() {
            return 0.60;
        }

        @Override
        public int llmDelayMs() {
            return 500;
        }

        @Override
        public int[] backoff429Seconds() {
            return new int[]{30, 60, 120};
        }

        @Override
        public int maxConsecutive429() {
            return 3;
        }
    }
}
