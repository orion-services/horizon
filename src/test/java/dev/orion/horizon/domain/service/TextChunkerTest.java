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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários do {@link TextChunker}.
 */
final class TextChunkerTest {

    @Test
    void emptyTextReturnsEmptyList() {
        final TextChunker chunker = new TextChunker(100, 10);
        final List<String> chunks = chunker.chunk("");
        assertTrue(chunks.isEmpty());
    }

    @Test
    void shortTextProducesSingleChunk() {
        final TextChunker chunker = new TextChunker(100, 10);
        final List<String> chunks = chunker.chunk("Hello world");
        assertEquals(1, chunks.size());
        assertEquals("Hello world", chunks.get(0));
    }

    @Test
    void longTextProducesMultipleChunks() {
        final TextChunker chunker = new TextChunker(10, 2);
        final String text = "0123456789ABCDEFGHIJ";
        final List<String> chunks = chunker.chunk(text);
        assertTrue(chunks.size() > 1);
    }

    @Test
    void chunksHaveCorrectOverlap() {
        final TextChunker chunker = new TextChunker(10, 3);
        final String text = "AAAAAAAAAA" + "BBBBBBBBB";
        final List<String> chunks = chunker.chunk(text);
        assertTrue(chunks.size() >= 2);
        final String first = chunks.get(0);
        final String second = chunks.get(1);
        final String overlap =
                first.substring(first.length() - 3);
        assertTrue(second.startsWith(overlap));
    }

    @Test
    void invalidChunkSizeThrowsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TextChunker(0, 0)
        );
    }

    @Test
    void invalidOverlapThrowsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TextChunker(10, 10)
        );
    }

    @Test
    void negativeOverlapThrowsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TextChunker(10, -1)
        );
    }
}
