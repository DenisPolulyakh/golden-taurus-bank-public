package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import ru.money.goldentaurusbank.www.backend.service.telegram.TelegramLinkService;
import ru.money.goldentaurusbank.www.backend.service.telegram.TelegramReminderService;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Напоминания о платежах: кому, когда и по каким картам.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Интеграционные тесты напоминаний телеграм-бота")
class TelegramReminderIntegrationTest extends CreditCardTestBase {

    private static final LocalDate TODAY = LocalDate.now();
    private static final Long CHAT = 700L;

    @Autowired
    private TelegramReminderService reminderService;

    @Autowired
    private TelegramLinkService telegramLinkService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("За семь дней до платежа уходит напоминание")
    void remindsSevenDaysBefore() throws Exception {
        linkChat();
        Long cardId = card("Платинум", "4567", "200000", TODAY.plusDays(7));

        reminderService.run(TODAY);

        assertThat(kinds(cardId)).containsExactly("BEFORE_7");
    }

    @Test
    @DisplayName("За два дня до платежа уходит напоминание")
    void remindsTwoDaysBefore() throws Exception {
        linkChat();
        Long cardId = card("Платинум", "4567", "200000", TODAY.plusDays(2));

        reminderService.run(TODAY);

        assertThat(kinds(cardId)).containsExactly("BEFORE_2");
    }

    @Test
    @DisplayName("За день до платежа уходит последнее напоминание")
    void remindsOneDayBefore() throws Exception {
        linkChat();
        Long cardId = card("Платинум", "4567", "200000", TODAY.plusDays(1));

        reminderService.run(TODAY);

        assertThat(kinds(cardId)).containsExactly("BEFORE_1");
    }

    @Test
    @DisplayName("Повторный запуск в тот же день дубля не делает")
    void doesNotRepeatSameDay() throws Exception {
        linkChat();
        Long cardId = card("Платинум", "4567", "200000", TODAY.plusDays(7));

        reminderService.run(TODAY);
        reminderService.run(TODAY);

        assertThat(kinds(cardId)).containsExactly("BEFORE_7");
    }

    @Test
    @DisplayName("Карта с нулевым долгом не напоминает")
    void zeroDebtIsSilent() throws Exception {
        linkChat();
        Long cardId = card("Пустая", "1111", "0", TODAY.plusDays(7));

        reminderService.run(TODAY);

        assertThat(kinds(cardId)).isEmpty();
    }

    @Test
    @DisplayName("Карта без даты платежа не напоминает в обычный день")
    void withoutDateIsSilent() throws Exception {
        linkChat();
        Long cardId = card("Без даты", "2222", "200000", null);

        reminderService.run(TODAY.withDayOfMonth(15));

        assertThat(kinds(cardId)).isEmpty();
    }

    @Test
    @DisplayName("Первого числа приходит список карт без даты платежа")
    void missingDatesOnFirstDay() throws Exception {
        linkChat();
        Long cardId = card("Без даты", "2222", "200000", null);

        reminderService.run(TODAY.withDayOfMonth(1));

        assertThat(kinds(cardId)).containsExactly("NO_DATE");
    }

    @Test
    @DisplayName("Архивная карта не напоминает")
    void archivedIsSilent() throws Exception {
        linkChat();
        Long cardId = card("Старая", "3333", "200000", TODAY.plusDays(7));
        deleteCard(cardId).andExpect(status().isOk());

        reminderService.run(TODAY);

        assertThat(kinds(cardId)).isEmpty();
    }

    @Test
    @DisplayName("Без привязанных чатов напоминания не формируются")
    void noChatsNoReminders() throws Exception {
        Long cardId = card("Платинум", "4567", "200000", TODAY.plusDays(7));

        reminderService.run(TODAY);

        assertThat(kinds(cardId)).isEmpty();
    }

    @Test
    @DisplayName("После пропущенной даты без погашений приходит напоминание")
    void overdueWithoutRepayment() throws Exception {
        linkChat();
        Long cardId = card("Платинум", "4567", "200000", TODAY.minusDays(1));

        reminderService.run(TODAY);

        assertThat(kinds(cardId)).containsExactly("OVERDUE");
    }

    @Test
    @DisplayName("Если погашение внесено после даты платежа, напоминания нет")
    void overdueSilentAfterRepayment() throws Exception {
        linkChat();
        Long cardId = card("Платинум", "4567", "200000", TODAY.minusDays(1));
        repay(cardId, "1000").andExpect(status().isOk());

        reminderService.run(TODAY);

        assertThat(kinds(cardId)).isEmpty();
    }

    @Test
    @DisplayName("Месячный пакет уходит в привязанный чат")
    void monthlyPackageIsSent() throws Exception {
        linkChat();
        card("Платинум", "4567", "200000", TODAY.plusDays(7));

        assertThat(reminderService.sendMonthlyPackages()).isEqualTo(1);
    }

    @Test
    @DisplayName("Без привязанных чатов месячный пакет никуда не уходит")
    void monthlyPackageNeedsChat() throws Exception {
        card("Платинум", "4567", "200000", TODAY.plusDays(7));

        assertThat(reminderService.sendMonthlyPackages()).isZero();
    }

    @Test
    @DisplayName("Если чатов два, пакет уходит в оба")
    void monthlyPackageGoesToEveryChat() throws Exception {
        linkChat();
        linkSecondChat();

        assertThat(reminderService.sendMonthlyPackages()).isEqualTo(2);
    }

    private void linkSecondChat() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/telegram/link-code")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        telegramLinkService.linkChat(data.get("code").asText(), CHAT + 1);
    }

    private void linkChat() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/telegram/link-code")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        telegramLinkService.linkChat(data.get("code").asText(), CHAT);
    }

    private Long card(String name, String last4, String debt, LocalDate grace) throws Exception {
        StringBuilder body = new StringBuilder("{\"name\": \"%s\", \"last4\": \"%s\", \"limit\": 500000, \"debt\": %s"
                .formatted(name, last4, debt));
        if (grace != null) {
            body.append(", \"gracePeriodDate\": \"%s\"".formatted(grace));
        }
        body.append('}');

        MvcResult result = mockMvc.perform(post("/api/credit-cards")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andReturn();

        return dataId(result);
    }

    private List<String> kinds(Long cardId) {
        return jdbcTemplate.queryForList(
                "SELECT kind FROM taurus.telegram_reminders_sent WHERE credit_card_id = ? ORDER BY id",
                String.class, cardId);
    }
}
