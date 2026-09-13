package ru.money.goldentaurusbank.www.backend.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.TelegramLink;
import ru.money.goldentaurusbank.www.backend.model.domain.TelegramLinkCode;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.response.TelegramLinkCodeResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.TelegramLinkResponse;
import ru.money.goldentaurusbank.www.backend.repository.TelegramLinkCodeRepository;
import ru.money.goldentaurusbank.www.backend.repository.TelegramLinkRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.TELEGRAM_CODE_INVALID;
import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.TELEGRAM_LINK_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramLinkService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final Duration CODE_TTL = Duration.ofMinutes(15);

    private final TelegramLinkRepository telegramLinkRepository;
    private final TelegramLinkCodeRepository telegramLinkCodeRepository;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public TelegramLinkCodeResponse createCode(User user) {
        TelegramLinkCode code = TelegramLinkCode.builder()
                .user(user)
                .code(generateCode())
                .expiresAt(LocalDateTime.now().plus(CODE_TTL))
                .build();

        telegramLinkCodeRepository.save(code);

        return new TelegramLinkCodeResponse(code.getCode(), withZone(code.getExpiresAt()));
    }

    @Transactional(readOnly = true)
    public List<TelegramLinkResponse> links(User user) {
        return telegramLinkRepository.findByUserOrderByLinkedAtAsc(user).stream()
                .map(link -> new TelegramLinkResponse(link.getId(), link.getChatId(), withZone(link.getLinkedAt())))
                .toList();
    }

    @Transactional
    public void removeLink(User user, Long linkId) {
        TelegramLink link = telegramLinkRepository.findByIdAndUser(linkId, user)
                .orElseThrow(() -> new ApplicationException(
                        TELEGRAM_LINK_NOT_FOUND.getCode(), TELEGRAM_LINK_NOT_FOUND.getMessage()));

        telegramLinkRepository.delete(link);
    }

    @Transactional
    public User linkChat(String rawCode, Long chatId) {
        String value = rawCode == null ? "" : rawCode.trim().toUpperCase();

        TelegramLinkCode code = telegramLinkCodeRepository.findByCode(value)
                .filter(item -> item.getUsedAt() == null)
                .filter(item -> item.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new ApplicationException(
                        TELEGRAM_CODE_INVALID.getCode(), TELEGRAM_CODE_INVALID.getMessage()));

        code.setUsedAt(LocalDateTime.now());

        TelegramLink link = telegramLinkRepository.findByChatId(chatId)
                .orElseGet(() -> TelegramLink.builder().chatId(chatId).build());
        link.setUser(code.getUser());
        telegramLinkRepository.save(link);

        return code.getUser();
    }

    @Transactional
    public boolean unlinkChat(Long chatId) {
        Optional<TelegramLink> link = telegramLinkRepository.findByChatId(chatId);
        link.ifPresent(telegramLinkRepository::delete);

        return link.isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<User> userByChat(Long chatId) {
        return telegramLinkRepository.findByChatIdWithUser(chatId).map(TelegramLink::getUser);
    }

    @Transactional(readOnly = true)
    public List<TelegramLink> allLinks() {
        return telegramLinkRepository.findAll();
    }

    /**
     * В базе время лежит без пояса, в поясе сервера (в контейнере это UTC). Браузер
     * читает такую строку как своё местное время и сдвигает её на разницу поясов —
     * код объявлялся просроченным сразу после выдачи. Поэтому наружу время уходит с поясом.
     */
    private static OffsetDateTime withZone(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }

        return code.toString();
    }
}
