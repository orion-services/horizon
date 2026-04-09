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

import java.util.PriorityQueue;
import org.junit.jupiter.api.Test;

/**
 * Testes de ordenação para {@link LinkCandidate}.
 */
final class LinkCandidatePriorityQueueTest {
    @Test
    void priorityQueuePopsHighestFinalScoreFirst() {
        final LinkCandidate a = new LinkCandidate(
                "https://example.com/a",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                0.10,
                null
        );
        final LinkCandidate b = new LinkCandidate(
                "https://example.com/b",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                0.90,
                null
        );
        final LinkCandidate c = new LinkCandidate(
                "https://example.com/c",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                0.50,
                null
        );

        final PriorityQueue<LinkCandidate> queue = new PriorityQueue<>();
        queue.add(a);
        queue.add(b);
        queue.add(c);

        assertEquals("https://example.com/b", queue.poll().url());
        assertEquals("https://example.com/c", queue.poll().url());
        assertEquals("https://example.com/a", queue.poll().url());
    }
}

