package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Отчёт «Бюджет на месяц».
 *
 * <p>Главное, что здесь проверяется — тождество
 * {@code остаток = остаток на 1-е + движения − траты}. Оно должно выполняться
 * при любом составе операций, потому что каждая операция попадает ровно в одну
 * корзину и с тем же знаком, с каким подвинула слиток. Поэтому почти каждый
 * тест заканчивается сверкой {@code discrepancy = 0} и совпадением остатка
 * бюджета с фактической суммой слитка: если модель врёт, ломается именно это.
 *
 * <p>Все суммы взяты из реального августа Дениса в Excel, чтобы цифры в тестах
 * читались как история, а не как случайный набор.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Интеграционные тесты бюджета на месяц")
class BudgetIntegrationTest extends IntegrationTestBase {

    private static final int YEAR = 2026;
    private static final int MONTH = 8;

    /**
     * Откат пишет обратную операцию датой «сейчас», а не датой оригинала — так
     * устроена вся история приложения. Поэтому тесты отката живут в текущем
     * месяце: только там операция и её откат попадают в один отчёт.
     */
    private static final YearMonth CURRENT = YearMonth.now();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private String accessToken;
    private Long vaultId;
    private Long walletId;   // «Расход Текущий» — бюджетный слиток
    private Long incomeId;   // «Доход текущий» — слиток-источник финансирования
    private Long healthId;   // «Здоровье Мария» — откуда приходят возмещения

