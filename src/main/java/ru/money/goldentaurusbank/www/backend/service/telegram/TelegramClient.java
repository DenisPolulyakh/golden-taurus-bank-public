package ru.money.goldentaurusbank.www.backend.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import ru.money.goldentaurusbank.www.backend.config.TelegramProperties;
import ru.money.goldentaurusbank.www.backend.model.dto.telegram.TelegramUpdate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class TelegramClient {

    private final TelegramProperties properties;
    private final RestClient restClient;

    public TelegramClient(TelegramProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    public List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) {
        JsonNode response = restClient.get()
                .uri(method("getUpdates") + "?offset={offset}&timeout={timeout}", offset, timeoutSeconds)
                .retrieve()
                .body(JsonNode.class);

        List<TelegramUpdate> updates = new ArrayList<>();
        if (response == null || !response.path("ok").asBoolean()) {
            return updates;
        }

        for (JsonNode node : response.path("result")) {
            updates.add(new TelegramUpdate(
                    node.path("update_id").asLong(),
                    new TelegramUpdate.TelegramMessage(
                            new TelegramUpdate.TelegramChat(node.path("message").path("chat").path("id").asLong()),
                            node.path("message").path("text").asText("")
                    )
            ));
        }

        return updates;
    }

    public void sendMessage(Long chatId, String text) {
        if (!properties.isEnabled()) {
            log.debug("Телеграм выключен, сообщение в чат {} не отправлено", chatId);
            return;
        }

        restClient.post()
                .uri(method("sendMessage"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", text))
                .retrieve()
                .toBodilessEntity();
    }

    public void sendDocument(Long chatId, String fileName, byte[] content, String caption) {
        if (!properties.isEnabled()) {
            log.debug("Телеграм выключен, файл в чат {} не отправлен", chatId);
            return;
        }

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("chat_id", chatId);
        parts.add("caption", caption);
        parts.add("document", named(fileName, content));

        restClient.post()
                .uri(method("sendDocument"))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .toBodilessEntity();
    }

    private Resource named(String fileName, byte[] content) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    private String method(String name) {
        return "%s/bot%s/%s".formatted(properties.getApiUrl(), properties.getBotToken(), name);
    }
}
