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

package dev.orion.horizon.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.orion.horizon.domain.model.PageNode;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Testes para {@link VisitRegistry}, incluindo cenário concorrente.
 */
final class VisitRegistryTest {

    @Test
    void markVisitedReturnsNullOnFirstCall() {
        final VisitRegistry registry = new VisitRegistry();
        final String url = "https://example.com/a";
        final PageNode node = minimalNode(url);

        assertNull(registry.markVisited(url, node));
    }

    @Test
    void markVisitedReturnsOriginalNodeOnSecondCall() {
        final VisitRegistry registry = new VisitRegistry();
        final String url = "https://example.com/b";
        final PageNode first = minimalNode(url);
        final PageNode second = minimalNode(url);

        assertNull(registry.markVisited(url, first));
        assertEquals(first, registry.markVisited(url, second));
    }

    @Test
    void isVisitedFalseBeforeAndTrueAfterRegister() {
        final VisitRegistry registry = new VisitRegistry();
        final String url = "https://example.com/c";

        assertFalse(registry.isVisited(url));
        registry.markVisited(url, minimalNode(url));
        assertTrue(registry.isVisited(url));
    }

    @Test
    void hundredThreadsSameUrlOnlyOneFirstInsert() throws Exception {
        final VisitRegistry registry = new VisitRegistry();
        final String url = "https://example.com/concurrent";
        final int threadCount = 100;
        final ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final AtomicInteger firstInsertWins = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int suffix = i;
            pool.submit(
                    () -> {
                        try {
                            start.await();
                            final String nodeUrl = url + "#t" + suffix;
                            final PageNode node = minimalNode(nodeUrl);
                            final PageNode previous =
                                    registry.markVisited(url, node);
                            if (previous == null) {
                                firstInsertWins.incrementAndGet();
                            }
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        } finally {
                            done.countDown();
                        }
                    }
            );
        }

        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS));
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, firstInsertWins.get());
        assertEquals(1, registry.size());
    }

    @Test
    void snapshotReturnsUnmodifiableCopy() {
        final VisitRegistry registry = new VisitRegistry();
        final String url = "https://example.com/snap";
        registry.markVisited(url, minimalNode(url));

        final Map<String, PageNode> first = registry.snapshot();
        assertEquals(1, first.size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> first.put("https://example.com/other", minimalNode(url))
        );

        registry.markVisited("https://example.com/other", minimalNode(url));
        assertEquals(1, first.size());

        final Map<String, PageNode> second = registry.snapshot();
        assertEquals(2, second.size());
        assertNotSame(first, second);
    }

    private static PageNode minimalNode(final String url) {
        return new PageNode(
                url,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
