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

package dev.orion.horizon.domain.port.in;

import dev.orion.horizon.domain.model.CrawlJob;
import dev.orion.horizon.domain.model.JobOptions;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de entrada para submissão e consulta de jobs de crawl.
 */
public interface CrawlJobPort {

    /**
     * Enfileira ou inicia um novo job de crawl com a consulta e URL informadas.
     *
     * @param query texto da consulta do usuário
     * @param url URL inicial do crawl
     * @param options sobrescritas opcionais de parâmetros; pode ser
     *                {@code null} para usar apenas a configuração padrão
     * @return identificador único atribuído ao job criado
     * @throws IllegalArgumentException se {@code query} ou {@code url} forem
     *                                  inválidos segundo as regras do domínio
     * @throws RuntimeException se a persistência ou o agendamento falharem
     */
    UUID submitJob(String query, String url, JobOptions options);

    /**
     * Recupera o estado atual do job, se existir.
     *
     * @param jobId identificador do job
     * @return o agregado {@link CrawlJob} se encontrado; vazio caso contrário
     * @throws IllegalArgumentException se {@code jobId} for {@code null}
     * @throws RuntimeException se a leitura do armazenamento falhar
     */
    Optional<CrawlJob> getStatus(UUID jobId);

    /**
     * Gera o conteúdo CSV do log de execução do job, quando disponível.
     *
     * @param jobId identificador do job
     * @return corpo CSV como texto, ou vazio se o job não existir ou não
     *         houver dados exportáveis
     * @throws IllegalArgumentException se {@code jobId} for {@code null}
     * @throws RuntimeException se a montagem ou leitura dos dados falhar
     */
    Optional<String> exportCsv(UUID jobId);
}
