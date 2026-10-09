package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Аварийный пакет: долги открытым текстом плюс запертый сундук.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Интеграционные тесты аварийного пакета")
class EmergencyPackageIntegrationTest extends CreditCardTestBase {

    private static final String PAYLOAD = "eyJ2IjoxfQ==.encrypted-vault";

    @Test
    @DisplayName("В пакете есть дата, карта с долгом и запертый сундук")
    void packageContainsCardsAndVault() throws Exception {
        Long cardId = createCard("Платинум", "1234", "100000", "25000");
        saveVault(accessToken, PAYLOAD, null);

        JsonNode data = loadPackage(accessToken);

        assertThat(data.get("generatedAt").asText()).isNotBlank();
        assertThat(data.get("vault").get("payload").asText()).isEqualTo(PAYLOAD);

        JsonNode card = data.get("cards").get(0);
        assertThat(card.get("cardId").asLong()).isEqualTo(cardId);
        assertThat(card.get("name").asText()).isEqualTo("Платинум");
        assertThat(card.get("last4").asText()).isEqualTo("1234");
        assertThat(card.get("debt").decimalValue()).isEqualByComparingTo("25000");
        assertThat(card.get("limit").decimalValue()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("Сундук не заведён — пакет всё равно приходит, долги на месте")
    void packageWorksWithoutVault() throws Exception {
        createCard("Без реквизитов", "5555", "50000", "1000");

        JsonNode data = loadPackage(accessToken);

        assertThat(data.get("cards")).hasSize(1);
        assertThat(data.get("vault").get("payload").isNull()).isTrue();
    }

    @Test
    @DisplayName("Архивные карты в пакет не попадают")
    void archivedCardsAreSkipped() throws Exception {
        Long archived = createCard("Старая", "1111", "10000", "5000");
        createCard("Активная", "2222", "20000", "0");

        deleteCard(archived).andExpect(status().isOk());

        JsonNode cards = loadPackage(accessToken).get("cards");

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).get("name").asText()).isEqualTo("Активная");
    }

    @Test
    @DisplayName("Чужие карты и чужой сундук в пакет не попадают")
    void packageIsPerUser() throws Exception {
        createCard("Моя карта", "1234", "100000", "0");
        saveVault(accessToken, PAYLOAD, null);

        String otherToken = registerAndLogin("other-package@example.com", "Other User");

        JsonNode data = loadPackage(otherToken);

        assertThat(data.get("cards")).isEmpty();
        assertThat(data.get("vault").get("payload").isNull()).isTrue();
    }

    @Test
    @DisplayName("В пакете нет расшифрованных реквизитов — только сундук строкой")
    void packageCarriesNoPlainRequisites() throws Exception {
        createCard("Платинум", "1234", "100000", "0");
        saveVault(accessToken, PAYLOAD, null);

        MvcResult result = mockMvc.perform(get("/api/card-requisites/emergency-package")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();

        assertThat(body).doesNotContain("\"pan\"", "\"account\"", "\"bic\"", "\"receiver\"");
    }

    @Test
    @DisplayName("Без токена пакет не отдаётся")
    void withoutTokenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/card-requisites/emergency-package"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(3002));
    }

    private JsonNode loadPackage(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/card-requisites/emergency-package")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private void saveVault(String token, String payload, Long version) throws Exception {
        String body = version == null
                ? "{\"payload\": \"%s\"}".formatted(payload)
                : "{\"payload\": \"%s\", \"version\": %d}".formatted(payload, version);

        mockMvc.perform(put("/api/card-requisites")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
