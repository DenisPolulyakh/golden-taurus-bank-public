package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сундук с реквизитами карт: сервер хранит непрозрачную строку и следит только
 * за версией.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Интеграционные тесты реквизитов карт")
class CardRequisitesIntegrationTest extends CreditCardTestBase {

    private static final String PAYLOAD = "eyJ2IjoxfQ==.first-encrypted-blob";
    private static final String SECOND_PAYLOAD = "eyJ2IjoxfQ==.second-encrypted-blob";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Сундука нет — приходит пустой ответ, а не ошибка")
    void emptyWhenNothingSaved() throws Exception {
        JsonNode data = loadRequisites(accessToken);

        assertThat(isNull(data.get("payload"))).isTrue();
        assertThat(isNull(data.get("version"))).isTrue();
    }

    @Test
    @DisplayName("Первое сохранение создаёт сундук с нулевой версией")
    void firstSaveCreatesVault() throws Exception {
        saveRequisites(accessToken, PAYLOAD, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.payload").value(PAYLOAD));
    }

    @Test
    @DisplayName("Сундук возвращается ровно таким, каким его сохранили")
    void payloadReturnedAsIs() throws Exception {
        saveRequisites(accessToken, PAYLOAD, null).andExpect(status().isOk());

        JsonNode data = loadRequisites(accessToken);

        assertThat(data.get("payload").asText()).isEqualTo(PAYLOAD);
        assertThat(data.get("version").asLong()).isZero();
    }

    @Test
    @DisplayName("Сохранение с актуальной версией обновляет сундук и поднимает версию")
    void saveWithCurrentVersionUpdates() throws Exception {
        saveRequisites(accessToken, PAYLOAD, null).andExpect(status().isOk());

        saveRequisites(accessToken, SECOND_PAYLOAD, 0L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.payload").value(SECOND_PAYLOAD));
    }

    @Test
    @DisplayName("Сохранение с устаревшей версией не проходит и ничего не портит")
    void staleVersionRejected() throws Exception {
        saveRequisites(accessToken, PAYLOAD, null).andExpect(status().isOk());
        saveRequisites(accessToken, SECOND_PAYLOAD, 0L).andExpect(status().isOk());

        saveRequisites(accessToken, "третья попытка", 0L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4040));

        assertThat(loadRequisites(accessToken).get("payload").asText()).isEqualTo(SECOND_PAYLOAD);
    }

    @Test
    @DisplayName("Сохранение без версии поверх существующего сундука не проходит")
    void missingVersionRejectedWhenVaultExists() throws Exception {
        saveRequisites(accessToken, PAYLOAD, null).andExpect(status().isOk());

        saveRequisites(accessToken, SECOND_PAYLOAD, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4040));

        assertThat(loadRequisites(accessToken).get("payload").asText()).isEqualTo(PAYLOAD);
    }

    @Test
    @DisplayName("Чужой сундук не виден: у второго пользователя свой, пустой")
    void vaultIsNotSharedBetweenUsers() throws Exception {
        saveRequisites(accessToken, PAYLOAD, null).andExpect(status().isOk());

        String otherToken = registerAndLogin("other-requisites@example.com", "Other User");

        assertThat(isNull(loadRequisites(otherToken).get("payload"))).isTrue();
    }

    @Test
    @DisplayName("Без токена сундук недоступен ни на чтение, ни на запись")
    void withoutTokenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/card-requisites"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(3002));

        mockMvc.perform(put("/api/card-requisites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\": \"что-нибудь\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(3002));
    }

    @Test
    @DisplayName("Пустой сундук сохранить нельзя")
    void blankPayloadRejected() throws Exception {
        saveRequisites(accessToken, "   ", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @DisplayName("Сундук больше мегабайта сохранить нельзя")
    void tooLargePayloadRejected() throws Exception {
        saveRequisites(accessToken, "x".repeat(1_048_577), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @DisplayName("У пользователя всегда ровно один сундук, сколько бы раз он ни сохранял")
    void singleVaultPerUser() throws Exception {
        saveRequisites(accessToken, PAYLOAD, null).andExpect(status().isOk());
        saveRequisites(accessToken, SECOND_PAYLOAD, 0L).andExpect(status().isOk());

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM taurus.card_requisites", Integer.class);

        assertThat(rows).isEqualTo(1);
    }

    private JsonNode loadRequisites(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/card-requisites")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private ResultActions saveRequisites(String token, String payload, Long version) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        if (payload != null) {
            body.put("payload", payload);
        }
        if (version != null) {
            body.put("version", version);
        }

        return mockMvc.perform(put("/api/card-requisites")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    private boolean isNull(JsonNode node) {
        return node == null || node.isNull();
    }
}
