package com.orion.horizon;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * Configurações do crawler.
 */
@ConfigMapping(prefix = "horizon.crawler")
public interface CrawlerConfig {
    int maxDepth();

    int maxSteps();

    long sessionTimeoutMs();

    int threadCount();

    boolean restrictToDomain();

    int minContentLength();

    int chunkSize();

    int chunkOverlap();

    long playwrightTimeoutMs();

    long jsoupEnrichTimeoutMs();

    double preThreshold();

    double finalThreshold();

    int llmDelayMs();

    @WithName("backoff-429-seconds")
    int[] backoff429Seconds();

    @WithName("max-consecutive-429")
    int maxConsecutive429();
}

