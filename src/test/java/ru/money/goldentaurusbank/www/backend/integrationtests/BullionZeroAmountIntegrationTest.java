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
import ru.money.goldentaurusbank.www.backend.repository.TransactionRepository;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Слиток с нулевой суммой — обычное дело: пустой слиток можно завести и можно
 * сохранить, поправив у него описание или тип.
 *
 * <p>Ноль при этом не операция: записи в историю не появляется. Запрет на
 * нулевую сумму остаётся только у самих операций — внести, снять, перевести на
 * 0 по-прежнему нельзя.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Интеграционные тесты нулевой суммы слитка")
class BullionZeroAmountIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private EntityManager entityManager;

    private String accessToken;
    private Long goldId;
    private Long vaultId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "zeroamount@example.com",
                            "password": "Test123%",
                            "fullName": "Zero Amount User"
                        }
                        """));

        User user = userRepository.findByEmail("zeroamount@example.com").get();
        mockMvc.perform(get("/api/auth/verify").param("token", user.getVerificationToken()));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "zeroamount@example.com",
                                    "password": "Test123%"
                                }
                                """))
                .andReturn();
        accessToken = json(loginResult).get("data").get("token").asText();

        goldId = createBullionName("Золото");
        vaultId = createVault("Хранилище");
    }

    @Test
    @DisplayName("Слиток заводится с нулевой суммой и без операции в истории")
    void createsWithZero() throws Exception {
        Long bullionId = createBullion("0");

        assertEquals(0, bullion(bullionId).get("amount").decimalValue().signum());
        assertEquals(0, transactionRepository.count(), "ноль не операция, в историю писать нечего");
    }

    @Test
    @DisplayName("Повторное создание с нулём отдаёт существующий слиток, а не ошибку")
    void repeatedCreateWithZeroKeepsBullion() throws Exception {
        Long bullionId = createBullion("100000");
        long transactionsAfterCreate = transactionRepository.count();

        // Раньше здесь падало «Cумма пополнения или снятия должна отличаться от 0»:
        // пара «наименование + хранилище» уже занята, и запрос уходил в пополнение
        Long repeatedId = createBullion("0");

        assertEquals(bullionId, repeatedId);
        assertEquals(100000, bullion(bullionId).get("amount").decimalValue().intValue());
        assertEquals(transactionsAfterCreate, transactionRepository.count(), "пополнения на 0 не бывает");
    }

    @Test
    @DisplayName("Правка обнуляет сумму слитка")
    void updateToZero() throws Exception {
        Long bullionId = createBullion("100000");

        update(bullionId, "0");

        assertEquals(0, bullion(bullionId).get("amount").decimalValue().signum());
    }

    @Test
    @DisplayName("Пустой слиток сохраняется с нулём - правится описание, не сумма")
    void updateZeroBullionKeepsZero() throws Exception {
        Long bullionId = createBullion("0");

        update(bullionId, "0");

        JsonNode bullion = bullion(bullionId);
        assertEquals(0, bullion.get("amount").decimalValue().signum());
        assertEquals("Правка", bullion.get("description").asText());
        assertEquals(0, transactionRepository.count(), "сумма не изменилась - операции нет");
    }

    // Вспомогательные методы

    private JsonNode bullion(Long bullionId) throws Exception {
        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(get("/api/bullions/" + bullionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data");
    }

    private void update(Long bullionId, String amount) throws Exception {
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(put("/api/bullions/" + bullionId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": %s,
                                    "description": "Правка"
                                }
                                """.formatted(goldId, vaultId, amount)))
                .andExpect(status().isOk());
    }

    private Long createBullion(String amount) throws Exception {
        entityManager.flush();
        entityManager.clear();

        MvcResult result = mockMvc.perform(post("/api/bullions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bullionNameId": %d,
                                    "vaultId": %d,
                                    "amount": %s,
                                    "dateOperation": "2026-07-20T12:00:00"
                                }
                                """.formatted(goldId, vaultId, amount)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data").get("id").asLong();
    }

    private Long createVault(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "%s"
                                }
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
                                {
                                    "title": "%s"
                                }
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("data").get("id").asLong();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
