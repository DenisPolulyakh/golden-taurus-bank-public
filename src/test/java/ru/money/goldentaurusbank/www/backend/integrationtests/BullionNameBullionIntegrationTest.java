package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.core.type.TypeReference;
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
import ru.money.goldentaurusbank.www.backend.model.domain.Bullion;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.response.GroupedBullionResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.VaultSummaryResponse;
import ru.money.goldentaurusbank.www.backend.repository.BullionRepository;
import ru.money.goldentaurusbank.www.backend.repository.BullionNameRepository;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;
import ru.money.goldentaurusbank.www.backend.repository.VaultRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.money.goldentaurusbank.www.backend.model.dto.enums.TransactionKind.*;
import static ru.money.goldentaurusbank.www.backend.service.BullionService.DEPOSIT_AMOUNT_COMMENT;
import static ru.money.goldentaurusbank.www.backend.service.BullionService.WITHDRAWAL_AMOUNT_COMMENT;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Интеграционные тесты слитков")
class BullionNameBullionIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BullionNameRepository bullionNameRepository;

    @Autowired
    private VaultRepository vaultRepository;

    @Autowired
    private BullionRepository bullionRepository;

    @Autowired
    private EntityManager entityManager;

    private String accessToken;
    private Long userId;
    private Long bullionNameId1;
    private Long bullionNameId2;
    private Long vaultId1;
    private Long vaultId2;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        bullionNameRepository.deleteAll();
        vaultRepository.deleteAll();
        bullionRepository.deleteAll();

        String registerRequest = """
                {
                    "email": "bullionuser@example.com",
                    "password": "Test123%",
                    "fullName": "Bullion Test User"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        User user = userRepository.findByEmail("bullionuser@example.com").get();
        userId = user.getId();

        mockMvc.perform(get("/api/auth/verify")
                .param("token", user.getVerificationToken()));

        String loginRequest = """
                {
                    "email": "bullionuser@example.com",
                    "password": "Test123%"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        accessToken = jsonNode.get("data").get("token").asText();

        String createBullionNameRequest1 = """
                {
                    "title": "Финансовая подушка"
                }
                """;
        MvcResult bullionNameResult1 = mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullionNameRequest1))
                .andReturn();
        bullionNameId1 = objectMapper.readTree(bullionNameResult1.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String createBullionNameRequest2 = """
                {
                    "title": "Накопления"
                }
                """;
        MvcResult bullionNameResult2 = mockMvc.perform(post("/api/bullion-names")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullionNameRequest2))
                .andReturn();
        bullionNameId2 = objectMapper.readTree(bullionNameResult2.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String createVaultRequest1 = """
                {
                    "name": "Сбербанк",
                    "interestRate": 5.0
                }
                """;
        MvcResult vaultResult1 = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createVaultRequest1))
                .andReturn();
        vaultId1 = objectMapper.readTree(vaultResult1.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String createVaultRequest2 = """
                {
                    "name": "Тинькофф",
                    "interestRate": 6.0
                }
                """;
        MvcResult vaultResult2 = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createVaultRequest2))
                .andReturn();
        vaultId2 = objectMapper.readTree(vaultResult2.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    @Test
    @DisplayName("Создание слитка - успешно")
    void createBullionSuccess() throws Exception {
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "description": "На чёрный день",
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId1);

        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Слиток успешно добавлен"))
                .andExpect(jsonPath("$.data.amount").value(100000));
    }

    @Test
    @DisplayName("Создание слитка с дубликатом категории и хранилища - обновляет сумму")
    void createBullionDuplicateUpdatesAmount() throws Exception {
        String request1 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId1);

        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request1))
                .andExpect(status().isOk());

        String request2 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 50000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId1);

        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Слиток успешно добавлен"))
                .andExpect(jsonPath("$.data.amount").value(150000));
    }

    @Test
    @DisplayName("Получение группированных слитков - успешно")
    void getGroupedBullionsSuccess() throws Exception {
        String createBullion1 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion1))
                .andExpect(status().isOk());

        String createBullion2 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 50000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId2);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion2))
                .andExpect(status().isOk());

        String createBullion3 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 75000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId2, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion3))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(get("/api/bullions/grouped")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalAmount").exists())
                .andExpect(jsonPath("$.data.totalAmount").value(225000))
                .andExpect(jsonPath("$.data.averageRate").value(5.22))
                .andExpect(jsonPath("$.data.bullionNameBullionList").isArray())
                .andExpect(jsonPath("$.data.bullionNameBullionList.length()").value(2))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        // Получаем общую статистику
        BigDecimal totalAmount = new BigDecimal(jsonNode.get("data").get("totalAmount").asText());
        BigDecimal averageRate = new BigDecimal(jsonNode.get("data").get("averageRate").asText());

        // Получаем список категорий
        List<GroupedBullionResponse.BullionNameBullion> bullionNameBullionList = objectMapper.convertValue(
                jsonNode.get("data").get("bullionNameBullionList"),
                new TypeReference<List<GroupedBullionResponse.BullionNameBullion>>() {}
        );

        // Проверяем общую сумму
        assertThat(totalAmount).isEqualByComparingTo(new BigDecimal("225000"));

        // Проверяем первую категорию
        GroupedBullionResponse.BullionNameBullion group1 = bullionNameBullionList.stream()
                .filter(g -> g.getBullionNameId().equals(bullionNameId1))
                .findFirst()
                .orElse(null);

        assertThat(group1).isNotNull();
        assertThat(group1.getBullionNameTitle()).isNotEmpty();
        assertThat(group1.getBullionNameAmount()).isEqualByComparingTo(new BigDecimal("150000"));
        assertThat(group1.getBullionNameAverageRate()).isEqualByComparingTo(new BigDecimal("5.33"));
        assertThat(group1.getVaults()).hasSize(2);

        // Проверяем хранилища в первой категории
        GroupedBullionResponse.BullionNameBullion.VaultInfo vault1 = group1.getVaults().stream()
                .filter(v -> v.getId().equals(vaultId1))
                .findFirst()
                .orElse(null);
        assertThat(vault1).isNotNull();
        assertThat(vault1.getAmount()).isEqualByComparingTo(new BigDecimal("100000"));

        GroupedBullionResponse.BullionNameBullion.VaultInfo vault2 = group1.getVaults().stream()
                .filter(v -> v.getId().equals(vaultId2))
                .findFirst()
                .orElse(null);
        assertThat(vault2).isNotNull();
        assertThat(vault2.getAmount()).isEqualByComparingTo(new BigDecimal("50000"));

        // Проверяем вторую категорию
        GroupedBullionResponse.BullionNameBullion group2 = bullionNameBullionList.stream()
                .filter(g -> g.getBullionNameId().equals(bullionNameId2))
                .findFirst()
                .orElse(null);

        assertThat(group2).isNotNull();
        assertThat(group2.getBullionNameTitle()).isNotEmpty();
        assertThat(group2.getBullionNameAmount()).isEqualByComparingTo(new BigDecimal("75000"));
        assertThat(group2.getVaults()).hasSize(1);

        // Проверяем хранилище во второй категории
        GroupedBullionResponse.BullionNameBullion.VaultInfo vaultInGroup2 = group2.getVaults().stream()
                .filter(v -> v.getId().equals(vaultId1))
                .findFirst()
                .orElse(null);
        assertThat(vaultInGroup2).isNotNull();
        assertThat(vaultInGroup2.getAmount()).isEqualByComparingTo(new BigDecimal("75000"));

    }

    @Test
    @DisplayName("Получение сводки по хранилищу - успешно")
    void getVaultSummarySuccess() throws Exception {
        String createBullion1 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "description": "Описание 1",
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion1))
                .andExpect(status().isOk());

        String createBullion2 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 75000,
                    "description": "Описание 2",
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId2, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion2))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(get("/api/vaults/{vaultId}/summary", vaultId1)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.vaultId").value(vaultId1))
                .andExpect(jsonPath("$.data.vaultName").value("Сбербанк"))
                .andExpect(jsonPath("$.data.totalAmount").value(175000))
                .andExpect(jsonPath("$.data.bullionNamesCount").value(2))
                .andExpect(jsonPath("$.data.bullions").isArray())
                .andExpect(jsonPath("$.data.bullions.length()").value(2))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        VaultSummaryResponse summary = objectMapper.convertValue(
                jsonNode.get("data"),
                new TypeReference<VaultSummaryResponse>() {}
        );

        assertThat(summary.getTotalAmount()).isEqualByComparingTo(new BigDecimal("175000"));
        assertThat(summary.getBullions()).hasSize(2);
    }

    @Test
    @DisplayName("Получение сводки по несуществующему хранилищу - ошибка")
    void getVaultSummaryNotFoundThrowsException() throws Exception {
        mockMvc.perform(get("/api/vaults/{vaultId}/summary", 99999L)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4004));
    }

    @Test
    @DisplayName("Получение общей суммы всех слитков")
    void getTotalAmountSuccess() throws Exception {
        String createBullion1 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion1))
                .andExpect(status().isOk());

        String createBullion2 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 50000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId2, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion2))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bullions/total")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(150000));
    }

    @Test
    @DisplayName("Получение всех слитков пользователя")
    void getAllBullionsSuccess() throws Exception {
        String createBullion1 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion1))
                .andExpect(status().isOk());

        String createBullion2 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 50000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId2, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion2))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("Обновление слитка - успешно")
    void updateBullionSuccess() throws Exception {
        String createRequest = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "description": "Старое описание",
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId1);

        MvcResult createResult = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andReturn();

        Long bullionId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String updateRequest = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 150000,
                    "description": "Новое описание",
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId1);

        mockMvc.perform(put("/api/bullions/{bullionId}", bullionId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.amount").value(150000))
                .andExpect(jsonPath("$.data.description").value("Новое описание"));
    }

    @Test
    @DisplayName("Проверка операций DEPOSIT при увеличении суммы слитка")
    void updateAmountUp() throws Exception {
        Long bullionId = createBullion(100000);

        MvcResult result = updateBullionAmount(bullionId, "150000", null, null);

        BigDecimal amount = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("amount").decimalValue();
        assertThat(amount).isEqualByComparingTo("150000");

        JsonNode operation = lastTransaction();
        assertThat(operation.get("kind").asText()).isEqualTo(DEPOSIT.name());
        assertThat(operation.get("amount").decimalValue()).isEqualByComparingTo("50000");
        assertThat(operation.get("comment").asText()).isEqualTo(DEPOSIT_AMOUNT_COMMENT);
    }

    @Test
    @DisplayName("Проверка операций WITHDRAWAL при уменьшении суммы слитка")
    void updateAmountDown() throws Exception {
        Long bullionId = createBullion(100000);

        MvcResult result = updateBullionAmount(bullionId, "40000", null, null);

        BigDecimal amount = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("amount").decimalValue();
        assertThat(amount).isEqualByComparingTo("40000");

        JsonNode operation = lastTransaction();
        assertThat(operation.get("kind").asText()).isEqualTo(WITHDRAWAL.name());
        assertThat(operation.get("amount").decimalValue()).isEqualByComparingTo("60000");
        assertThat(operation.get("comment").asText()).isEqualTo(WITHDRAWAL_AMOUNT_COMMENT);
    }

    @Test
    @DisplayName("Проверка отсутствие операций при изменении описания слитка")
    void updateDescription() throws Exception {
        Long bullionId = createBullion(100000);
        long operationsBefore = transactionCount();

        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "description": "%s",
                    "amount": 100000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId1, "Новое описание");

        MvcResult result = mockMvc.perform(put("/api/bullions/{bullionId}", bullionId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data.get("amount").decimalValue()).isEqualByComparingTo("100000");
        assertThat(data.get("description").asText()).isEqualTo("Новое описание");

        // Сумма не изменилась — новых операций быть не должно, последней остаётся создание слитка
        assertThat(transactionCount()).isEqualTo(operationsBefore);
        assertThat(lastTransaction().get("kind").asText()).isEqualTo(OPENING_BALANCE.name());
    }

    @Test
    @DisplayName("Комментарий пользователя становится комментарием корректирующей операции")
    void updateAmountUsesUserComment() throws Exception {
        Long bullionId = createBullion(100000);

        // По краям пробелы — determineComment их срезает
        updateBullionAmount(bullionId, "150000", "  докупил  ", null);

        assertThat(lastTransaction().get("comment").asText()).isEqualTo("докупил");
    }

    @Test
    @DisplayName("Комментарий из пробелов заменяется константой пополнения")
    void updateAmountBlankUserCommentFallsBackToDepositComment() throws Exception {
        Long bullionId = createBullion(100000);

        updateBullionAmount(bullionId, "150000", "   ", null);

        assertThat(lastTransaction().get("comment").asText()).isEqualTo(DEPOSIT_AMOUNT_COMMENT);
    }

    @Test
    @DisplayName("Пустой комментарий заменяется константой снятия")
    void updateAmountEmptyUserCommentFallsBackToWithdrawalComment() throws Exception {
        Long bullionId = createBullion(100000);

        updateBullionAmount(bullionId, "40000", "", null);

        assertThat(lastTransaction().get("comment").asText()).isEqualTo(WITHDRAWAL_AMOUNT_COMMENT);
    }

    @Test
    @DisplayName("Дата из запроса доезжает до корректирующей операции")
    void updateAmountKeepsDateOperation() throws Exception {
        Long bullionId = createBullion(100000);

        // Дата правки отличается и от даты создания слитка, и от текущей — обе подмены поймаются
        updateBullionAmount(bullionId, "150000", null, "2026-08-10T14:30:00");

        LocalDateTime dateOperation = LocalDateTime.parse(lastTransaction().get("dateOperation").asText());
        assertThat(dateOperation).isEqualTo(LocalDateTime.of(2026, 8, 10, 14, 30));
    }

    @Test
    @DisplayName("Удаление пустого слитка - успешно")
    void deleteEmptyBullionSuccess() throws Exception {
        Long bullionId = createBullion(0);

        mockMvc.perform(delete("/api/bullions/{bullionId}", bullionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Слиток успешно удалён"));

        assertThat(bullionRepository.findById(bullionId).orElseThrow().isArchived()).isTrue();
    }

    @Test
    @DisplayName("Удаление слитка с остатком - отказ, остаток на месте")
    void deleteFundedBullionThrowsException() throws Exception {
        Long bullionId = createBullion(100000);

        mockMvc.perform(delete("/api/bullions/{bullionId}", bullionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4038))
                .andExpect(jsonPath("$.message")
                        .value("У слитка есть остаток: удалить можно только с переносом остатка"));

        // Остаток отсюда не списывается: деньги ушли бы в никуда, минуя перенос
        Bullion bullion = bullionRepository.findById(bullionId).orElseThrow();
        assertThat(bullion.isArchived()).isFalse();
        assertThat(bullion.getAmount()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("Попытка доступа к слиткам без токена - ошибка")
    void accessWithoutTokenThrowsException() throws Exception {
        mockMvc.perform(get("/api/bullions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Получение группированных слитков - пустой список")
    void getGroupedBullionsEmpty() throws Exception {
        mockMvc.perform(get("/api/bullions/grouped")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.bullionNameBullionList").isArray())
                .andExpect(jsonPath("$.data.bullionNameBullionList.length()").value(0))
                .andReturn();
    }

    @Test
    @DisplayName("Получение группированных слитков - несколько категорий в одном хранилище")
    void getGroupedBullionsMultipleBullionNamesOneVault() throws Exception {
        String createBullion1 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion1))
                .andExpect(status().isOk());

        String createBullion2 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 75000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId2, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion2))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(get("/api/bullions/grouped")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalAmount").exists())
                .andExpect(jsonPath("$.data.averageRate").exists())
                .andExpect(jsonPath("$.data.bullionNameBullionList").isArray())
                .andExpect(jsonPath("$.data.bullionNameBullionList.length()").value(2))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        // Получаем список категорий
        List<GroupedBullionResponse.BullionNameBullion> bullionNameBullionList = objectMapper.convertValue(
                jsonNode.get("data").get("bullionNameBullionList"),
                new TypeReference<List<GroupedBullionResponse.BullionNameBullion>>() {}
        );

        assertThat(bullionNameBullionList).hasSize(2);
        assertThat(bullionNameBullionList).extracting(GroupedBullionResponse.BullionNameBullion::getBullionNameTitle)
                .containsExactlyInAnyOrder("Финансовая подушка", "Накопления");

        // Проверяем, что у обоих категорий vaults содержит одно хранилище
        for (GroupedBullionResponse.BullionNameBullion bullionName : bullionNameBullionList) {
            assertThat(bullionName.getVaults()).hasSize(1);
            assertThat(bullionName.getVaults().get(0).getId()).isEqualTo(vaultId1);
            assertThat(bullionName.getVaults().get(0).getName()).isNotEmpty();
        }

        // Проверяем сумму первой категории
        GroupedBullionResponse.BullionNameBullion bullionName1 = bullionNameBullionList.stream()
                .filter(c -> c.getBullionNameId().equals(bullionNameId1))
                .findFirst()
                .orElse(null);
        assertThat(bullionName1).isNotNull();
        assertThat(bullionName1.getBullionNameAmount()).isEqualByComparingTo(new BigDecimal("100000"));

        // Проверяем сумму второй категории
        GroupedBullionResponse.BullionNameBullion bullionName2 = bullionNameBullionList.stream()
                .filter(c -> c.getBullionNameId().equals(bullionNameId2))
                .findFirst()
                .orElse(null);
        assertThat(bullionName2).isNotNull();
        assertThat(bullionName2.getBullionNameAmount()).isEqualByComparingTo(new BigDecimal("75000"));

        // Проверяем общую сумму
        BigDecimal totalAmount = new BigDecimal(jsonNode.get("data").get("totalAmount").asText());
        assertThat(totalAmount).isEqualByComparingTo(new BigDecimal("175000"));
    }

    @Test
    @DisplayName("Получение хранилища - пустое хранилище")
    void getVaultSummaryEmpty() throws Exception {
        mockMvc.perform(get("/api/vaults/{vaultId}", vaultId1)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(vaultId1))
                .andExpect(jsonPath("$.data.name").value("Сбербанк"))
                .andExpect(jsonPath("$.data.totalAmount").value(0))
                .andExpect(jsonPath("$.data.bullionNamesCount").value(0));
    }

    @Test
    @DisplayName("Получение сводки по хранилищу с несколькими слитками")
    void getVaultSummaryMultipleBullions() throws Exception {
        String createBullion1 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion1))
                .andExpect(status().isOk());

        String createBullion2 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 50000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion2))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(get("/api/vaults/{vaultId}/summary", vaultId1)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(150000))
                .andExpect(jsonPath("$.data.bullionNamesCount").value(1))
                .andExpect(jsonPath("$.data.bullions.length()").value(1))
                .andExpect(jsonPath("$.data.bullions[0].amount").value(150000))
                .andReturn();
    }

    @Test
    @DisplayName("Получение сводки по хранилищу - несколько категорий")
    void getVaultSummaryMultipleBullionNames() throws Exception {
        String createBullion1 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 100000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion1))
                .andExpect(status().isOk());

        String createBullion2 = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": 75000,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId2, vaultId1);
        mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBullion2))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(get("/api/vaults/{vaultId}/summary", vaultId1)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(175000))
                .andExpect(jsonPath("$.data.bullionNamesCount").value(2))
                .andExpect(jsonPath("$.data.bullions.length()").value(2))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        VaultSummaryResponse summary = objectMapper.convertValue(
                jsonNode.get("data"),
                new TypeReference<VaultSummaryResponse>() {}
        );

        assertThat(summary.getBullions()).hasSize(2);
        assertThat(summary.getBullions()).extracting(VaultSummaryResponse.BullionByBullionNameResponse::getBullionNameTitle)
                .containsExactlyInAnyOrder("Финансовая подушка", "Накопления");
    }

    private Long createBullion(long amount) throws Exception {
        String request = """
                {
                    "bullionNameId": %d,
                    "vaultId": %d,
                    "amount": %d,
                    "dateOperation": "2026-07-20T12:00:00"
                }
                """.formatted(bullionNameId1, vaultId1, amount);

        MvcResult result = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    /** userComment передаётся только когда задан — иначе поля в теле нет вовсе, как у старого фронта. */
    private MvcResult updateBullionAmount(Long bullionId, String amount, String userComment, String dateOperation)
            throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("bullionNameId", bullionNameId1);
        request.put("vaultId", vaultId1);
        request.put("amount", new BigDecimal(amount));
        if (userComment != null) {
            request.put("userComment", userComment);
        }
        request.put("dateOperation", dateOperation != null ? dateOperation : "2026-07-20T12:00:00");

        return mockMvc.perform(put("/api/bullions/{bullionId}", bullionId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** Последняя операция в общей истории: она отсортирована от свежих к старым. */
    private JsonNode lastTransaction() throws Exception {
        return transactionHistory().get("content").get(0);
    }

    private long transactionCount() throws Exception {
        return transactionHistory().get("totalElements").asLong();
    }

    private JsonNode transactionHistory() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/transactions/history")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}