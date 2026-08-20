package ru.money.goldentaurusbank.www.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.util.StringUtils;

/**
 * Ключ шифрования номеров карт. Приложение без него не стартует намеренно:
 * с пустым или чужим ключом номера в БД превращаются в мусор молча, а
 * перешифровать их потом нечем.
 *
 * <p>{@code Encryptors.delux} — AES-256-GCM со случайным IV, то есть шифртекст
 * одного и того же номера каждый раз разный. Поэтому ни искать по номеру, ни
 * проверять уникальность на уровне SQL нельзя — для поиска рядом лежат открытые
 * последние 4 цифры.
 */
@Configuration
public class CardCryptoConfig {

    @Bean
    public TextEncryptor cardNumberEncryptor(@Value("${app.card.secret:}") String secret,
                                             @Value("${app.card.salt:}") String salt) {
        if (!StringUtils.hasText(secret) || !StringUtils.hasText(salt)) {
            throw new IllegalStateException(
                    "CARD_SECRET и CARD_SALT обязательны: без них номера карт не прочитать. "
                            + "salt должен быть hex-строкой (openssl rand -hex 8)");
        }
        return Encryptors.delux(secret, salt);
    }
}
