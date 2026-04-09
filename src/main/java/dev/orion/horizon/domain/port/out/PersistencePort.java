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

import dev.orion.horizon.domain.model.AgentCallLog;
import dev.orion.horizon.domain.model.CrawlJob;
import dev.orion.horizon.domain.model.CrawlResult;
import dev.orion.horizon.domain.model.LinkCandidate;
import dev.orion.horizon.domain.model.PageNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída para persistência do estado do crawl e exportação CSV.
 */
public interface PersistencePort {

    /**
     * Persiste um job recém-criado.
     *
     * @param job agregado a gravar; não deve ser {@code null}
     * @throws IllegalArgumentException se {@code job} for inválido para
     *                                  persistência
     * @throws RuntimeException se a gravação falhar
     */
    void saveJob(CrawlJob job);

    /**
     * Atualiza o job existente com o estado fornecido.
     *
     * @param job agregado a atualizar; não deve ser {@code null}
     * @throws IllegalArgumentException se {@code job} for inválido ou
     *                                  inexistente
     * @throws RuntimeException se a atualização falhar
     */
    void updateJob(CrawlJob job);

    /**
     * Registra uma visita a página no contexto do job.
     *
     * @param jobId identificador do job
     * @param node nó visitado; não deve ser {@code null}
     * @throws IllegalArgumentException se {@code jobId} ou {@code node} forem
     *                                  inválidos
     * @throws RuntimeException se a gravação falhar
     */
    void savePageVisit(UUID jobId, PageNode node);

    /**
     * Registra um candidato a link extraído a partir de uma origem.
     *
     * @param jobId identificador do job
     * @param sourceUrl URL da página onde o link foi encontrado
     * @param link candidato; não deve ser {@code null}
     * @throws IllegalArgumentException se algum parâmetro obrigatório for
     *                                  inválido
     * @throws RuntimeException se a gravação falhar
     */
    void saveLinkCandidate(UUID jobId, String sourceUrl, LinkCandidate link);

    /**
     * Persiste um resultado de crawl associado ao job.
     *
     * @param jobId identificador do job
     * @param result resultado; não deve ser {@code null}
     * @throws IllegalArgumentException se {@code jobId} ou {@code result}
     *                                  forem inválidos
     * @throws RuntimeException se a gravação falhar
     */
    void saveCrawlResult(UUID jobId, CrawlResult result);

    /**
     * Registra o log de uma chamada a agente/LLM.
     *
     * @param jobId identificador do job
     * @param log registro da chamada; não deve ser {@code null}
     * @throws IllegalArgumentException se {@code jobId} ou {@code log} forem
     *                                  inválidos
     * @throws RuntimeException se a gravação falhar
     */
    void saveAgentCall(UUID jobId, AgentCallLog log);

    /**
     * Busca um job pelo identificador.
     *
     * @param jobId identificador do job
     * @return o job se existir; vazio caso contrário
     * @throws IllegalArgumentException se {@code jobId} for {@code null}
     * @throws RuntimeException se a consulta ao armazenamento falhar
     */
    Optional<CrawlJob> findJobById(UUID jobId);

    /**
     * Monta as linhas do CSV de exportação para o job (cabeçalho incluído ou
     * não, conforme convenção do adaptador).
     *
     * @param jobId identificador do job
     * @return lista de linhas, cada uma como array de colunas; pode ser vazia
     *         se não houver dados
     * @throws IllegalArgumentException se {@code jobId} for {@code null}
     * @throws RuntimeException se a leitura ou agregação falhar
     */
    List<String[]> exportJobAsCsvRows(UUID jobId);
}
