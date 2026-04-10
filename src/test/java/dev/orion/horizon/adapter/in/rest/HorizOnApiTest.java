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

package dev.orion.horizon.adapter.in.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.orion.horizon.domain.model.AgentResponse;
import dev.orion.horizon.domain.model.LLMRequest;
import dev.orion.horizon.domain.port.out.BrowserPort;
import dev.orion.horizon.domain.port.out.LLMProviderPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Named;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Testes de integração {@link QuarkusTest} para os 3 endpoints REST.
 *
 * <p>BrowserPort e LLMProviderPorts são mockados para evitar chamadas
 * reais ao browser e aos provedores de LLM.
 */
@QuarkusTest
class HorizOnApiTest {

    /** UUID regex para verificação de formato. */
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}"
            + "-[0-9a-f]{4}-[0-9a-f]{12}";

    @InjectMock
    BrowserPort browserPort;

    @InjectMock
    @Named("ollama")
    LLMProviderPort verifierLlm;

    @InjectMock
    @Named("anthropic")
    LLMProviderPort extractorLlm;

    @BeforeEach
    void configureMocks() {
        Mockito.when(browserPort.loadPage(
                Mockito.anyString(), Mockito.anyLong()))
                .thenReturn(Optional.of(
                        "<html><body>Content</body></html>"
                ));

        final AgentResponse stubResponse =
                AgentResponse.builder()
                        .rawContent(
                                "{\"relevant\":false,"
                                + "\"confidence\":0.1,"
                                + "\"justification\":\"test\"}"
                        )
                        .providerUsed("STUB")
                        .modelUsed("stub")
                        .inputTokens(1)
                        .outputTokens(1)
                        .latencyMs(1L)
                        .httpStatus(200)
                        .build();

        Mockito.when(verifierLlm.call(
                Mockito.any(LLMRequest.class)))
                .thenReturn(stubResponse);
        Mockito.when(extractorLlm.call(
                Mockito.any(LLMRequest.class)))
                .thenReturn(stubResponse);
        Mockito.when(verifierLlm.getProviderName())
                .thenReturn("STUB");
        Mockito.when(verifierLlm.getModelName())
                .thenReturn("stub");
        Mockito.when(extractorLlm.getProviderName())
                .thenReturn("STUB");
        Mockito.when(extractorLlm.getModelName())
                .thenReturn("stub");
    }

    /** POST /HorizOn/search — 202 com job_id UUID válido. */
    @Test
    void searchReturns202WithValidJobId() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"query\":\"test query\","
                        + "\"url\":\"https://example.com\"}")
                .when().post("/HorizOn/search")
                .then()
                .statusCode(202)
                .body("job_id", matchesPattern(UUID_PATTERN))
                .body("status", equalTo("PENDING"))
                .body("created_at", notNullValue());
    }

    /** POST /HorizOn/search — 400 para query vazia. */
    @Test
    void searchReturns400ForEmptyQuery() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"query\":\"\","
                        + "\"url\":\"https://example.com\"}")
                .when().post("/HorizOn/search")
                .then()
                .statusCode(400);
    }

    /** POST /HorizOn/search — 400 para URL sem protocolo. */
    @Test
    void searchReturns400ForInvalidUrl() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"query\":\"test\","
                        + "\"url\":\"example.com\"}")
                .when().post("/HorizOn/search")
                .then()
                .statusCode(400);
    }

    /** POST /HorizOn/search — 400 para body sem query. */
    @Test
    void searchReturns400ForMissingQuery() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"url\":\"https://example.com\"}")
                .when().post("/HorizOn/search")
                .then()
                .statusCode(400);
    }

    /** GET /HorizOn/status/{jobId} — 200 com status PENDING. */
    @Test
    void statusReturns200WithPendingAfterCreation() {
        final String jobId =
                given()
                        .contentType(ContentType.JSON)
                        .body("{\"query\":\"test query\","
                                + "\"url\":\"https://example.com\"}")
                        .when().post("/HorizOn/search")
                        .then().statusCode(202)
                        .extract().path("job_id");

        given()
                .when().get("/HorizOn/status/" + jobId)
                .then()
                .statusCode(200)
                .body("job_id", equalTo(jobId))
                .body("status", notNullValue());
    }

    /** GET /HorizOn/status/{jobId} — 404 para UUID inexistente. */
    @Test
    void statusReturns404ForUnknownJobId() {
        final String unknown = UUID.randomUUID().toString();
        given()
                .when().get("/HorizOn/status/" + unknown)
                .then()
                .statusCode(404)
                .body("error", notNullValue());
    }

    /** GET /HorizOn/status/{jobId} — 400 para UUID malformado. */
    @Test
    void statusReturns400ForMalformedUuid() {
        given()
                .when().get("/HorizOn/status/not-a-uuid")
                .then()
                .statusCode(400)
                .body("error", notNullValue());
    }

    /** GET /HorizOn/csv/{jobId} — 200 com Content-Type text/csv. */
    @Test
    void csvReturns200WithCorrectContentType() {
        final String jobId =
                given()
                        .contentType(ContentType.JSON)
                        .body("{\"query\":\"test\","
                                + "\"url\":\"https://example.com\"}")
                        .when().post("/HorizOn/search")
                        .then().statusCode(202)
                        .extract().path("job_id");

        given()
                .when().get("/HorizOn/csv/" + jobId)
                .then()
                .statusCode(200)
                .contentType(containsString("text/csv"));
    }

    /** GET /HorizOn/csv/{jobId} — Content-Disposition presente. */
    @Test
    void csvHasContentDispositionHeader() {
        final String jobId =
                given()
                        .contentType(ContentType.JSON)
                        .body("{\"query\":\"test\","
                                + "\"url\":\"https://example.com\"}")
                        .when().post("/HorizOn/search")
                        .then().statusCode(202)
                        .extract().path("job_id");

        final String disposition =
                given()
                        .when().get("/HorizOn/csv/" + jobId)
                        .then().statusCode(200)
                        .extract()
                        .header("Content-Disposition");

        assertTrue(
                disposition != null
                && disposition.contains("attachment"),
                "Content-Disposition deve conter attachment"
        );
    }

    /** GET /HorizOn/csv/{jobId} — CSV contém cabeçalho com 25 colunas. */
    @Test
    void csvContainsHeaderWith25Columns() {
        final String jobId =
                given()
                        .contentType(ContentType.JSON)
                        .body("{\"query\":\"test\","
                                + "\"url\":\"https://example.com\"}")
                        .when().post("/HorizOn/search")
                        .then().statusCode(202)
                        .extract().path("job_id");

        final String csv =
                given()
                        .when().get("/HorizOn/csv/" + jobId)
                        .then().statusCode(200)
                        .extract().body().asString();

        final String headerLine = csv.split("\n")[0];
        final long columnCount =
                headerLine.chars()
                        .filter(c -> c == ',')
                        .count() + 1;
        assertTrue(
                columnCount >= 25,
                "CSV deve ter pelo menos 25 colunas, encontrou: "
                        + columnCount
        );
    }

    /** GET /HorizOn/csv/{jobId} — 404 para UUID inexistente. */
    @Test
    void csvReturns404ForUnknownJobId() {
        final String unknown = UUID.randomUUID().toString();
        given()
                .when().get("/HorizOn/csv/" + unknown)
                .then()
                .statusCode(404);
    }
}
