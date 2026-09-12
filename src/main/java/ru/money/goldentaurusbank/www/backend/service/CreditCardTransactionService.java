package ru.money.goldentaurusbank.www.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.Bullion;
import ru.money.goldentaurusbank.www.backend.model.domain.CreditCard;
import ru.money.goldentaurusbank.www.backend.model.domain.CreditCardHistory;
import ru.money.goldentaurusbank.www.backend.model.domain.Transaction;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.BullionType;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.CreditCardOperation;
import ru.money.goldentaurusbank.www.backend.model.dto.telegram.CreditCardOperationEvent;
import ru.money.goldentaurusbank.www.backend.model.dto.request.RepayFromBullionRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CreditCardHistoryResponse;
import ru.money.goldentaurusbank.www.backend.model.mapper.CreditCardMapper;
import ru.money.goldentaurusbank.www.backend.repository.BullionRepository;
import ru.money.goldentaurusbank.www.backend.repository.CreditCardHistoryRepository;
import ru.money.goldentaurusbank.www.backend.repository.CreditCardRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.*;

/**
 * Операции по кредитным картам. Отдельно от {@link TransactionService}: там
 * ноги операции — слитки и деньги реально перекладываются, здесь двигается долг
 * карты, а слитков операция обычно не касается вовсе.
 *
 * <p>Единственное исключение — погашение из накопителя: оно делает обе вещи
 * сразу и потому связывает две записи ссылкой {@code bullionTransactionId}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditCardTransactionService {

    public static final String REPAY_FROM_BULLION_COMMENT = "Погашение задолженности по карте";

    private final CreditCardRepository creditCardRepository;
    private final CreditCardHistoryRepository creditCardHistoryRepository;
    private final BullionRepository bullionRepository;
    private final TransactionService transactionService;
    private final CreditCardMapper creditCardMapper;
    private final ApplicationEventPublisher eventPublisher;

    // ------------------------------------------------------------------
    // Операции
    // ------------------------------------------------------------------

    /** Трата по карте: списание лимита, долг растёт. */
    @Transactional
    public CreditCard spend(Long cardId, BigDecimal amount, User user, String comment, LocalDateTime dateOperation) {
        CreditCard card = loadCard(cardId, user);
        record(card, CreditCardOperation.SPEND, amount, user, comment, dateOperation, null, null);
        return card;
    }

    /** Погашение задолженности. Слитки не трогает — деньги в накопитель кладутся отдельно. */
    @Transactional
    public CreditCard repay(Long cardId, BigDecimal amount, User user, String comment, LocalDateTime dateOperation) {
        CreditCard card = loadCard(cardId, user);
        record(card, CreditCardOperation.REPAY, amount, user, comment, dateOperation, null, null);
        return card;
    }

    /** Стартовая задолженность заводимой карты — регистрация уже существующего долга. */
    @Transactional
    public CreditCardHistory openingDebt(CreditCard card, BigDecimal amount, User user,
                                         String comment, LocalDateTime dateOperation) {
        return record(card, CreditCardOperation.OPENING_DEBT, amount, user, comment, dateOperation, null, null);
    }

    /**
     * Погашение деньгами из накопителя: списание со слитка плюс погашение долга,
     * обе записи в одной транзакции и связаны ссылкой. Порознь они не откатываются.
     */
    @Transactional
    public CreditCard repayFromBullion(RepayFromBullionRequest request, User user) {
        Bullion bullion = bullionRepository.findByIdAndUser(request.getBullionId(), user)
                .orElseThrow(() -> new ApplicationException(BULLION_NOT_FOUND.getCode(), BULLION_NOT_FOUND.getMessage()));

        if (bullion.getCreditCard() == null || bullion.isArchived()) {
            throw new ApplicationException(BULLION_NOT_ACCUMULATOR.getCode(), BULLION_NOT_ACCUMULATOR.getMessage());
        }

        CreditCard card = loadCard(bullion.getCreditCard().getId(), user);
        BigDecimal amount = scale(request.getAmount());
        String comment = comment(request.getComment(), REPAY_FROM_BULLION_COMMENT);

        // Порядок важен: сначала проверки карты, потом деньги. Иначе слиток
        // успел бы опустеть под операцию, которую карта всё равно не примет.
        requireRepayFits(card, amount);

        transactionService.requireExpenseAllowed(bullion);
        Transaction withdrawal = transactionService.withdraw(bullion.getId(), amount, user,
                comment, request.getDateOperation(), null);

        record(card, CreditCardOperation.REPAY, amount, user, comment,
                request.getDateOperation(), null, withdrawal.getId());

        log.info("Repay from bullion: cardId={}, bullionId={}, amount={}", card.getId(), bullion.getId(), amount);
        return card;
    }

    /**
     * Единственная точка записи в историю карты: проверяет операцию, двигает долг,
     * сохраняет запись. Неуспешная операция в историю не попадает.
     */
    private CreditCardHistory record(CreditCard card, CreditCardOperation operation, BigDecimal amount, User user,
                                     String comment, LocalDateTime dateOperation,
                                     Long reversalOfId, Long bullionTransactionId) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApplicationException(CHANGE_AMOUNT_ZERO.getCode(), CHANGE_AMOUNT_ZERO.getMessage());
        }

        BigDecimal scaled = scale(amount);
        BigDecimal debt;

        if (operation.increasesDebt()) {
            debt = scale(card.getDebt().add(scaled));
            if (debt.compareTo(card.getCardLimit()) > 0) {
                throw new ApplicationException(CREDIT_CARD_LIMIT_EXCEEDED.getCode(),
                        "Списание " + scaled + " превышает доступный лимит карты " + card.getName()
                                + ". Доступно: " + card.getRemainder());
            }
        } else {
            requireRepayFits(card, scaled);
            debt = scale(card.getDebt().subtract(scaled));
        }

        card.setDebt(debt);
        creditCardRepository.save(card);

        CreditCardHistory history = creditCardHistoryRepository.save(CreditCardHistory.builder()
                .creditCardId(card.getId())
                .userId(user.getId())
                .operation(operation)
                .amount(scaled)
                .debtAfter(debt)
                .dateOperation(dateOperation == null ? LocalDateTime.now() : dateOperation)
                .comment(comment)
                .reversalOfId(reversalOfId)
                .bullionTransactionId(bullionTransactionId)
                .build());

        log.info("Credit card operation recorded: id={}, cardId={}, operation={}, amount={}, debtAfter={}",
                history.getId(), card.getId(), operation, scaled, debt);

        eventPublisher.publishEvent(new CreditCardOperationEvent(
                user.getId(), card.getName(), card.getLast4(), operation, scaled,
                operation.increasesDebt() ? debt.subtract(scaled) : debt.add(scaled),
                debt, card.getCardLimit(), reversalOfId != null));

        return history;
    }

    private void requireRepayFits(CreditCard card, BigDecimal amount) {
        if (amount.compareTo(card.getDebt()) > 0) {
            throw new ApplicationException(CREDIT_CARD_REPAY_EXCEEDS_DEBT.getCode(),
                    "Погашение " + amount + " больше задолженности по карте " + card.getName()
                            + ". Текущая задолженность: " + card.getDebt());
        }
    }

    // ------------------------------------------------------------------
    // Откат
    // ------------------------------------------------------------------

    /**
     * Откат — обратная запись, а не удаление: арифметика долга сходится всегда,
     * более поздние операции не трогаются. Погашение из накопителя отыгрывается
     * целиком, обеими ногами.
     */
    @Transactional
    public CreditCardHistory rollback(Long historyId, User user) {
        CreditCardHistory original = creditCardHistoryRepository.findById(historyId)
                .orElseThrow(() -> new ApplicationException(
                        CREDIT_CARD_HISTORY_NOT_FOUND.getCode(), CREDIT_CARD_HISTORY_NOT_FOUND.getMessage()));

        if (!original.getUserId().equals(user.getId())) {
            throw new ApplicationException(ACCESS_DENIED.getCode(), ACCESS_DENIED.getMessage());
        }
        if (original.getReversalOfId() != null) {
            throw new ApplicationException(CREDIT_CARD_OPERATION_ALREADY_REVERSED.getCode(),
                    "Откат отката не делается");
        }
        if (creditCardHistoryRepository.existsByReversalOfId(historyId)) {
            throw new ApplicationException(CREDIT_CARD_OPERATION_ALREADY_REVERSED.getCode(),
                    CREDIT_CARD_OPERATION_ALREADY_REVERSED.getMessage());
        }

        CreditCard card = creditCardRepository.findByIdAndUser(original.getCreditCardId(), user)
                .orElseThrow(() -> new ApplicationException(
                        CREDIT_CARD_NOT_FOUND.getCode(), CREDIT_CARD_NOT_FOUND.getMessage()));

        // Вторая нога — деньги на слитке. Возвращаем её первой: если слиток
        // окажется недоступен, долг тоже останется нетронутым.
        Long reverseTransactionId = null;
        if (original.getBullionTransactionId() != null) {
            Transaction reverse = transactionService.rollbackCardLinkedTransaction(
                    original.getBullionTransactionId(), user);
            reverseTransactionId = reverse.getId();
        }

        CreditCardOperation reverseOperation = original.getOperation().increasesDebt()
                ? CreditCardOperation.REPAY
                : CreditCardOperation.SPEND;

        return record(card, reverseOperation, original.getAmount(), user,
                "Откат операции #" + historyId, LocalDateTime.now(), historyId, reverseTransactionId);
    }

    // ------------------------------------------------------------------
    // История
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CreditCardHistoryResponse> getHistory(Long cardId, User user) {
        CreditCard card = creditCardRepository.findByIdAndUser(cardId, user)
                .orElseThrow(() -> new ApplicationException(
                        CREDIT_CARD_NOT_FOUND.getCode(), CREDIT_CARD_NOT_FOUND.getMessage()));

        List<CreditCardHistory> operations =
                creditCardHistoryRepository.findByCreditCardIdOrderByDateOperationDescIdDesc(cardId);
        if (operations.isEmpty()) {
            return List.of();
        }

        Set<Long> ids = operations.stream().map(CreditCardHistory::getId).collect(Collectors.toSet());
        Map<Long, Long> reversedBy = new HashMap<>();
        for (Object[] row : creditCardHistoryRepository.findReversalsOf(ids)) {
            reversedBy.put((Long) row[0], (Long) row[1]);
        }

        return operations.stream()
                .map(operation -> creditCardMapper.toHistoryResponse(
                        operation, reversedBy.get(operation.getId()), card.getName()))
                .toList();
    }

    // ------------------------------------------------------------------

    private CreditCard loadCard(Long cardId, User user) {
        return creditCardRepository.findByIdAndUserAndArchivedFalse(cardId, user)
                .orElseThrow(() -> new ApplicationException(
                        CREDIT_CARD_NOT_FOUND.getCode(), CREDIT_CARD_NOT_FOUND.getMessage()));
    }

    /** Накопителем может быть только кредитный слиток: дебетовый — это свои деньги, их незачем «гасить». */
    public static void requireCreditBullion(Bullion bullion) {
        if (bullion.getBullionType() != BullionType.CREDIT) {
            throw new ApplicationException(BULLION_NOT_CREDIT.getCode(), BULLION_NOT_CREDIT.getMessage());
        }
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String comment(String userComment, String defaultComment) {
        return userComment == null || userComment.isBlank() ? defaultComment : userComment;
    }
}
