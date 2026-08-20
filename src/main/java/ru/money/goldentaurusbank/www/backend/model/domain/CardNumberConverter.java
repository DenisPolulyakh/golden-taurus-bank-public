package ru.money.goldentaurusbank.www.backend.model.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * Шифрует номер карты по дороге в БД и расшифровывает обратно, поэтому в коде
 * номер всегда открытый, а в таблице — всегда шифртекст.
 *
 * <p>Конвертер — Spring-бин: Boot отдаёт Hibernate {@code SpringBeanContainer},
 * и конструктор с зависимостью отрабатывает. Конструктора без аргументов здесь
 * нет намеренно — если контейнер бинов вдруг отвалится, приложение упадёт на
 * старте, а не запишет номера открытым текстом.
 */
@Component
@Converter
@RequiredArgsConstructor
public class CardNumberConverter implements AttributeConverter<String, String> {

    private final TextEncryptor cardNumberEncryptor;

    @Override
    public String convertToDatabaseColumn(String cardNumber) {
        return cardNumber == null ? null : cardNumberEncryptor.encrypt(cardNumber);
    }

    @Override
    public String convertToEntityAttribute(String encrypted) {
        return encrypted == null ? null : cardNumberEncryptor.decrypt(encrypted);
    }
}
