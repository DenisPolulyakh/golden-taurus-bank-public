package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import ru.money.goldentaurusbank.www.backend.model.domain.Transaction;
import ru.money.goldentaurusbank.www.backend.repository.TransactionRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Интеграционные тесты переноса выделенных слитков в другое хранилище")
class MoveBullionsIntegrationTest extends CreditCardTestBase {

    private static final int SAME_VAULT = 4000;
    private static final int INSUFFICIENT_FUNDS = 4002;
    private static final int BULLION_NOT_FOUND = 4006;
    private static final int VAULT_INCOME_NOT_ALLOWED = 4015;
    private static final int VAULT_EXPENSE_NOT_ALLOWED = 4016;
    private static final int VAULT_TRANSFER_NOT_ALLOWED = 4017;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("Полный перенос в хранилище без такого слитка: слиток создан, исходный в архиве")
    void fullMoveCreatesTargetAndArchivesSource() throws Exception {
        Long goldId = createName("Золото");
        Long fromVaultId = createVault("Откуда");
        Long toVaultId = createVault("Куда");
        Long sourceId = createBullionOfName(goldId, fromVaultId, "100000");

        move(toVaultId, null, item(sourceId, "100000")).andExpect(status().isOk());

        assertNull(liveBullion(sourceId), "исходный слиток ушёл в архив");
        JsonNode target = liveBullionInVault(toVaultId, goldId);
        assertNotNull(target, "в целевом хранилище появился слиток");
        assertEquals(0, new BigDecimal("100000").compareTo(target.get("amount").decimalValue()));
    }

    @Test
    @DisplayName("Частичный перенос: остаток остаётся в исходном слитке, остальное уходит")
    void partialMoveKeepsRemainder() throws Exception {
        Long goldId = createName("Золото");
        Long fromVaultId = createVault("Откуда");
        Long toVaultId = createVault("Куда");
        Long sourceId = createBullionOfName(goldId, fromVaultId, "1000100");

        move(toVaultId, null, item(sourceId, "1000000")).andExpect(status().isOk());

        JsonNode source = liveBullion(sourceId);
        assertNotNull(source, "слиток с остатком не архивируется");
        assertEquals(0, new BigDecimal("100").compareTo(source.get("amount").decimalValue()));
        JsonNode target = liveBullionInVault(toVaultId, goldId);
        assertEquals(0, new BigDecimal("1000000").compareTo(target.get("amount").decimalValue()));
    }

    @Test
    @DisplayName("В целевом хранилище слиток того же наименования есть: сумма добавляется, дубля нет")
    void addsToExistingTargetBullion() throws Exception {
        Long goldId = createName("Золото");
        Long fromVaultId = createVault("Откуда");
        Long toVaultId = createVault("Куда");
        Long sourceId = createBullionOfName(goldId, fromVaultId, "30000");
        Long existingId = createBullionOfName(goldId, toVaultId, "5000");

        move(toVaultId, null, item(sourceId, "30000")).andExpect(status().isOk());

        assertEquals(1, liveBullionsInVault(toVaultId));
        JsonNode target = liveBullion(existingId);
        assertEquals(0, new BigDecimal("35000").compareTo(target.get("amount").decimalValue()));
    }

