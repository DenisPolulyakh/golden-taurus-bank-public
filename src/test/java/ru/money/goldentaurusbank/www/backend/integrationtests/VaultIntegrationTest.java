package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.VaultType;
import ru.money.goldentaurusbank.www.backend.model.dto.response.VaultResponse;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;
import ru.money.goldentaurusbank.www.backend.repository.VaultRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DisplayName("Интеграционные тесты банков")
class VaultIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VaultRepository vaultRepository;


    @Autowired
    private EntityManager entityManager;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig()
                    .withUser("test@greenmail.com", "test"))
            .withPerMethodLifecycle(false);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.default-schema", () -> "taurus");
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
        registry.add("spring.mail.username", () -> "test@greenmail.com");
        registry.add("spring.mail.password", () -> "test");
        registry.add("spring.mail.protocol", () -> "smtp");
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "true");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.ssl.enable", () -> "false");
        registry.add("app.jwt.secret", () -> "testSecretKeyForJWTTokenGeneration2026");
        registry.add("app.jwt.access-expiration", () -> "3600000");
        registry.add("app.jwt.refresh-expiration", () -> "604800000");
    }

    private String accessToken;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        vaultRepository.deleteAll();

        // Регистрация и подтверждение пользователя
        String registerRequest = """
                {
                    "email": "vaultuser@example.com",
                    "password": "Test123%",
                    "fullName": "Vault Test User"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        User user = userRepository.findByEmail("vaultuser@example.com").get();
        userId = user.getId();

        // Подтверждение email
        mockMvc.perform(get("/api/auth/verify")
                .param("token", user.getVerificationToken()));

        // Логин для получения токена
        String loginRequest = """
                {
                    "email": "vaultuser@example.com",
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
    }

    @Test
    @DisplayName("Создание банка - успешно")
    void createVaultSuccess() throws Exception {
        String request = """
                {
                    "name": "Сбербанк",
                    "description": "Крупнейший банк России"
                }
                """;

        mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Хранилище успешно добавлено"))
                .andExpect(jsonPath("$.data.name").value("Сбербанк"))
                .andExpect(jsonPath("$.data.description").value("Крупнейший банк России"));

        // Проверяем, что банк сохранился в БД
        List<VaultResponse> vaults = getVaults();
        assertThat(vaults).hasSize(1);
        assertThat(vaults.get(0).getName()).isEqualTo("Сбербанк");
        assertThat(vaults.get(0).getDescription()).isEqualTo("Крупнейший банк России");
        assertThat(vaults.get(0).getVaultType()).isEqualTo(VaultType.REGULAR);
    }

    @Test
    @DisplayName("Создание банка без описания - успешно")
    void createVaultWithoutDescriptionSuccess() throws Exception {
        String request = """
                {
                    "name": "Тинькофф"
                }
                """;

        mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Хранилище успешно добавлено"))
                .andExpect(jsonPath("$.data.name").value("Тинькофф"))
                .andExpect(jsonPath("$.data.description").isEmpty());

        List<VaultResponse> vaults = getVaults();
        assertThat(vaults).hasSize(1);
        assertThat(vaults.get(0).getName()).isEqualTo("Тинькофф");
        assertThat(vaults.get(0).getVaultType()).isEqualTo(VaultType.REGULAR);
    }

    @Test
    @DisplayName("Создание банка с пустым именем - ошибка валидации")
    void createVaultEmptyNameValidationError() throws Exception {
        String request = """
                {
                    "name": ""
                }
                """;

        mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @DisplayName("Создание банка с именем короче 2 символов - ошибка валидации")
    void createVaultNameTooShortValidationError() throws Exception {
        String request = """
                {
                    "name": "А"
                }
                """;

        mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @DisplayName("Создание банка с описанием длиннее 500 символов - ошибка валидации")
    void createVaultDescriptionTooLongValidationError() throws Exception {
        String longDescription = "A".repeat(501);
        String request = String.format("""
                {
                    "name": "Тестовый банк",
                    "description": "%s"
                }
                """, longDescription);

        mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @DisplayName("Получение всех банков пользователя")
    void getAllVaultsSuccess() throws Exception {
        // Создаём несколько банков
        createVault("Сбербанк", "Крупнейший банк России");
        createVault("Тинькофф", "Онлайн-банк");
        createVault("Альфа-Банк", "Универсальный банк");

        MvcResult result = mockMvc.perform(get("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        List<VaultResponse> vaults = objectMapper.convertValue(
                jsonNode.get("data").get("content"),
                new TypeReference<List<VaultResponse>>() {
                }
        );

        assertThat(vaults).extracting(VaultResponse::getName)
                .containsExactlyInAnyOrder("Сбербанк", "Тинькофф", "Альфа-Банк");
    }

    @Test
    @DisplayName("Получение банков у разных пользователей - изолированы")
    void getAllVaultsUserIsolation() throws Exception {
        // Создаём банк для первого пользователя
        createVault("Банк Пользователя 1", "Описание 1");

        // Создаём второго пользователя
        String registerRequest2 = """
                {
                    "email": "Vaultuser2@example.com",
                    "password": "Test123%",
                    "fullName": "Second Vault User"
                }
                """;
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest2));

        User user2 = userRepository.findByEmail("Vaultuser2@example.com").get();
        mockMvc.perform(get("/api/auth/verify")
                .param("token", user2.getVerificationToken()));

        String loginRequest2 = """
                {
                    "email": "Vaultuser2@example.com",
                    "password": "Test123%"
                }
                """;
        MvcResult loginResult2 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest2))
                .andReturn();

        String responseBody2 = loginResult2.getResponse().getContentAsString();
        JsonNode jsonNode2 = objectMapper.readTree(responseBody2);
        String tokenUser2 = jsonNode2.get("data").get("token").asText();

        // Создаём банк для второго пользователя
        String requestVault2 = """
                {
                    "name": "Банк Пользователя 2",
                    "description": "Описание 2"
                }
                """;
        mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + tokenUser2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestVault2))
                .andExpect(status().isOk());

        // Проверяем банки первого пользователя
        List<VaultResponse> vaultsUser1 = getVaults();
        assertThat(vaultsUser1).hasSize(1);
        assertThat(vaultsUser1.get(0).getName()).isEqualTo("Банк Пользователя 1");

        // Проверяем банки второго пользователя
        MvcResult resultUser2 = mockMvc.perform(get("/api/vaults")
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isOk())
                .andReturn();

        String responseUser2 = resultUser2.getResponse().getContentAsString();
        JsonNode jsonNodeUser2 = objectMapper.readTree(responseUser2);
        List<VaultResponse> vaultsUser2 = objectMapper.convertValue(
                jsonNodeUser2.get("data").get("content"),
                new TypeReference<List<VaultResponse>>() {
                }
        );

        assertThat(vaultsUser2).hasSize(1);
        assertThat(vaultsUser2.get(0).getName()).isEqualTo("Банк Пользователя 2");
    }

    @Test
    @DisplayName("Обновление банка - успешно")
    void updateVaultSuccess() throws Exception {
        // Создаём банк
        String createRequest = """
                {
                    "name": "Старое название",
                    "description": "Старое описание"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andReturn();

        String createResponse = createResult.getResponse().getContentAsString();
        JsonNode createJson = objectMapper.readTree(createResponse);
        Long vaultId = createJson.get("data").get("id").asLong();

        // Обновляем банк
        String updateRequest = """
                {
                    "name": "Новое название",
                    "description": "Новое описание"
                }
                """;

        mockMvc.perform(put("/api/vaults/{vaultId}", vaultId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Хранилище успешно обновлено"))
                .andExpect(jsonPath("$.data.name").value("Новое название"))
                .andExpect(jsonPath("$.data.description").value("Новое описание"));

        // Проверяем в БД
        List<VaultResponse> vaults = getVaults();
        assertThat(vaults).hasSize(1);
        assertThat(vaults.get(0).getName()).isEqualTo("Новое название");
        assertThat(vaults.get(0).getDescription()).isEqualTo("Новое описание");
    }

    @Test
    @DisplayName("Обновление банка - только название, описание удаляется")
    void updateVaultOnlyNameSuccess() throws Exception {
        // Создаём банк
        String createRequest = """
                {
                    "name": "Старое название",
                    "description": "Старое описание"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andReturn();

        String createResponse = createResult.getResponse().getContentAsString();
        JsonNode createJson = objectMapper.readTree(createResponse);
        Long vaultId = createJson.get("data").get("id").asLong();

        // Обновляем только название
        String updateRequest = """
                {
                    "name": "Только новое название"
                }
                """;

        mockMvc.perform(put("/api/vaults/{vaultId}", vaultId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("Только новое название"))
                .andExpect(jsonPath("$.data.description").isEmpty());
    }

    @Test
    @DisplayName("Обновление несуществующего банка - ошибка")
    void updateVaultNotFoundThrowsException() throws Exception {
        String updateRequest = """
                {
                    "name": "Новое имя",
                    "description": "Новое описание"
                }
                """;

        mockMvc.perform(put("/api/vaults/{vaultId}", 99999L)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4004));
    }

    @Test
    @DisplayName("Обновление банка другого пользователя - ошибка")
    void updateVaultAnotherUserThrowsException() throws Exception {
        // Создаём банк для первого пользователя
        String createRequest = """
                {
                    "name": "Мой банк"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andReturn();

        String createResponse = createResult.getResponse().getContentAsString();
        JsonNode createJson = objectMapper.readTree(createResponse);
        Long vaultId = createJson.get("data").get("id").asLong();

        // Создаём второго пользователя
        String registerRequest2 = """
                {
                    "email": "vaultuser3@example.com",
                    "password": "Test123%",
                    "fullName": "Third Vault User"
                }
                """;
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest2));

        User user2 = userRepository.findByEmail("vaultuser3@example.com").get();
        mockMvc.perform(get("/api/auth/verify")
                .param("token", user2.getVerificationToken()));

        String loginRequest2 = """
                {
                    "email": "vaultuser3@example.com",
                    "password": "Test123%"
                }
                """;
        MvcResult loginResult2 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest2))
                .andReturn();

        String responseBody2 = loginResult2.getResponse().getContentAsString();
        JsonNode jsonNode2 = objectMapper.readTree(responseBody2);
        String tokenUser2 = jsonNode2.get("data").get("token").asText();

        // Второй пользователь пытается обновить банк первого
        String updateRequest = """
                {
                    "name": "Чужой банк"
                }
                """;

        mockMvc.perform(put("/api/vaults/{vaultId}", vaultId)
                        .header("Authorization", "Bearer " + tokenUser2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4004));
    }

    @Test
    @DisplayName("Удаление банка - успешно")
    void deleteVaultSuccess() throws Exception {
        // Создаём банк
        String createRequest = """
                {
                    "name": "Удаляемый банк"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andReturn();

        String createResponse = createResult.getResponse().getContentAsString();
        JsonNode createJson = objectMapper.readTree(createResponse);
        Long vaultId = createJson.get("data").get("id").asLong();

        // Проверяем, что банк создался
        assertThat(getVaults()).hasSize(1);

        // Удаляем банк
        mockMvc.perform(delete("/api/vaults/{vaultId}", vaultId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Хранилище успешно удалено"));

        // Проверяем, что банк удалился
        assertThat(getVaults()).isEmpty();
    }

    @Test
    @DisplayName("Удаление несуществующего банка - ошибка")
    void deleteVaultNotFoundThrowsException() throws Exception {
        mockMvc.perform(delete("/api/vaults/{vaultId}", 99999L)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4004));
    }

    @Test
    @DisplayName("Удаление банка другого пользователя - ошибка")
    void deleteVaultAnotherUserThrowsException() throws Exception {
        // Создаём банк для первого пользователя
        String createRequest = """
                {
                    "name": "Мой банк для удаления"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andReturn();

        String createResponse = createResult.getResponse().getContentAsString();
        JsonNode createJson = objectMapper.readTree(createResponse);
        Long vaultId = createJson.get("data").get("id").asLong();

        // Создаём второго пользователя
        String registerRequest2 = """
                {
                    "email": "vaultuser4@example.com",
                    "password": "Test123%",
                    "fullName": "Fourth Vault User"
                }
                """;
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest2));

        User user2 = userRepository.findByEmail("vaultuser4@example.com").get();
        mockMvc.perform(get("/api/auth/verify")
                .param("token", user2.getVerificationToken()));

        String loginRequest2 = """
                {
                    "email": "vaultuser4@example.com",
                    "password": "Test123%"
                }
                """;
        MvcResult loginResult2 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest2))
                .andReturn();

        String responseBody2 = loginResult2.getResponse().getContentAsString();
        JsonNode jsonNode2 = objectMapper.readTree(responseBody2);
        String tokenUser2 = jsonNode2.get("data").get("token").asText();

        // Второй пользователь пытается удалить банк первого
        mockMvc.perform(delete("/api/vaults/{vaultId}", vaultId)
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4004));

        // Проверяем, что банк первого пользователя остался
        assertThat(getVaults()).hasSize(1);
    }

    @Test
    @DisplayName("Попытка доступа к банкам без токена - ошибка")
    void accessWithoutTokenThrowsException() throws Exception {
        mockMvc.perform(get("/api/vaults"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Создание хранилища с дублирующимся именем (без учёта регистра) - ошибка")
    void createVaultDuplicateNameThrowsException() throws Exception {
        String request = """
                {
                    "name": "Сбербанк",
                    "description": "Первый раз"
                }
                """;

        mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        String requestDuplicate = """
                {
                    "name": "сбербанк",
                    "description": "Второй раз"
                }
                """;

        mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestDuplicate))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4005));

        List<VaultResponse> vaults = getVaults();
        assertThat(vaults).hasSize(1);
        assertThat(vaults.get(0).getDescription()).isEqualTo("Первый раз");
    }

    @Test
    @DisplayName("Создание хранилища с тем же именем в другом банке - успешно")
    void createVaultSameNameInAnotherBankSuccess() throws Exception {
        Long bankId1 = createBank("Сбер");
        Long bankId2 = createBank("Тинькофф");

        String request1 = """
                {
                    "name": "Накопительный",
                    "bankId": %d
                }
                """.formatted(bankId1);

        mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request1))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        String request2 = """
                {
                    "name": "Накопительный",
                    "bankId": %d
                }
                """.formatted(bankId2);

        mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request2))
                .andExpect(status().isOk());

        assertThat(getVaults()).hasSize(2);
    }

    // Вспомогательные методы
    private Long createBank(String name) throws Exception {
        String request = """
                {
                    "name": "%s"
                }
                """.formatted(name);

        MvcResult result = mockMvc.perform(post("/api/banks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private void createVault(String name, String description) throws Exception {
        String request = String.format("""
                {
                    "name": "%s",
                    "description": "%s"
                }
                """, name, description);

        mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    private void createVault(String name) throws Exception {
        String request = String.format("""
                {
                    "name": "%s"
                }
                """, name);

        mockMvc.perform(post("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    private List<VaultResponse> getVaults() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/vaults")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        return objectMapper.convertValue(
                jsonNode.get("data").get("content"),
                new TypeReference<List<VaultResponse>>() {
                }
        );
    }
}