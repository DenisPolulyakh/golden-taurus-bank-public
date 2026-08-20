package ru.money.goldentaurusbank.www.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.Bullion;
import ru.money.goldentaurusbank.www.backend.model.domain.CreditCard;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.BullionType;
import ru.money.goldentaurusbank.www.backend.model.dto.request.CreditCardRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CreditCardListResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CreditCardResponse;
import ru.money.goldentaurusbank.www.backend.model.mapper.CreditCardMapper;
import ru.money.goldentaurusbank.www.backend.repository.BullionRepository;
import ru.money.goldentaurusbank.www.backend.repository.CreditCardRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.*;

/**
 * Кредитные карты: заведение, правка, архивация, поиск и состав накопителя.
 * Движение долга живёт в {@link CreditCardTransactionService} — здесь только
 * стартовая задолженность и корректировка из формы, и обе тоже идут операциями.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditCardService {

    public static final String OPENING_DEBT_COMMENT = "Начальная задолженность при заведении карты";
    public static final String ADJUST_UP_COMMENT = "Увеличение задолженности при корректировке карты";
    public static final String ADJUST_DOWN_COMMENT = "Уменьшение задолженности при корректировке карты";


    private final CreditCardRepository creditCardRepository;
    private final BullionRepository bullionRepository;
    private final CreditCardTransactionService creditCardTransactionService;
    private final CreditCardMapper creditCardMapper;

    // ------------------------------------------------------------------
    // Чтение
    // ------------------------------------------------------------------

    /**
     * Все карты одной страницей: пагинации нет намеренно — карт у человека
     * единицы, а листать три штуки незачем. Поиск делает БД, сортировку —
     * компаратор: остаток вычисляемый, в {@code ORDER BY} его не передать.
     */
    @Transactional(readOnly = true)
    public CreditCardListResponse getAllCards(User user, String search, String sortBy, String sortOrder) {
        List<CreditCard> cards = StringUtils.isBlank(search)
                ? creditCardRepository.findByUserAndArchivedFalse(user)
                : creditCardRepository.search(user, search.trim());

        List<CreditCard> sorted = new ArrayList<>(cards);
        sorted.sort(comparator(sortBy, sortOrder));

        return CreditCardListResponse.builder()
                // Итоги — по всем активным картам, а не по найденному:
                // в шапке стоит «Текущий долг», а не «долг найденного»
                .totalDebt(creditCardRepository.getTotalDebt(user))
                .count(creditCardRepository.countByUserAndArchivedFalse(user))
                .cards(creditCardMapper.toResponseList(sorted))
                .build();
    }

    /**
     * По умолчанию — ближайший конец льготного периода: он и решает, какую карту
     * гасить первой. Дни считаются от одной и той же «сегодня», поэтому сортировать
     * можно прямо по дате.
     */
    private Comparator<CreditCard> comparator(String sortBy, String sortOrder) {
        boolean desc = "desc".equalsIgnoreCase(sortOrder);

        if (StringUtils.isBlank(sortBy) || "grace".equals(sortBy)) {
            // Карты без льготного периода всегда в конце: «не задан» — это не
            // «много дней», и переворот направления поднимать их наверх не должен.
            // Отсюда nullsLast поверх направления, а не reversed() поверх результата.
            Comparator<LocalDate> byDate = desc ? Comparator.reverseOrder() : Comparator.naturalOrder();
            return Comparator.comparing(CreditCard::getGracePeriodDate, Comparator.nullsLast(byDate));
        }

        Comparator<CreditCard> comparator = switch (sortBy) {
            case "debt" -> Comparator.comparing(CreditCard::getDebt);
            case "limit" -> Comparator.comparing(CreditCard::getCardLimit);
            case "remainder" -> Comparator.comparing(CreditCard::getRemainder);
            default -> Comparator.comparing(CreditCard::getName, String.CASE_INSENSITIVE_ORDER);
        };
        return desc ? comparator.reversed() : comparator;
    }

    @Transactional(readOnly = true)
    public CreditCardResponse getCard(User user, Long cardId) {
        return creditCardMapper.toResponse(loadCard(user, cardId));
    }

    /**
     * Слитки для выпадающего списка накопителя: кредитные, живые и ещё никем не
     * занятые. Привязанные к самой карте добавляются отдельно — иначе при правке
     * они пропали бы из собственного списка.
     */
    @Transactional(readOnly = true)
    public List<CreditCardResponse.AccumulatorInfo> getAvailableBullions(User user, Long cardId) {
        List<Bullion> available = new ArrayList<>(
                bullionRepository.findByUserAndBullionTypeAndArchivedFalseAndCreditCardIsNull(user, BullionType.CREDIT));

        if (cardId != null) {
            available.addAll(bullionRepository.findByCreditCardIdAndArchivedFalse(cardId));
        }

        return creditCardMapper.toAccumulators(available.stream()
                .sorted(Comparator.comparing(bullion -> bullion.getBullionName().getTitle(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList());
    }

    // ------------------------------------------------------------------
    // Запись
    // ------------------------------------------------------------------

    @Transactional
    public CreditCardResponse createCard(User user, CreditCardRequest request) {
        String name = request.getName().trim();
        requireNameFree(user, name, null);

        String last4 = normalizeLast4(request.getLast4());
        BigDecimal limit = scale(request.getLimit() == null ? BigDecimal.ZERO : request.getLimit());
        BigDecimal debt = scale(request.getDebt() == null ? BigDecimal.ZERO : request.getDebt());

        if (debt.compareTo(limit) > 0) {
            throw new ApplicationException(CREDIT_CARD_LIMIT_BELOW_DEBT.getCode(),
                    CREDIT_CARD_LIMIT_BELOW_DEBT.getMessage());
        }

        CreditCard card = creditCardRepository.save(CreditCard.builder()
                .user(user)
                .name(name)
                .last4(last4)
                .gracePeriodDate(request.getGracePeriodDate())
                .cardLimit(limit)
                // Долг ставит операция, а не поле: иначе история начиналась бы
                // с суммы, взявшейся ниоткуда
                .debt(BigDecimal.ZERO)
                .build());

        if (debt.compareTo(BigDecimal.ZERO) > 0) {
            creditCardTransactionService.openingDebt(card, debt, user, OPENING_DEBT_COMMENT, LocalDateTime.now());
        }

        applyAccumulators(user, card, request.getBullionIds());

        log.info("[CreditCardService.createCard] card id = {}, last4 = {}", card.getId(), card.getLast4());
        return creditCardMapper.toResponse(card);
    }

    @Transactional
    public CreditCardResponse updateCard(User user, Long cardId, CreditCardRequest request) {
        CreditCard card = loadCard(user, cardId);

        String name = request.getName().trim();
        requireNameFree(user, name, cardId);
        card.setName(name);

        // Пустое поле — «оставить прежние цифры»
        if (StringUtils.isNotBlank(request.getLast4())) {
            card.setLast4(normalizeLast4(request.getLast4()));
        }

        card.setGracePeriodDate(request.getGracePeriodDate());

        BigDecimal targetLimit = request.getLimit() == null ? card.getCardLimit() : scale(request.getLimit());
        BigDecimal targetDebt = request.getDebt() == null ? card.getDebt() : scale(request.getDebt());

        if (targetDebt.compareTo(targetLimit) > 0) {
            throw new ApplicationException(CREDIT_CARD_LIMIT_BELOW_DEBT.getCode(),
                    "Лимит " + targetLimit + " меньше задолженности " + targetDebt);
        }

        // Лимит вверх — сначала он, чтобы под него прошло увеличение долга;
        // лимит вниз — сначала долг, иначе новый лимит не примет старую сумму
        boolean limitFirst = targetLimit.compareTo(card.getCardLimit()) > 0;
        if (limitFirst) {
            card.setCardLimit(targetLimit);
        }
        creditCardRepository.save(card);

        // Правка задолженности из формы — тоже операция: иначе долг разъезжается
        // с историей и с графиком. Тот же приём, что у суммы слитка
        adjustDebt(user, card, targetDebt);

        if (!limitFirst) {
            card.setCardLimit(targetLimit);
            creditCardRepository.save(card);
        }

        applyAccumulators(user, card, request.getBullionIds());

        return creditCardMapper.toResponse(loadCard(user, cardId));
    }

    /**
     * Карта с историей не удаляется физически — на неё ссылается
     * {@code credit_card_history}. Слитки-накопители при этом освобождаются:
     * сами по себе они живые и могут уйти в другую карту.
     */
    @Transactional
    public void deleteCard(User user, Long cardId) {
        CreditCard card = loadCard(user, cardId);

        for (Bullion bullion : bullionRepository.findByCreditCardIdAndArchivedFalse(cardId)) {
            bullion.setCreditCard(null);
            bullionRepository.save(bullion);
        }

        card.setArchived(true);
        creditCardRepository.save(card);
        log.info("[CreditCardService.deleteCard] card id = {} archived", cardId);
    }

    private void adjustDebt(User user, CreditCard card, BigDecimal targetDebt) {
        BigDecimal delta = targetDebt.subtract(card.getDebt());
        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            creditCardTransactionService.spend(card.getId(), delta, user, ADJUST_UP_COMMENT, LocalDateTime.now());
        } else if (delta.compareTo(BigDecimal.ZERO) < 0) {
            creditCardTransactionService.repay(card.getId(), delta.negate(), user, ADJUST_DOWN_COMMENT, LocalDateTime.now());
        }
    }

    // ------------------------------------------------------------------
    // Накопитель
    // ------------------------------------------------------------------

    /**
     * Состав накопителя приходит целиком: чего нет в списке — то отвязывается.
     * {@code null} означает «не трогать», пустой список — «отвязать все».
     */
    private void applyAccumulators(User user, CreditCard card, List<Long> bullionIds) {
        if (bullionIds == null) {
            return;
        }

        Set<Long> requested = new HashSet<>(bullionIds);
        List<Bullion> current = bullionRepository.findByCreditCardIdAndArchivedFalse(card.getId());

        for (Bullion bullion : current) {
            if (!requested.remove(bullion.getId())) {
                bullion.setCreditCard(null);
                bullionRepository.save(bullion);
            }
        }

        for (Long bullionId : requested) {
            Bullion bullion = bullionRepository.findByIdAndUser(bullionId, user)
                    .orElseThrow(() -> new ApplicationException(
                            BULLION_NOT_FOUND.getCode(), BULLION_NOT_FOUND.getMessage()));

            if (bullion.isArchived()) {
                throw new ApplicationException(BULLION_NOT_FOUND.getCode(),
                        "Слиток удалён и накопителем быть не может");
            }
            CreditCardTransactionService.requireCreditBullion(bullion);
            if (bullion.getCreditCard() != null && !bullion.getCreditCard().getId().equals(card.getId())) {
                throw new ApplicationException(BULLION_ALREADY_LINKED.getCode(),
                        "Слиток " + bullion.getBullionName().getTitle() + " уже привязан к другой карте");
            }

            bullion.setCreditCard(card);
            bullionRepository.save(bullion);
        }

        // Коллекция карты уже загружена — после правок она устарела
        card.setAccumulators(bullionRepository.findByCreditCardIdAndArchivedFalse(card.getId()));
    }

    // ------------------------------------------------------------------

    private CreditCard loadCard(User user, Long cardId) {
        return creditCardRepository.findByIdAndUserAndArchivedFalse(cardId, user)
                .orElseThrow(() -> new ApplicationException(
                        CREDIT_CARD_NOT_FOUND.getCode(), CREDIT_CARD_NOT_FOUND.getMessage()));
    }

    private void requireNameFree(User user, String name, Long exceptCardId) {
        boolean taken = creditCardRepository.findByUserAndArchivedFalse(user).stream()
                .filter(card -> !card.getId().equals(exceptCardId))
                .anyMatch(card -> card.getName().equalsIgnoreCase(name));
        if (taken) {
            throw new ApplicationException(CREDIT_CARD_ALREADY_EXISTS.getCode(),
                    "Карта с названием \"" + name + "\" уже существует");
        }
    }

    /**
     * Пробелы и дефисы из формы убираются, дальше — ровно четыре цифры.
     * Полный номер не принимается намеренно: хранить его негде и незачем,
     * так что обрезать молча было бы обманом — пусть форма спросит правильно.
     */
    private String normalizeLast4(String rawLast4) {
        String digits = rawLast4 == null ? "" : rawLast4.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d{4}")) {
            throw new ApplicationException(CREDIT_CARD_INVALID_NUMBER.getCode(),
                    CREDIT_CARD_INVALID_NUMBER.getMessage());
        }
        return digits;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