    @Test
    @DisplayName("В целевом хранилище архивный слиток того же наименования оживает с тем же id")
    void revivesArchivedTargetBullion() throws Exception {
        Long goldId = createName("Золото");
        Long fromVaultId = createVault("Откуда");
        Long toVaultId = createVault("Куда");
        Long sourceId = createBullionOfName(goldId, fromVaultId, "30000");
        Long archivedId = createBullionOfName(goldId, toVaultId, "0");
        mockMvc.perform(delete("/api/bullions/{bullionId}", archivedId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        assertNull(liveBullion(archivedId));

        move(toVaultId, null, item(sourceId, "30000")).andExpect(status().isOk());

        JsonNode revived = liveBullion(archivedId);
        assertNotNull(revived, "прежний слиток вернулся в список");
        assertEquals(0, new BigDecimal("30000").compareTo(revived.get("amount").decimalValue()));
    }

    @Test
    @DisplayName("Три слитка за раз переносятся одним пакетом операций с общим batchId")
    void threeBullionsShareBatchId() throws Exception {
        Long fromVaultId = createVault("Откуда");
        Long toVaultId = createVault("Куда");
        Long first = createBullionOfName(createName("Золото"), fromVaultId, "1000");
        Long second = createBullionOfName(createName("Серебро"), fromVaultId, "2000");
        Long third = createBullionOfName(createName("Платина"), fromVaultId, "3000");

        move(toVaultId, null, item(first, "1000"), item(second, "1500"), item(third, "3000"))
                .andExpect(status().isOk());

        assertEquals(3, liveBullionsInVault(toVaultId), "в целевом три слитка");
        Set<Long> sources = Set.of(first, second, third);
        List<Transaction> transfers = transactionRepository.findAll().stream()
                .filter(t -> t.getSourceBullionId() != null && sources.contains(t.getSourceBullionId()))
                .toList();
        assertEquals(3, transfers.size());
        Set<Long> batchIds = transfers.stream().map(Transaction::getBatchId).collect(Collectors.toSet());
        assertEquals(1, batchIds.size(), "у всех операций переноса один batchId");
        assertNotNull(batchIds.iterator().next());
    }

    @Test
    @DisplayName("С датой операции: у операций переноса стоит переданная дата")
    void usesGivenOperationDate() throws Exception {
        Long goldId = createName("Золото");
        Long fromVaultId = createVault("Откуда");
        Long toVaultId = createVault("Куда");
        Long sourceId = createBullionOfName(goldId, fromVaultId, "10000");

        move(toVaultId, "2026-09-15T10:30:00", item(sourceId, "4000")).andExpect(status().isOk());

        Transaction transfer = transactionRepository.findAll().stream()
                .filter(t -> sourceId.equals(t.getSourceBullionId()))
                .findFirst().orElseThrow();
        assertEquals("2026-09-15T10:30", transfer.getDateOperation().toString());
    }

    @Test
    @DisplayName("Бюджетный слиток, ушедший в ноль, остаётся активным")
    void budgetBullionStaysAfterMoveToZero() throws Exception {
        Long walletNameId = createName("Кошелёк");
        Long fromVaultId = createVault("Откуда");
        Long toVaultId = createVault("Куда");
        Long walletId = createBullionOfName(walletNameId, fromVaultId, "5000");
        mockMvc.perform(put("/api/budget/settings")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "budgetBullionId": %d
                                }
                                """.formatted(walletId)))
                .andExpect(status().isOk());

        move(toVaultId, null, item(walletId, "5000")).andExpect(status().isOk());

        JsonNode wallet = liveBullion(walletId);
        assertNotNull(wallet, "бюджетный слиток не архивируется");
        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.get("amount").decimalValue()));
    }

    @Test
    @DisplayName("Накопитель кредитной карты, ушедший в ноль, остаётся активным и привязанным к карте")
    void cardBullionStaysAfterMoveToZero() throws Exception {
        Long fromVaultId = createVault("Откуда");
        Long toVaultId = createVault("Куда");
        Long bullionId = createCreditBullion(fromVaultId, "Подушка", "30000");
        Long cardId = createCard("Платинум", "4321", "300000", "1000", List.of(bullionId));

        move(toVaultId, null, item(bullionId, "30000")).andExpect(status().isOk());

        JsonNode bullion = liveBullion(bullionId);
        assertNotNull(bullion, "накопитель карты не архивируется");
        assertEquals(0, BigDecimal.ZERO.compareTo(bullion.get("amount").decimalValue()));
        JsonNode accumulators = getCard(cardId).get("accumulators");
        assertEquals(1, accumulators.size(), "связь с картой на месте");
        assertEquals(bullionId.longValue(), accumulators.get(0).get("bullionId").asLong());
    }

    @Test
    @DisplayName("Сумма больше остатка во втором слитке: ошибка и откат всего переноса")
    void insufficientFundsRollsEverythingBack() throws Exception {
        Long fromVaultId = createVault("Откуда");
        Long toVaultId = createVault("Куда");
        Long first = createBullionOfName(createName("Золото"), fromVaultId, "100");
        Long second = createBullionOfName(createName("Серебро"), fromVaultId, "200");
        long transactionsBefore = transactionRepository.count();

        move(toVaultId, null, item(first, "50"), item(second, "300"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INSUFFICIENT_FUNDS));

        assertEquals(0, new BigDecimal("100").compareTo(liveBullion(first).get("amount").decimalValue()));
        assertEquals(0, new BigDecimal("200").compareTo(liveBullion(second).get("amount").decimalValue()));
        assertEquals(0, liveBullionsInVault(toVaultId), "в целевом ничего не создано");
        assertEquals(transactionsBefore, transactionRepository.count(), "операций не прибавилось");
    }

    @Test
    @DisplayName("В целевом хранилище снята галочка «Можно вносить»: ошибка и ничего не изменилось")
    void rejectsTargetWithoutIncome() throws Exception {
        Long fromVaultId = createVault("Откуда");
        Long toVaultId = createVaultWith("Только снятие", ",\"allowedIncome\": false");
        Long sourceId = createBullionOfName(createName("Золото"), fromVaultId, "1000");
        long transactionsBefore = transactionRepository.count();

        move(toVaultId, null, item(sourceId, "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VAULT_INCOME_NOT_ALLOWED));

        assertEquals(0, new BigDecimal("1000").compareTo(liveBullion(sourceId).get("amount").decimalValue()));
        assertEquals(0, liveBullionsInVault(toVaultId));
        assertEquals(transactionsBefore, transactionRepository.count());
    }

    @Test
    @DisplayName("В исходном хранилище снята галочка «Можно снимать»: ошибка")
    void rejectsSourceWithoutExpense() throws Exception {
        Long fromVaultId = createVaultWith("Без снятия", ",\"allowedExpense\": false");
        Long toVaultId = createVault("Куда");
        Long sourceId = createBullionOfName(createName("Золото"), fromVaultId, "1000");

        move(toVaultId, null, item(sourceId, "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VAULT_EXPENSE_NOT_ALLOWED));

        assertEquals(0, new BigDecimal("1000").compareTo(liveBullion(sourceId).get("amount").decimalValue()));
    }

    @Test
    @DisplayName("В исходном хранилище снята галочка «Можно переводить»: ошибка")
    void rejectsSourceWithoutTransfer() throws Exception {
        Long fromVaultId = createVaultWith("Без переводов", ",\"allowedTransfer\": false");
        Long toVaultId = createVault("Куда");
        Long sourceId = createBullionOfName(createName("Золото"), fromVaultId, "1000");

        move(toVaultId, null, item(sourceId, "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VAULT_TRANSFER_NOT_ALLOWED));

        assertEquals(0, new BigDecimal("1000").compareTo(liveBullion(sourceId).get("amount").decimalValue()));
    }

    @Test
    @DisplayName("Перенос в то же хранилище запрещён")
    void rejectsSameVault() throws Exception {
        Long vaultId = createVault("Одно");
        Long sourceId = createBullionOfName(createName("Золото"), vaultId, "1000");

        move(vaultId, null, item(sourceId, "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(SAME_VAULT));
    }

    @Test
    @DisplayName("Чужой слиток не переносится")
    void rejectsForeignBullion() throws Exception {
        Long toVaultId = createVault("Куда");

        String ownToken = accessToken;
        accessToken = registerAndLogin("other-move@example.com", "Other User");
        Long foreignVaultId = createVault("Чужое");
        Long foreignBullionId = createBullionOfName(createName("Золото"), foreignVaultId, "1000");
        accessToken = ownToken;

        move(toVaultId, null, item(foreignBullionId, "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BULLION_NOT_FOUND));
    }

    @Test
    @DisplayName("Пустой список, нулевая сумма и запрос без хранилища отклоняются валидацией")
    void rejectsInvalidRequests() throws Exception {
        Long fromVaultId = createVault("Откуда");
        Long toVaultId = createVault("Куда");
        Long sourceId = createBullionOfName(createName("Золото"), fromVaultId, "1000");

        move(toVaultId, null).andExpect(status().isBadRequest());
        move(toVaultId, null, item(sourceId, "0")).andExpect(status().isBadRequest());
        rawMove("""
                {
                    "items": [ %s ]
                }
                """.formatted(item(sourceId, "500"))).andExpect(status().isBadRequest());

        assertEquals(0, new BigDecimal("1000").compareTo(liveBullion(sourceId).get("amount").decimalValue()));
    }

    private ResultActions move(Long toVaultId, String dateOperation, String... items) throws Exception {
        String date = dateOperation == null ? "null" : "\"" + dateOperation + "\"";
        return rawMove("""
                {
                    "toVaultId": %d,
                    "dateOperation": %s,
                    "items": [ %s ]
                }
                """.formatted(toVaultId, date, String.join(", ", items)));
    }

    private ResultActions rawMove(String body) throws Exception {
        return mockMvc.perform(post("/api/bullions/move-to-vault")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String item(Long bullionId, String amount) {
        return """
                { "bullionId": %d, "amount": %s }""".formatted(bullionId, amount);
    }

    private JsonNode liveBullion(Long bullionId) throws Exception {
        for (JsonNode bullion : liveBullions()) {
            if (bullion.get("id").asLong() == bullionId) {
                return bullion;
            }
        }
        return null;
    }

    private JsonNode liveBullionInVault(Long vaultId, Long bullionNameId) throws Exception {
        for (JsonNode bullion : liveBullions()) {
            if (bullion.get("vault").get("id").asLong() == vaultId
                    && bullion.get("bullionName").get("id").asLong() == bullionNameId) {
                return bullion;
            }
        }
        return null;
    }

    private int liveBullionsInVault(Long vaultId) throws Exception {
        int count = 0;
        for (JsonNode bullion : liveBullions()) {
            if (bullion.get("vault").get("id").asLong() == vaultId) {
                count++;
            }
        }
        return count;
    }

    private JsonNode liveBullions() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private Long createName(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "%s"
                                }
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn();
        return dataId(result);
    }

    private Long createBullionOfName(Long bullionNameId, Long vaultId, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": %s,
                                    "dateOperation": "2026-08-20T12:00:00"
                                }
                                """.formatted(bullionNameId, vaultId, amount)))
                .andExpect(status().isOk())
                .andReturn();
        return dataId(result);
    }

    private Long createVaultWith(String name, String extraFields) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "%s"%s
                                }
                                """.formatted(name, extraFields)))
                .andExpect(status().isOk())
                .andReturn();
        return dataId(result);
    }
}
