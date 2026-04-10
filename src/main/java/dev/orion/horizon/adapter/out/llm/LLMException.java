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

package dev.orion.horizon.adapter.out.llm;

/**
 * Falha ao chamar um provedor LLM (HTTP não 429, timeout, corpo inválido).
 */
public class LLMException extends RuntimeException {

    private final Integer httpStatus;

    /**
     * @param message descrição da falha
     */
    public LLMException(final String message) {
        this(message, null, null);
    }

    /**
     * @param message descrição da falha
     * @param httpStatus status HTTP quando aplicável
     */
    public LLMException(final String message, final Integer httpStatus) {
        this(message, httpStatus, null);
    }

    /**
     * @param message descrição da falha
     * @param cause causa original
     */
    public LLMException(final String message, final Throwable cause) {
        this(message, null, cause);
    }

    /**
     * @param message descrição da falha
     * @param httpStatus status HTTP quando aplicável
     * @param cause causa original
     */
    public LLMException(
            final String message,
            final Integer httpStatus,
            final Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /**
     * @return status HTTP da resposta do provedor, ou {@code null}
     */
    public Integer getHttpStatus() {
        return httpStatus;
    }
}
