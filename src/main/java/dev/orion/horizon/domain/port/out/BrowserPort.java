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

package dev.orion.horizon.domain.port.out;

import java.util.Optional;

/**
 * Porta de saída para carregar o HTML de uma página (motor de navegação).
 */
public interface BrowserPort {

    /**
     * Carrega a página e devolve o HTML renderizado após conteúdo dinâmico.
     *
     * @param url URL absoluta a abrir; não deve ser {@code null}
     * @param timeoutMs tempo máximo de espera pela carga, em milissegundos
     * @return HTML completo em caso de sucesso; vazio em timeout ou erro de
     *         navegação (contrato: não lançar apenas por falha de carga)
     * @throws IllegalArgumentException se {@code url} for inválida para o
     *                                  adaptador
     * @throws RuntimeException se o adaptador estiver indisponível de forma
     *                          irrecuperável (ex.: não inicializado)
     */
    Optional<String> loadPage(String url, long timeoutMs);
}
