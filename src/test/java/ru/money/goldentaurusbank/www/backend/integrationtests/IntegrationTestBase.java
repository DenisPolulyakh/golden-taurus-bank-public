package ru.money.goldentaurusbank.www.backend.integrationtests;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Общая обвязка интеграционных тестов: один PostgreSQL и один контекст Spring на
 * весь прогон вместо шести (см. plans/planCICD.md, п. 10).
 */
abstract class IntegrationTestBase {

    /** Все таблицы схемы; порядок неважен — CASCADE сам разберётся со связями. */
    private static final String TRUNCATE_ALL = "TRUNCATE TABLE "
            + "taurus.transactions, taurus.bullions, taurus.vaults, "
            + "taurus.bullion_names, taurus.bank_dictionary, taurus.users "
            + "RESTART IDENTITY CASCADE";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * База теперь общая на весь прогон, поэтому каждый тест начинает с пустой схемы.
     * Без этого до чужих строк добирался {@code userRepository.deleteAll()} из
     * {@code @BeforeEach} тест-классов: Hibernate пытался занулить {@code user_id}
     * в связанных таблицах и ронял транзакцию о NOT NULL. Заодно порядок классов
     * перестаёт что-либо значить.
     *
     * <p>Выполняется раньше {@code @BeforeEach} наследников — так их подготовка
     * данных ложится уже на чистое.
     */
    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute(TRUNCATE_ALL);
    }

    /**
     * Контейнер поднимается один раз на всю JVM и живёт до её конца. Поэтому здесь
     * нет ни {@code @Testcontainers}, ни {@code @Container}: эта пара останавливает
     * контейнер после каждого тест-класса, то есть поднимала бы PostgreSQL заново
     * шесть раз. Убирает за собой Ryuk — контейнер-сторож самого Testcontainers.
     */
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    static {
        POSTGRES.start();
    }

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig()
                    .withUser("test@greenmail.com", "test"))
            .withPerMethodLifecycle(false);

    /**
     * Метод обязан быть ровно один на все тесты. Spring кладёт {@code @DynamicPropertySource}
     * в ключ кэша контекста, сравнивая сами методы: шесть копий в шести классах давали
     * шесть разных ключей и, значит, шесть поднятий приложения.
     */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.default-schema", () -> "taurus");
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> GREEN_MAIL.getSmtp().getPort());
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
}
