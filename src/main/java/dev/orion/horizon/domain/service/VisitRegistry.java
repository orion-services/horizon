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

import dev.orion.horizon.domain.model.PageNode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro thread-safe de URLs já visitadas durante um crawl.
 *
 * <p>Usa {@link ConcurrentHashMap#putIfAbsent(Object, Object)} para garantir
 * que apenas uma entrada por URL seja mantida sob concorrência.
 */
public final class VisitRegistry {

    /** Mapa URL → primeiro {@link PageNode} registrado para essa URL. */
    private final ConcurrentHashMap<String, PageNode> visited =
            new ConcurrentHashMap<>();

    /**
     * Registra a visita à URL, se ainda não existir entrada.
     *
     * @param url URL normalizada usada como chave; não deve ser {@code null}
     * @param node nó associado à primeira visita; não deve ser {@code null}
     * @return {@code null} se esta foi a primeira inserção para {@code url};
     *         caso contrário, o {@link PageNode} previamente armazenado
     */
    public PageNode markVisited(final String url, final PageNode node) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(node, "node");
        return visited.putIfAbsent(url, node);
    }

    /**
     * Indica se a URL já consta como visitada.
     *
     * @param url chave a consultar; não deve ser {@code null}
     * @return {@code true} se houver entrada para {@code url}
     */
    public boolean isVisited(final String url) {
        Objects.requireNonNull(url, "url");
        return visited.containsKey(url);
    }

    /**
     * Quantidade de URLs distintas registradas.
     *
     * @return tamanho atual do mapa
     */
    public int size() {
        return visited.size();
    }

    /**
     * Cópia defensiva e somente leitura do estado atual.
     *
     * @return mapa imutável com o snapshot no instante da chamada
     */
    public Map<String, PageNode> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(visited));
    }
}
