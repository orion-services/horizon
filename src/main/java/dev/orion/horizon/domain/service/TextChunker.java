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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Divide um texto em chunks com sobreposição configurável.
 *
 * <p>Cada chunk tem no máximo {@code chunkSize} caracteres.
 * Chunks consecutivos se sobrepõem em {@code chunkOverlap} caracteres.
 */
public final class TextChunker {

    private final int chunkSize;
    private final int chunkOverlap;

    /**
     * Cria um TextChunker com os parâmetros fornecidos.
     *
     * @param chunkSize tamanho máximo de cada chunk em caracteres
     * @param chunkOverlap sobreposição entre chunks consecutivos
     */
    public TextChunker(final int chunkSize, final int chunkOverlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize deve ser > 0");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException(
                    "chunkOverlap deve ser >= 0 e < chunkSize"
            );
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * Divide o texto em chunks com sobreposição.
     *
     * @param text texto a dividir; não deve ser {@code null}
     * @return lista de chunks; vazia se o texto for vazio
     */
    public List<String> chunk(final String text) {
        Objects.requireNonNull(text, "text");
        final List<String> chunks = new ArrayList<>();
        if (text.isEmpty()) {
            return chunks;
        }

        final int step = chunkSize - chunkOverlap;
        int start = 0;
        while (start < text.length()) {
            final int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start += step;
        }
        return chunks;
    }
}
