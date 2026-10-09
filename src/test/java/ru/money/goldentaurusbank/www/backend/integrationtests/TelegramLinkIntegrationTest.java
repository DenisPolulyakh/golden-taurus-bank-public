package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.service.telegram.TelegramLinkService;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Привязка телеграм-чатов к аккаунтам по одноразовому коду.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Интеграционные тесты привязки телеграма")
class TelegramLinkIntegrationTest extends CreditCardTestBase {

    private static final Long CHAT = 500L;

    @Autowired
    private TelegramLinkService telegramLinkService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Код выдаётся авторизованному и живёт пятнадцать минут")
    void codeIsIssued() throws Exception {
        JsonNode data = createCode(accessToken);

        assertThat(data.get("code").asText()).hasSize(8);
        // Время приходит с поясом: без него браузер в другом поясе считал код просроченным
        assertThat(OffsetDateTime.parse(data.get("expiresAt").asText()))
                .isAfter(OffsetDateTime.now().plusMinutes(13))
                .isBefore(OffsetDateTime.now().plusMinutes(17));
    }

    @Test
    @DisplayName("Код привязывает чат к тому, кто его получил")
    void codeLinksChatToItsOwner() throws Exception {
        String code = createCode(accessToken).get("code").asText();

        telegramLinkService.linkChat(code, CHAT);

        assertThat(telegramLinkService.userByChat(CHAT).map(User::getEmail)).contains(EMAIL);
    }

    @Test
    @DisplayName("Использованный код второй раз не срабатывает")
    void usedCodeIsRejected() throws Exception {
        String code = createCode(accessToken).get("code").asText();
        telegramLinkService.linkChat(code, CHAT);

        assertThatThrownBy(() -> telegramLinkService.linkChat(code, 501L))
                .isInstanceOf(ApplicationException.class);
    }

    @Test
    @DisplayName("Просроченный код не срабатывает")
    void expiredCodeIsRejected() throws Exception {
        String code = createCode(accessToken).get("code").asText();
        jdbcTemplate.update("UPDATE taurus.telegram_link_codes SET expires_at = ? WHERE code = ?",
                LocalDateTime.now().minusMinutes(1), code);

        assertThatThrownBy(() -> telegramLinkService.linkChat(code, CHAT))
                .isInstanceOf(ApplicationException.class);
    }

    @Test
    @DisplayName("Неверный код не срабатывает")
    void unknownCodeIsRejected() {
        assertThatThrownBy(() -> telegramLinkService.linkChat("ZZZZZZZZ", CHAT))
                .isInstanceOf(ApplicationException.class);
    }

    @Test
    @DisplayName("Чат, привязанный к другому аккаунту, переходит к новому владельцу")
    void chatMovesToNewOwner() throws Exception {
        telegramLinkService.linkChat(createCode(accessToken).get("code").asText(), CHAT);

        String otherToken = registerAndLogin("telegram-other@example.com", "Other User");
        telegramLinkService.linkChat(createCode(otherToken).get("code").asText(), CHAT);

        assertThat(telegramLinkService.userByChat(CHAT).map(User::getEmail))
                .contains("telegram-other@example.com");
        assertThat(links(accessToken)).isEmpty();
        assertThat(links(otherToken)).hasSize(1);
    }

    @Test
    @DisplayName("В списке привязок видны только свои чаты")
    void linksArePerUser() throws Exception {
        telegramLinkService.linkChat(createCode(accessToken).get("code").asText(), CHAT);

        String otherToken = registerAndLogin("telegram-third@example.com", "Third User");

        assertThat(links(accessToken)).hasSize(1);
        assertThat(links(otherToken)).isEmpty();
    }

    @Test
    @DisplayName("Чужую привязку удалить нельзя")
    void foreignLinkCannotBeRemoved() throws Exception {
        telegramLinkService.linkChat(createCode(accessToken).get("code").asText(), CHAT);
        Long linkId = links(accessToken).get(0).get("id").asLong();

        String otherToken = registerAndLogin("telegram-stranger@example.com", "Stranger");

        mockMvc.perform(delete("/api/telegram/links/{linkId}", linkId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.code").value(4042));

        assertThat(telegramLinkService.userByChat(CHAT)).isPresent();
    }

    @Test
    @DisplayName("Свою привязку удалить можно")
    void ownLinkIsRemoved() throws Exception {
        telegramLinkService.linkChat(createCode(accessToken).get("code").asText(), CHAT);
        Long linkId = links(accessToken).get(0).get("id").asLong();

        mockMvc.perform(delete("/api/telegram/links/{linkId}", linkId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertThat(telegramLinkService.userByChat(CHAT)).isEmpty();
    }

    @Test
    @DisplayName("Без токена код не выдаётся")
    void withoutTokenUnauthorized() throws Exception {
        mockMvc.perform(post("/api/telegram/link-code"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(3002));
    }

    private JsonNode createCode(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/telegram/link-code")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private JsonNode links(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/telegram/links")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
