package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Интеграционные тесты аутентификации")
class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Успешная регистрация пользователя")
    void registerSuccess() throws Exception {
        String request = """
                {
                    "email": "test@example.com",
                    "password": "123456%",
                    "fullName": "Test User"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Пользователь успешно зарегистрирован. Проверьте почту для подтверждения."));

        assertThat(userRepository.findByEmail("test@example.com")).isPresent();
        User savedUser = userRepository.findByEmail("test@example.com").get();
        assertThat(savedUser.getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.getFullName()).isEqualTo("Test User");
        assertThat(savedUser.isEmailVerified()).isFalse();
        assertThat(savedUser.getVerificationToken()).isNotNull();
    }

    @Test
    @DisplayName("Регистрация с уже существующим email - ошибка")
    void registerEmailAlreadyExistsThrowsException() throws Exception {
        String request = """
                {
                    "email": "existing@example.com",
                    "password": "123456%",
                    "fullName": "Existing User"
                }
                """;
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1004))
                .andExpect(jsonPath("$.message").value("Пользователь с таким email уже существует"));
    }

    @Test
    @DisplayName("Регистрация с невалидными данными - ошибка валидации")
    void registerInvalidDataValidationError() throws Exception {
        String request = """
                {
                    "email": "not-an-email",
                    "password": "123",
                    "fullName": ""
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @DisplayName("Успешное подтверждение email")
    void verifyEmailSuccess() throws Exception {
        String registerRequest = """
                {
                    "email": "verify@example.com",
                    "password": "123456%",
                    "fullName": "Verify User"
                }
                """;
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        User user = userRepository.findByEmail("verify@example.com").get();
        String token = user.getVerificationToken();

        mockMvc.perform(get("/api/auth/verify")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Email успешно подтверждён!"));

        User verifiedUser = userRepository.findByEmail("verify@example.com").get();
        assertThat(verifiedUser.isEmailVerified()).isTrue();
        // Токен намеренно сохраняется, чтобы повторный переход по ссылке был идемпотентным
        assertThat(verifiedUser.getVerificationToken()).isNotNull();
    }

    @Test
    @DisplayName("Повторное подтверждение по той же ссылке - успех (идемпотентность)")
    void verifyEmailTwiceIsIdempotent() throws Exception {
        String registerRequest = """
                {
                    "email": "verify-twice@example.com",
                    "password": "123456%",
                    "fullName": "Verify Twice User"
                }
                """;
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        User user = userRepository.findByEmail("verify-twice@example.com").get();
        String token = user.getVerificationToken();

        // Первый переход по ссылке
        mockMvc.perform(get("/api/auth/verify")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Email успешно подтверждён!"));

        // Повторный переход по той же ссылке - тоже успех, без ошибки
        mockMvc.perform(get("/api/auth/verify")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Email успешно подтверждён!"));

        User verifiedUser = userRepository.findByEmail("verify-twice@example.com").get();
        assertThat(verifiedUser.isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("Подтверждение email с неверным токеном - ошибка")
    void verifyEmailInvalidTokenThrowsException() throws Exception {
        mockMvc.perform(get("/api/auth/verify")
                        .param("token", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("Неверный или просроченный токен верификации"));
    }

    @Test
    @DisplayName("Успешный вход после подтверждения email")
    void loginSuccessAfterVerification() throws Exception {
        String registerRequest = """
                {
                    "email": "login@example.com",
                    "password": "123456%",
                    "fullName": "Login User"
                }
                """;
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        User user = userRepository.findByEmail("login@example.com").get();
        mockMvc.perform(get("/api/auth/verify")
                .param("token", user.getVerificationToken()));

        String loginRequest = """
                {
                    "email": "login@example.com",
                    "password": "123456%"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.email").value("login@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("Login User"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        String token = jsonNode.get("data").get("token").asText();
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("Вход без подтверждения email - ошибка")
    void loginWithoutEmailVerificationThrowsException() throws Exception {
        String registerRequest = """
                {
                    "email": "unverified@example.com",
                    "password": "123456%",
                    "fullName": "Unverified User"
                }
                """;
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        String loginRequest = """
                {
                    "email": "unverified@example.com",
                    "password": "123456%"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("Аккаунт не активирован. Подтвердите email, перейдя по ссылке в письме."));
    }

    @Test
    @DisplayName("Вход с неверным паролем - ошибка")
    void loginWrongPasswordThrowsException() throws Exception {
        String registerRequest = """
                {
                    "email": "wrongpass@example.com",
                    "password": "123456%",
                    "fullName": "Wrong Password User"
                }
                """;
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        User user = userRepository.findByEmail("wrongpass@example.com").get();
        mockMvc.perform(get("/api/auth/verify")
                .param("token", user.getVerificationToken()));

        String loginRequest = """
                {
                    "email": "wrongpass@example.com",
                    "password": "wrongpassword"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("Неверный email или пароль"));
    }

    @Test
    @DisplayName("Повторная отправка письма для верификации")
    void resendVerificationSuccess() throws Exception {
        String registerRequest = """
                {
                    "email": "resend@example.com",
                    "password": "123456%",
                    "fullName": "Resend User"
                }
                """;
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest));

        User user = userRepository.findByEmail("resend@example.com").get();
        String oldToken = user.getVerificationToken();

        mockMvc.perform(post("/api/auth/resend-verification")
                        .param("email", "resend@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Письмо с подтверждением отправлено повторно"));

        User updatedUser = userRepository.findByEmail("resend@example.com").get();
        assertThat(updatedUser.getVerificationToken()).isNotEqualTo(oldToken);
    }

    @Test
    @DisplayName("Битый или протухший токен - 401, чтобы фронт обновил токен или ушёл на логин")
    void invalidOrExpiredTokenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/vaults")
                        .header("Authorization", "Bearer это-не-токен"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(3002));

        // Настоящая подпись, но срок вышел час назад — как после долгого отсутствия
        long now = System.currentTimeMillis();
        String expiredToken = Jwts.builder()
                .subject("1")
                .claim("email", "ghost@example.com")
                .claim("role", "USER")
                .issuedAt(new Date(now - 7_200_000))
                .expiration(new Date(now - 3_600_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/vaults")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(3002));
    }

    @Test
    @DisplayName("Вход с несуществующим email - ошибка")
    void loginUserNotFoundThrowsException() throws Exception {
        String loginRequest = """
                {
                    "email": "nonexistent@example.com",
                    "password": "123456%"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("Неверный email или пароль"));
    }
}