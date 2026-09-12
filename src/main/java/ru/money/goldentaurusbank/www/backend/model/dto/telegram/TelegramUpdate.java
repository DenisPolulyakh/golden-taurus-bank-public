package ru.money.goldentaurusbank.www.backend.model.dto.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(@JsonProperty("update_id") Long updateId, TelegramMessage message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramMessage(TelegramChat chat, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramChat(Long id) {
    }

    public Long chatId() {
        return message == null || message.chat() == null ? null : message.chat().id();
    }

    public String text() {
        return message == null || message.text() == null ? "" : message.text().trim();
    }
}
