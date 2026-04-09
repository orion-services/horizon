package com.orion.horizon.domain.model;

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