    @BeforeEach
    void setUp() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "budget@example.com",
                            "password": "Test123%",
                            "fullName": "Budget User"
                        }
                        """));

        User user = userRepository.findByEmail("budget@example.com").get();
        mockMvc.perform(get("/api/auth/verify").param("token", user.getVerificationToken()));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "budget@example.com",
                                    "password": "Test123%"
                                }
                                """))
                .andReturn();
        accessToken = json(loginResult).get("data").get("token").asText();

        vaultId = createVault("Кешбокс");
        walletId = createBullion("Расход Текущий", "0");
        incomeId = createBullion("Доход текущий", "259490.00");
        healthId = createBullion("Здоровье Мария", "50000.00");

        setSettings(walletId, incomeId);
        setPlan(YEAR, MONTH, "100000.00");
        setPlan(CURRENT.getYear(), CURRENT.getMonthValue(), "100000.00");
    }

    // ------------------------------------------------------------------
    // Тождество
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Остаток бюджета сходится с суммой слитка при любом составе операций")
    void identityHolds() throws Exception {
        fundToPlan();
        withdraw(walletId, "12000.00", "2026-08-01T09:00:00", true);
        deposit(walletId, "4192.74", "2026-08-01T21:00:00", true);
        withdraw(walletId, "10000.00", "2026-08-02T09:00:00", true);
        deposit(walletId, "8402.76", "2026-08-02T21:00:00", true);
        transfer(healthId, walletId, "3000.00", "2026-08-02T22:00:00", true);
        transfer(incomeId, walletId, "15000.00", "2026-08-20T10:00:00", false);

        JsonNode report = month();

        assertEquals(0, amount(report, "discrepancy").signum());
        assertEquals(amount(report, "closingBalance"), amount(report, "bullionAmount"));
        assertAmount("115000.00", report, "funding");
        assertAmount("6404.50", report, "spent");
    }

    @Test
    @DisplayName("Снял утром, вернул вечером - трата дня равна разнице")
    void takenMinusReturnedIsDailySpend() throws Exception {
        fundToPlan();
        withdraw(walletId, "12000.00", "2026-08-01T09:00:00", true);
        deposit(walletId, "4192.74", "2026-08-01T21:00:00", true);

        JsonNode day = day(1);

        assertAmount("12000.00", day, "taken");
        assertAmount("4192.74", day, "returned");
        assertAmount("7807.26", day, "spent");
    }

    @Test
    @DisplayName("Возмещение переводом внутрь уменьшает трату дня, наружу - увеличивает")
    void compensationMovesDailySpend() throws Exception {
        fundToPlan();
        withdraw(walletId, "10000.00", "2026-08-05T09:00:00", true);
        transfer(healthId, walletId, "3000.00", "2026-08-05T20:00:00", true);
        transfer(walletId, healthId, "500.00", "2026-08-06T20:00:00", true);

        assertAmount("3000.00", day(5), "compensated");
        assertAmount("7000.00", day(5), "spent");
        assertAmount("-500.00", day(6), "compensated");
        assertAmount("500.00", day(6), "spent");
    }

    @Test
    @DisplayName("Финансирование не попадает в траты")
    void fundingIsNotSpending() throws Exception {
        fundToPlan();

        JsonNode report = month();

        assertAmount("100000.00", report, "funding");
        assertAmount("0.00", report, "spent");
        assertAmount("100000.00", report, "available");
        assertAmount("100000.00", day(1), "funding");
        assertAmount("0.00", day(1), "spent");
    }

    @Test
    @DisplayName("Умолчание корзины зависит от типа: наличные - трата, перевод - движение бюджета")
    void defaultBucketDependsOnOperationType() throws Exception {
        fundToPlan();
        withdrawWithoutFlag(walletId, "10000.00", "2026-08-07T09:00:00");
        transferWithoutFlag(incomeId, walletId, "33745.32", "2026-08-07T10:00:00");

        JsonNode day = day(7);

        // Перевод внутрь без галочки — финансирование, а не возмещение. С прежним
        // умолчанием день показал бы «потрачено −23 745,32»
        assertAmount("0.00", day, "compensated");
        assertAmount("10000.00", day, "spent");
        assertAmount("33745.32", day, "funding");
    }

    // ------------------------------------------------------------------
    // Откат
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Откат траты возвращает день к нулю - корзина копируется в обратную операцию")
    void rollbackCopiesBucket() throws Exception {
        fundToPlan(CURRENT.getYear(), CURRENT.getMonthValue());
        Long transactionId = withdraw(walletId, "10000.00", at(10, 9), true);

        rollback(transactionId);

        JsonNode report = current();

        assertAmount("0.00", report, "spent");
        assertEquals(0, amount(report, "discrepancy").signum());
        assertEquals(amount(report, "closingBalance"), amount(report, "bullionAmount"));
    }

    @Test
    @DisplayName("Откат финансирования не считается тратой")
    void rollbackOfFundingStaysInFundingBucket() throws Exception {
        Long transactionId = fundToPlan(CURRENT.getYear(), CURRENT.getMonthValue());

        rollback(transactionId);

        JsonNode report = current();

        assertAmount("0.00", report, "spent");
        assertAmount("0.00", report, "funding");
        assertEquals(0, amount(report, "discrepancy").signum());
    }

    @Test
    @DisplayName("Пара операция плюс откат не раздувает валовые колонки дня")
    void reversedPairIsHiddenFromGrossColumns() throws Exception {
        fundToPlan(CURRENT.getYear(), CURRENT.getMonthValue());
        Long transactionId = withdraw(walletId, "35619.05", at(1, 9), true);
        withdraw(walletId, "5000.00", at(1, 10), true);

        rollback(transactionId);

        JsonNode day = currentDay(1);

        assertAmount("5000.00", day, "taken");
        assertAmount("0.00", day, "returned");
        assertAmount("5000.00", day, "spent");
    }

    @Test
    @DisplayName("Откат через границу месяца правит не прошлое, а сегодняшний день")
    void rollbackAcrossMonthBoundaryLandsToday() throws Exception {
        fundToPlan();
        Long transactionId = withdraw(walletId, "10000.00", "2026-08-10T09:00:00", true);

        mockMvc.perform(post("/api/budget/%d/%d/transactions/%d/rollback".formatted(YEAR, MONTH, transactionId))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Август остаётся как был: трата в нём случилась, отчёт месяца не
        // переписывается задним числом. Возврат виден датой отката.
        assertAmount("10000.00", month(), "spent");
        assertEquals(0, amount(month(), "discrepancy").signum());
        assertEquals(0, amount(current(), "discrepancy").signum());
    }

    @Test
    @DisplayName("Операция не по бюджетному слитку не откатывается из отчёта")
    void foreignTransactionIsRejected() throws Exception {
        Long transactionId = withdraw(healthId, "1000.00", "2026-08-03T09:00:00", true);

        mockMvc.perform(post("/api/budget/%d/%d/transactions/%d/rollback".formatted(YEAR, MONTH, transactionId))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4034));
    }

    // ------------------------------------------------------------------
    // Переразметка
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Переразметка перекладывает сумму между корзинами, не двигая слиток")
    void setBucketMovesMoneyBetweenBuckets() throws Exception {
        fundToPlan();
        Long transactionId = transfer(incomeId, walletId, "15000.00", "2026-08-20T10:00:00", true);

        assertAmount("-15000.00", day(20), "spent");
        BigDecimal amountBefore = amount(month(), "bullionAmount");

        mockMvc.perform(patch("/api/budget/transactions/%d/bucket".formatted(transactionId))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "budgetOperation": false }
                                """))
                .andExpect(status().isOk());

        JsonNode report = month();

        assertAmount("0.00", day(20), "spent");
        assertAmount("15000.00", day(20), "funding");
        assertAmount("115000.00", report, "funding");
        assertEquals(amountBefore, amount(report, "bullionAmount"));
        assertEquals(0, amount(report, "discrepancy").signum());
    }

    // ------------------------------------------------------------------
    // План, перерасход, переходящий остаток
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Докинуть можно и сверх плана - это видно отдельной строкой")
    void extraFundingIsVisible() throws Exception {
        fundToPlan();

        mockMvc.perform(post("/api/budget/%d/%d/fund".formatted(YEAR, MONTH))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "amount": 15000.00,
                                    "dateOperation": "2026-08-20T10:00:00"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode report = month();

        assertAmount("115000.00", report, "funding");
        assertAmount("15000.00", report, "extraFunding");
        assertAmount("15000.00", day(20), "funding");
        assertAmount("0.00", day(20), "spent");
    }

    @Test
    @DisplayName("Повторный добор до плана отклоняется, но с подсказкой доложить явной суммой")
    void secondTopUpToPlanIsRejected() throws Exception {
        fundToPlan();

        mockMvc.perform(post("/api/budget/%d/%d/fund".formatted(YEAR, MONTH))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4036));
    }

    @Test
    @DisplayName("Перерасход по плану считается даже без докидывания")
    void overspendWithoutExtraFunding() throws Exception {
        fundToPlan();
        transfer(incomeId, walletId, "20000.00", "2026-08-02T10:00:00", false);
        withdraw(walletId, "110000.00", "2026-08-15T09:00:00", true);

        JsonNode report = month();

        assertAmount("110000.00", report, "spent");
        assertAmount("10000.00", report, "overspend");
        assertEquals(0, amount(report, "discrepancy").signum());
    }

    @Test
    @DisplayName("Не закрыл месяц - остаток становится началом следующего")
    void remainderCarriesOverToNextMonth() throws Exception {
        fundToPlan();
        withdraw(walletId, "97460.61", "2026-08-15T09:00:00", true);

        JsonNode august = month();
        assertAmount("2539.39", august, "closingBalance");

        JsonNode september = month(YEAR, 9);
        assertAmount("2539.39", september, "openingBalance");
    }

    @Test
    @DisplayName("Закрытие месяца уводит остаток и обнуляет бюджет")
    void closeMonthMovesRemainder() throws Exception {
        fundToPlan();
        withdraw(walletId, "97460.61", "2026-08-15T09:00:00", true);

        mockMvc.perform(post("/api/budget/%d/%d/close".formatted(YEAR, MONTH))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "targetBullionId": %d }
                                """.formatted(incomeId)))
                .andExpect(status().isOk());

        JsonNode report = month();

        assertAmount("0.00", report, "closingBalance");
        assertEquals(0, amount(report, "discrepancy").signum());
        assertAmount("0.00", month(YEAR, 9), "openingBalance");
    }

    @Test
    @DisplayName("Закрытие пишет снимок плана, финансирования, трат и остатка на момент закрытия")
    void closeMonthWritesSnapshot() throws Exception {
        fundToPlan();
        withdraw(walletId, "97460.61", "2026-08-15T09:00:00", true);

        JsonNode report = close(YEAR, MONTH, incomeId);

        assertTrue(report.get("closed").asBoolean());
        assertFalse(report.get("snapshotMismatch").asBoolean());

        JsonNode snapshot = report.get("snapshot");
        assertAmount("100000.00", snapshot, "plannedAmount");
        assertAmount("100000.00", snapshot, "funding");
        assertAmount("97460.61", snapshot, "spent");
        assertAmount("2539.39", snapshot, "closingBalance");
        assertAmount("2539.39", snapshot, "remainderTransferred");
        assertEquals("Доход текущий", snapshot.get("remainderTargetBullion").get("title").asText());
    }

    @Test
    @DisplayName("Остатка нет - закрытие проходит без targetBullionId и без перевода")
    void closeMonthWithoutRemainderNeedsNoTarget() throws Exception {
        fundToPlan();
        withdraw(walletId, "100000.00", "2026-08-15T09:00:00", true);
        BigDecimal incomeBefore = bullionAmount(incomeId);

        JsonNode report = close(YEAR, MONTH, null);

        assertTrue(report.get("closed").asBoolean());
        JsonNode snapshot = report.get("snapshot");
        assertAmount("0.00", snapshot, "closingBalance");
        assertAmount("0.00", snapshot, "remainderTransferred");
        assertTrue(snapshot.get("remainderTargetBullion").isNull());
        assertEquals(0, incomeBefore.compareTo(bullionAmount(incomeId)));
    }

    @Test
    @DisplayName("План не задавали - в снимке он тоже пустой, а не ноль")
    void closeMonthWithoutPlanLeavesSnapshotPlanNull() throws Exception {
        // В setUp план задан на август и на текущий месяц (CURRENT) - берём
        // соседний месяц, но не CURRENT: тесты гоняются и в августе, и в
        // сентябре, и план CURRENT не должен туда случайно попасть.
        int noPlanMonth = MONTH == 12 ? 1 : MONTH + 1;
        if (CURRENT.getYear() == YEAR && CURRENT.getMonthValue() == noPlanMonth) {
            noPlanMonth = noPlanMonth == 12 ? 1 : noPlanMonth + 1;
        }
        String date = "2026-%02d-05T0%d:00:00";

        // Сначала финансирование, потом трата - иначе списывать не с чего.
        transfer(incomeId, walletId, "1000.00", date.formatted(noPlanMonth, 8), false);
        withdraw(walletId, "1000.00", date.formatted(noPlanMonth, 9), true);

        JsonNode report = close(YEAR, noPlanMonth, null);

        assertTrue(report.get("snapshot").get("plannedAmount").isNull());
    }

    @Test
    @DisplayName("Остаток положительный - без targetBullionId закрытие отклоняется")
    void closeMonthWithRemainderRequiresTarget() throws Exception {
        fundToPlan();
        withdraw(walletId, "97460.61", "2026-08-15T09:00:00", true);

        mockMvc.perform(post("/api/budget/%d/%d/close".formatted(YEAR, MONTH))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4039));
    }

    @Test
    @DisplayName("Повторное закрытие уже закрытого месяца отклоняется")
    void closingAlreadyClosedMonthIsRejected() throws Exception {
        close(YEAR, MONTH, null);

        mockMvc.perform(post("/api/budget/%d/%d/close".formatted(YEAR, MONTH))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4045));
    }

    // ------------------------------------------------------------------
    // Запреты в закрытом месяце
    // ------------------------------------------------------------------

    @Test
    @DisplayName("План закрытого месяца менять нельзя")
    void settingPlanOnClosedMonthIsRejected() throws Exception {
        close(YEAR, MONTH, null);

        mockMvc.perform(put("/api/budget/%d/%d/plan".formatted(YEAR, MONTH))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "plannedAmount": 50000.00 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4046));
    }

    @Test
    @DisplayName("Финансировать закрытый месяц нельзя")
    void fundingClosedMonthIsRejected() throws Exception {
        close(YEAR, MONTH, null);

        mockMvc.perform(post("/api/budget/%d/%d/fund".formatted(YEAR, MONTH))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4046));
    }

    @Test
    @DisplayName("Откатить операцию закрытого месяца нельзя")
    void rollbackInClosedMonthIsRejected() throws Exception {
        transfer(incomeId, walletId, "1000.00", "2026-08-05T08:00:00", false);
        Long transactionId = withdraw(walletId, "1000.00", "2026-08-05T09:00:00", true);
        close(YEAR, MONTH, null);

        mockMvc.perform(post("/api/budget/%d/%d/transactions/%d/rollback".formatted(YEAR, MONTH, transactionId))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4046));
    }

    @Test
    @DisplayName("Переразметить операцию закрытого месяца нельзя")
    void bucketChangeInClosedMonthIsRejected() throws Exception {
        transfer(incomeId, walletId, "1000.00", "2026-08-05T08:00:00", false);
        Long transactionId = withdraw(walletId, "1000.00", "2026-08-05T09:00:00", true);
        close(YEAR, MONTH, null);

        mockMvc.perform(patch("/api/budget/transactions/%d/bucket".formatted(transactionId))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "budgetOperation": false }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4046));
    }

    @Test
    @DisplayName("Закрытие одного месяца не мешает редактировать другой")
    void otherMonthsStayEditableAfterClose() throws Exception {
        close(YEAR, MONTH, null);

        mockMvc.perform(put("/api/budget/%d/%d/plan".formatted(YEAR, MONTH + 1))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "plannedAmount": 50000.00 }
                                """))
                .andExpect(status().isOk());

        fundToPlan(YEAR, MONTH + 1);

        assertAmount("50000.00", month(YEAR, MONTH + 1), "funding");
    }

    // ------------------------------------------------------------------
    // Переоткрытие
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Переоткрыть незакрытый месяц нельзя")
    void reopenUnclosedMonthIsRejected() throws Exception {
        mockMvc.perform(post("/api/budget/%d/%d/reopen".formatted(YEAR, MONTH))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4047));
    }

    @Test
    @DisplayName("Переоткрытие откатывает перевод остатка - слитки возвращаются к исходным суммам")
    void reopenRestoresRemainderTransfer() throws Exception {
        fundToPlan();
        withdraw(walletId, "97460.61", "2026-08-15T09:00:00", true);
        BigDecimal incomeBeforeClose = bullionAmount(incomeId);

        close(YEAR, MONTH, incomeId);

        assertEquals(0, new BigDecimal("0.00").compareTo(bullionAmount(walletId)));
        assertEquals(0, incomeBeforeClose.add(new BigDecimal("2539.39")).compareTo(bullionAmount(incomeId)));

        JsonNode report = reopen(YEAR, MONTH);

        assertFalse(report.get("closed").asBoolean());
        assertTrue(report.get("snapshot").isNull());
        assertEquals(0, new BigDecimal("2539.39").compareTo(bullionAmount(walletId)));
        assertEquals(0, incomeBeforeClose.compareTo(bullionAmount(incomeId)));
    }

    @Test
    @DisplayName("Переоткрытие без остатка просто стирает снимок, лишних операций не создаёт")
    void reopenWithoutRemainderJustClearsSnapshot() throws Exception {
        close(YEAR, MONTH, null);
        long transactionsBefore = transactionCount();

        JsonNode report = reopen(YEAR, MONTH);

        assertFalse(report.get("closed").asBoolean());
        assertEquals(transactionsBefore, transactionCount());
    }

    @Test
    @DisplayName("После переоткрытия план и финансирование снова редактируются, месяц можно закрыть заново")
    void reopenAllowsEditingAndReclosing() throws Exception {
        close(YEAR, MONTH, null);
        reopen(YEAR, MONTH);

        setPlan(YEAR, MONTH, "80000.00");
        fundToPlan();
        withdraw(walletId, "80000.00", "2026-08-15T09:00:00", true);

        JsonNode report = close(YEAR, MONTH, null);

        assertTrue(report.get("closed").asBoolean());
        assertAmount("80000.00", report.get("snapshot"), "plannedAmount");
    }

    // ------------------------------------------------------------------
    // Расхождение со снимком
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Смена бюджетного слитка после закрытия помечает расхождение со снимком")
    void changingBudgetBullionAfterCloseFlagsMismatch() throws Exception {
        fundToPlan();
        withdraw(walletId, "97460.61", "2026-08-15T09:00:00", true);
        close(YEAR, MONTH, incomeId);

        setSettings(healthId, incomeId);

        JsonNode report = month();
        assertTrue(report.get("closed").asBoolean());
        assertTrue(report.get("snapshotMismatch").asBoolean());
    }

    @Test
    @DisplayName("Пока ничего не поменяли, закрытый месяц не расходится со снимком")
    void snapshotMatchesWhenNothingChangedAfterClose() throws Exception {
        fundToPlan();
        withdraw(walletId, "97460.61", "2026-08-15T09:00:00", true);
        close(YEAR, MONTH, incomeId);

        assertFalse(month().get("snapshotMismatch").asBoolean());
    }

    // ------------------------------------------------------------------
    // Выбор слитка
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Бюджетным можно назначить любой слиток, отчёт переезжает целиком")
    void anyBullionCanBecomeBudget() throws Exception {
        fundToPlan();
        withdraw(walletId, "10000.00", "2026-08-04T09:00:00", true);
        withdraw(healthId, "2500.00", "2026-08-04T09:00:00", true);

        assertEquals("Расход Текущий", month().get("budgetBullion").get("title").asText());
        assertAmount("10000.00", day(4), "spent");

        setSettings(healthId, incomeId);

        assertEquals("Здоровье Мария", month().get("budgetBullion").get("title").asText());
        assertAmount("2500.00", day(4), "spent");
        assertEquals(0, amount(month(), "discrepancy").signum());
    }

    @Test
    @DisplayName("Слиток не выбран - пустой отчёт, а не ошибка")
    void reportWithoutBudgetBullion() throws Exception {
        setSettings(null, null);

        JsonNode report = month();

        assertTrue(report.get("budgetBullion").isNull());
        assertAmount("0.00", report, "spent");
        assertEquals(0, report.get("days").size());
    }

    @Test
    @DisplayName("Слиток-источник виден и до того, как выбран бюджетный слиток")
    void sourceBullionSurvivesWithoutBudgetBullion() throws Exception {
        setSettings(null, incomeId);

        JsonNode report = month();

        assertTrue(report.get("budgetBullion").isNull());
        assertEquals("Доход текущий", report.get("sourceBullion").get("title").asText());
    }

    @Test
    @DisplayName("Бюджетный слиток и источник финансирования не могут совпадать")
    void budgetAndSourceMustDiffer() throws Exception {
        mockMvc.perform(put("/api/budget/settings")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "budgetBullionId": %d,
                                    "sourceBullionId": %d
                                }
                                """.formatted(walletId, walletId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4033));
    }

    // ------------------------------------------------------------------
    // Таблица и год
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Строка есть на каждый день месяца, остаток идёт нарастающим итогом")
    void everyDayHasRow() throws Exception {
        fundToPlan();
        withdraw(walletId, "10000.00", "2026-08-02T09:00:00", true);

        JsonNode report = month();

        assertEquals(31, report.get("days").size());
        assertAmount("100000.00", day(1), "balance");
        assertAmount("90000.00", day(2), "balance");
        assertAmount("90000.00", day(3), "balance");
        assertEquals(amount(report, "closingBalance"), amount(day(31), "balance"));
    }

    @Test
    @DisplayName("Годовая сводка тянет остаток из месяца в месяц")
    void yearlyReportCarriesBalance() throws Exception {
        fundToPlan();
        withdraw(walletId, "97460.61", "2026-08-15T09:00:00", true);

        MvcResult result = mockMvc.perform(get("/api/budget/" + YEAR)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode report = json(result);

        assertEquals(12, report.get("months").size());
        JsonNode august = report.get("months").get(MONTH - 1);
        assertAmount("97460.61", august, "spent");
        assertAmount("100000.00", august, "funding");
        assertAmount("2539.39", august, "closingBalance");
        assertAmount("2539.39", report.get("months").get(MONTH), "closingBalance");
        assertAmount("0.00", report.get("months").get(0), "spent");
    }

    @Test
    @DisplayName("Годовая сводка помечает закрытые месяцы")
    void yearlyReportMarksClosedMonths() throws Exception {
        close(YEAR, MONTH, null);

        JsonNode report = year(YEAR);

        assertTrue(report.get("months").get(MONTH - 1).get("closed").asBoolean());
        assertFalse(report.get("months").get(MONTH).get("closed").asBoolean());
    }

    @Test
    @DisplayName("Годовая сводка помечает расхождение со снимком")
    void yearlyReportMarksSnapshotMismatch() throws Exception {
        fundToPlan();
        withdraw(walletId, "97460.61", "2026-08-15T09:00:00", true);
        close(YEAR, MONTH, incomeId);

        setSettings(healthId, incomeId);

        JsonNode report = year(YEAR);

        assertTrue(report.get("months").get(MONTH - 1).get("snapshotMismatch").asBoolean());
    }

    // ------------------------------------------------------------------
    // Вспомогательные методы
    // ------------------------------------------------------------------

    private JsonNode month() throws Exception {
        return month(YEAR, MONTH);
    }

    private JsonNode month(int year, int month) throws Exception {
        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(get("/api/budget/%d/%d".formatted(year, month))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private JsonNode day(int day) throws Exception {
        return month().get("days").get(day - 1);
    }

    private JsonNode current() throws Exception {
        return month(CURRENT.getYear(), CURRENT.getMonthValue());
    }

    private JsonNode currentDay(int day) throws Exception {
        return current().get("days").get(day - 1);
    }

    /** Дата внутри текущего месяца в формате запроса. */
    private static String at(int day, int hour) {
        return CURRENT.atDay(day).atTime(hour, 0).toString();
    }

    private void rollback(Long transactionId) throws Exception {
        mockMvc.perform(post("/api/budget/%d/%d/transactions/%d/rollback"
                        .formatted(CURRENT.getYear(), CURRENT.getMonthValue(), transactionId))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    /** targetBullionId == null - тело {} как от клиента без остатка для переноса. */
    private JsonNode close(int year, int month, Long targetBullionId) throws Exception {
        entityManager.flush();
        entityManager.clear();

        String body = targetBullionId == null
                ? "{}"
                : "{ \"targetBullionId\": %d }".formatted(targetBullionId);

        MvcResult result = mockMvc.perform(post("/api/budget/%d/%d/close".formatted(year, month))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private JsonNode reopen(int year, int month) throws Exception {
        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(post("/api/budget/%d/%d/reopen".formatted(year, month))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private JsonNode year(int year) throws Exception {
        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(get("/api/budget/" + year)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private BigDecimal bullionAmount(Long bullionId) {
        entityManager.flush();
        Object value = entityManager
                .createNativeQuery("SELECT amount FROM taurus.bullions WHERE id = " + bullionId)
                .getSingleResult();
        return new BigDecimal(value.toString());
    }

    private long transactionCount() {
        entityManager.flush();
        return ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM taurus.transactions")
                .getSingleResult()).longValue();
    }

    /**
     * {@code ObjectMapper.readTree} отдаёт дробные числа как {@code DoubleNode},
     * и {@code asText()} превращает 100000.00 в «100000.0». Поэтому суммы в
     * тестах сравниваются как BigDecimal по значению, а не по строке.
     */
    private static BigDecimal amount(JsonNode node, String field) {
        return node.get(field).decimalValue();
    }

    private static void assertAmount(String expected, JsonNode node, String field) {
        BigDecimal actual = amount(node, field);
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "%s: ожидали %s, получили %s".formatted(field, expected, actual.toPlainString()));
    }

    private Long fundToPlan() throws Exception {
        return fundToPlan(YEAR, MONTH);
    }

    private Long fundToPlan(int year, int month) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/budget/%d/%d/fund".formatted(year, month))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        json(result);
        return lastTransactionId();
    }

    private void setPlan(int year, int month, String plannedAmount) throws Exception {
        mockMvc.perform(put("/api/budget/%d/%d/plan".formatted(year, month))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "plannedAmount": %s }
                                """.formatted(plannedAmount)))
                .andExpect(status().isOk());
    }

    private void setSettings(Long budgetBullionId, Long sourceBullionId) throws Exception {
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(put("/api/budget/settings")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "budgetBullionId": %s,
                                    "sourceBullionId": %s
                                }
                                """.formatted(budgetBullionId, sourceBullionId)))
                .andExpect(status().isOk());
    }

    private Long withdraw(Long bullionId, String amount, String date, boolean budgetOperation) throws Exception {
        mockMvc.perform(post("/api/bullions/withdraw")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": %s,
                                    "dateOperation": "%s",
                                    "budgetOperation": %s
                                }
                                """.formatted(bullionNameOf(bullionId), vaultId, amount, date, budgetOperation)))
                .andExpect(status().isOk());
        return lastTransactionId();
    }

    private Long deposit(Long bullionId, String amount, String date, boolean budgetOperation) throws Exception {
        mockMvc.perform(post("/api/bullions/refill")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": %s,
                                    "dateOperation": "%s",
                                    "budgetOperation": %s
                                }
                                """.formatted(bullionNameOf(bullionId), vaultId, amount, date, budgetOperation)))
                .andExpect(status().isOk());
        return lastTransactionId();
    }

    private Long transfer(Long from, Long to, String amount, String date, boolean budgetOperation) throws Exception {
        mockMvc.perform(post("/api/bullions/transfer")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fromBullionId": %d,
                                    "toBullionId": %d,
                                    "amount": %s,
                                    "dateOperation": "%s",
                                    "budgetOperation": %s
                                }
                                """.formatted(from, to, amount, date, budgetOperation)))
                .andExpect(status().isOk());
        return lastTransactionId();
    }

    /** Запрос без поля budgetOperation — как от клиента, который про него не знает. */
    private void withdrawWithoutFlag(Long bullionId, String amount, String date) throws Exception {
        mockMvc.perform(post("/api/bullions/withdraw")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": %s,
                                    "dateOperation": "%s"
                                }
                                """.formatted(bullionNameOf(bullionId), vaultId, amount, date)))
                .andExpect(status().isOk());
    }

    private void transferWithoutFlag(Long from, Long to, String amount, String date) throws Exception {
        mockMvc.perform(post("/api/bullions/transfer")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fromBullionId": %d,
                                    "toBullionId": %d,
                                    "amount": %s,
                                    "dateOperation": "%s"
                                }
                                """.formatted(from, to, amount, date)))
                .andExpect(status().isOk());
    }

    private Long lastTransactionId() {
        entityManager.flush();
        return ((Number) entityManager
                .createNativeQuery("SELECT MAX(id) FROM taurus.transactions")
                .getSingleResult()).longValue();
    }

    private Long bullionNameOf(Long bullionId) {
        entityManager.flush();
        return ((Number) entityManager
                .createNativeQuery("SELECT bullion_name_id FROM taurus.bullions WHERE id = " + bullionId)
                .getSingleResult()).longValue();
    }

    private Long createBullion(String title, String amount) throws Exception {
        Long bullionNameId = createBullionName(title);
        MvcResult result = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": %s,
                                    "dateOperation": "2026-07-01T00:00:00"
                                }
                                """.formatted(bullionNameId, vaultId, amount)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data").get("id").asLong();
    }

    private Long createVault(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s" }
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data").get("id").asLong();
    }

    private Long createBullionName(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "%s" }
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data").get("id").asLong();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
