package ru.money.goldentaurusbank.www.backend.service.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.response.EmergencyPackageResponse;
import ru.money.goldentaurusbank.www.backend.service.CardRequisitesService;
import ru.money.goldentaurusbank.www.backend.service.CreditCardService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramCommandService {

    private static final String PACKAGE_CAPTION = """
            Аварийный пакет: долги и запертый сундук.
            Без фразы бесполезен — открывается только открывашкой.""";

    private final CreditCardService creditCardService;
    private final CardRequisitesService cardRequisitesService;
    private final TelegramSummaryBuilder summaryBuilder;
    private final TelegramClient telegramClient;
    private final ObjectMapper objectMapper;

    public void sendSummary(User user, Long chatId) {
        telegramClient.sendMessage(chatId,
                summaryBuilder.build(creditCardService.getAllCards(user, null, null, null)));
    }

    public void sendEmergencyPackage(User user, Long chatId) {
        EmergencyPackageResponse emergencyPackage = cardRequisitesService.buildEmergencyPackage(user);

        try {
            byte[] content = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(emergencyPackage);

            telegramClient.sendDocument(chatId,
                    "taurus-package-%s.json".formatted(LocalDate.now()), content, PACKAGE_CAPTION);
        } catch (Exception e) {
            log.error("Не удалось отправить аварийный пакет в чат {}", chatId, e);
            telegramClient.sendMessage(chatId, "Не получилось собрать пакет, попробуйте позже");
        }
    }
}
