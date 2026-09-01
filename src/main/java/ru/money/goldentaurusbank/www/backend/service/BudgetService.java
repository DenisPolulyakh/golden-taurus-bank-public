package ru.money.goldentaurusbank.www.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.BudgetMonth;
import ru.money.goldentaurusbank.www.backend.model.domain.Bullion;
import ru.money.goldentaurusbank.www.backend.model.domain.Transaction;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.domain.Vault;
import ru.money.goldentaurusbank.www.backend.model.dto.request.BudgetCloseRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.BudgetFundRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.BudgetPlanRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.BudgetSettingsRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.BudgetBullionDto;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.BudgetDayDto;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.BudgetMonthDto;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.BudgetYearDto;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.TransactionDto;
import ru.money.goldentaurusbank.www.backend.repository.BudgetMonthRepository;
import ru.money.goldentaurusbank.www.backend.repository.BullionRepository;
import ru.money.goldentaurusbank.www.backend.repository.TransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.*;

/**
 * Отчёт «Бюджет на месяц» по одному слитку.
 * <p>
 * Вся арифметика держится на одном тождестве: каждая операция, задевающая
 * бюджетный слиток, попадает ровно в одну корзину — либо в траты
 * ({@code budget_operation = true}), либо в движение самого бюджета
 * ({@code false}) — и с тем же знаком, с каким она подвинула слиток. Отсюда
 * <pre>остаток на конец = остаток на 1-е + движения − траты</pre>
 * выполняется при любом составе операций. Это не правило, которое надо
 * поддерживать, а следствие; расхождение — баг в подсчёте, и отчёт показывает
 * его отдельным полем, а не прячет.
 * <p>
 * Тип операции (внесение / снятие / перевод) для бюджета не значит ничего:
 * перевод внутрь слитка бывает и финансированием месяца, и возмещением траты,
 * различить их может только человек.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BullionRepository bullionRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetMonthRepository budgetMonthRepository;
    private final TransactionService transactionService;

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("LLLL yyyy");
    private static final DateTimeFormatter SHORT_MONTH_LABEL = DateTimeFormatter.ofPattern("LLLL");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd.MM");
    private static final int SCALE = 2;

    // ------------------------------------------------------------------
    // Отчёт за месяц
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public BudgetMonthDto getMonth(int year, int month, User user) {
        YearMonth period = YearMonth.of(year, month);
        Optional<Bullion> budget = bullionRepository.findByUserAndBudgetTrueAndArchivedFalse(user);

        // Слиток не выбран — отдаём пустой отчёт, а не ошибку: экран должен
        // открыться и предложить выбрать слиток, а не показать красное.
        if (budget.isEmpty()) {
            return emptyMonth(period);
        }
        return buildMonth(period, budget.get(), user);
    }

    private BudgetMonthDto buildMonth(YearMonth period, Bullion bullion, User user) {
        BigDecimal openingBalance = balanceBefore(user, bullion, period.atDay(1).atStartOfDay());
        BigDecimal controlBalance = balanceBefore(user, bullion, period.plusMonths(1).atDay(1).atStartOfDay());

        List<BudgetDayDto> days = buildDays(period, bullion, user, openingBalance);

        BigDecimal spent = sum(days, BudgetDayDto::getSpent);
        BigDecimal funding = sum(days, BudgetDayDto::getFunding);
        BigDecimal available = scaled(openingBalance.add(funding));
        BigDecimal closingBalance = scaled(available.subtract(spent));

        Optional<BudgetMonth> plan = budgetMonthRepository
                .findByUserAndYearAndMonth(user, period.getYear(), period.getMonthValue());
        BigDecimal planned = plan.map(BudgetMonth::getPlannedAmount).orElse(null);

        return BudgetMonthDto.builder()
                .year(period.getYear())
                .month(period.getMonthValue())
                .monthLabel(period.atDay(1).format(MONTH_LABEL))
                .budgetBullion(toDto(bullion))
                .sourceBullion(bullionRepository.findByUserAndBudgetSourceTrueAndArchivedFalse(user)
                        .map(this::toDto).orElse(null))
                .plannedAmount(planned)
                .planComment(plan.map(BudgetMonth::getComment).orElse(null))
                .openingBalance(openingBalance)
                .funding(funding)
                .extraFunding(planned == null ? BigDecimal.ZERO : max(BigDecimal.ZERO, funding.subtract(planned)))
                .available(available)
                .spent(spent)
                .overspend(planned == null ? null : scaled(spent.subtract(planned)))
                .closingBalance(closingBalance)
                .controlBalance(controlBalance)
                .discrepancy(scaled(closingBalance.subtract(controlBalance)))
                .bullionAmount(bullion.getAmount())
                .days(days)
                .build();
    }

    /**
     * Строка на каждый календарный день месяца, включая пустые: иначе номера
     * дней идут с пропусками, как «транши» в Excel, и глазами по ним не пройти.
     */
    private List<BudgetDayDto> buildDays(YearMonth period, Bullion bullion, User user, BigDecimal openingBalance) {
        Map<Integer, Object[]> byDay = new HashMap<>();
        for (Object[] row : transactionRepository.getBudgetDailyStatistics(
                user.getId(), bullion.getId(), period.getYear(), period.getMonthValue())) {
            byDay.put(toInt(row[0]), row);
        }

        List<BudgetDayDto> days = new ArrayList<>(period.lengthOfMonth());
        BigDecimal balance = openingBalance;

        for (int day = 1; day <= period.lengthOfMonth(); day++) {
            Object[] row = byDay.get(day);

            BigDecimal taken = row == null ? BigDecimal.ZERO : toAmount(row[1]);
            BigDecimal returned = row == null ? BigDecimal.ZERO : toAmount(row[2]);
            BigDecimal compensated = row == null ? BigDecimal.ZERO : toAmount(row[3]);
            BigDecimal funding = row == null ? BigDecimal.ZERO : toAmount(row[4]);
            long count = row == null ? 0L : toLong(row[5]);

            BigDecimal spent = scaled(taken.subtract(returned).subtract(compensated));
            balance = scaled(balance.add(funding).subtract(spent));

            days.add(BudgetDayDto.builder()
                    .day(day)
                    .date(period.atDay(day).format(DAY_LABEL))
                    .taken(taken)
                    .returned(returned)
                    .compensated(compensated)
                    .spent(spent)
                    .funding(funding)
                    .balance(balance)
                    .transactionCount(count)
                    .build());
        }
        return days;
    }

    private BudgetMonthDto emptyMonth(YearMonth period) {
        return BudgetMonthDto.builder()
                .year(period.getYear())
                .month(period.getMonthValue())
                .monthLabel(period.atDay(1).format(MONTH_LABEL))
                .openingBalance(BigDecimal.ZERO)
                .funding(BigDecimal.ZERO)
                .extraFunding(BigDecimal.ZERO)
                .available(BigDecimal.ZERO)
                .spent(BigDecimal.ZERO)
                .closingBalance(BigDecimal.ZERO)
                .controlBalance(BigDecimal.ZERO)
                .discrepancy(BigDecimal.ZERO)
                .bullionAmount(BigDecimal.ZERO)
                .days(List.of())
                .build();
    }

    /**
     * Операции бюджетного слитка за один день — то, что раскрывается кликом по
     * строке таблицы. Здесь видно хранилище, комментарий и контрагента, то есть
     * всё, что в Excel приходилось вбивать руками в колонку «Источник».
     */
    @Transactional(readOnly = true)
    public List<TransactionDto> getDayTransactions(int year, int month, int day, User user) {
        Optional<Bullion> budget = bullionRepository.findByUserAndBudgetTrueAndArchivedFalse(user);
        if (budget.isEmpty()) {
            return List.of();
        }
        LocalDateTime from = LocalDate.of(year, month, day).atStartOfDay();
        return transactionService.toDtos(transactionRepository.findBudgetTransactionsBetween(
                user.getId(), budget.get().getId(), from, from.plusDays(1)));
    }

    // ------------------------------------------------------------------
    // Годовая сводка
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public BudgetYearDto getYear(int year, User user) {
        Optional<Bullion> budget = bullionRepository.findByUserAndBudgetTrueAndArchivedFalse(user);
        if (budget.isEmpty()) {
            return BudgetYearDto.builder()
                    .year(year)
                    .months(emptyYearRows(year))
                    .totalPlanned(BigDecimal.ZERO)
                    .totalFunding(BigDecimal.ZERO)
                    .totalSpent(BigDecimal.ZERO)
                    .build();
        }

        Bullion bullion = budget.get();
        Map<Integer, Object[]> byMonth = new HashMap<>();
        for (Object[] row : transactionRepository.getBudgetMonthlyStatistics(user.getId(), bullion.getId(), year)) {
            byMonth.put(toInt(row[0]), row);
        }
        Map<Integer, BudgetMonth> plans = new HashMap<>();
        for (BudgetMonth plan : budgetMonthRepository.findByUserAndYearOrderByMonthAsc(user, year)) {
            plans.put(plan.getMonth(), plan);
        }

        // Остаток тянется из месяца в месяц: не закрыл месяц — остаток стал
        // началом следующего. Стартуем от фактического остатка на 1 января.
        BigDecimal balance = balanceBefore(user, bullion, LocalDate.of(year, 1, 1).atStartOfDay());

        List<BudgetYearDto.BudgetYearMonthDto> months = new ArrayList<>(12);
        BigDecimal totalPlanned = BigDecimal.ZERO;
        BigDecimal totalFunding = BigDecimal.ZERO;
        BigDecimal totalSpent = BigDecimal.ZERO;

        for (int month = 1; month <= 12; month++) {
            Object[] row = byMonth.get(month);
            BigDecimal spent = row == null ? BigDecimal.ZERO : toAmount(row[1]);
            BigDecimal funding = row == null ? BigDecimal.ZERO : toAmount(row[2]);
            long count = row == null ? 0L : toLong(row[3]);

            BigDecimal planned = Optional.ofNullable(plans.get(month))
                    .map(BudgetMonth::getPlannedAmount).orElse(null);

            balance = scaled(balance.add(funding).subtract(spent));
            totalFunding = totalFunding.add(funding);
            totalSpent = totalSpent.add(spent);
            if (planned != null) {
                totalPlanned = totalPlanned.add(planned);
            }

            months.add(BudgetYearDto.BudgetYearMonthDto.builder()
                    .month(month)
                    .monthLabel(LocalDate.of(year, month, 1).format(SHORT_MONTH_LABEL))
                    .plannedAmount(planned)
                    .funding(funding)
                    .spent(spent)
                    .overspend(planned == null ? null : scaled(spent.subtract(planned)))
                    .closingBalance(balance)
                    .transactionCount(count)
                    .build());
        }

        return BudgetYearDto.builder()
                .year(year)
                .budgetBullion(toDto(bullion))
                .months(months)
                .totalPlanned(scaled(totalPlanned))
                .totalFunding(scaled(totalFunding))
                .totalSpent(scaled(totalSpent))
                .build();
    }

    private List<BudgetYearDto.BudgetYearMonthDto> emptyYearRows(int year) {
        List<BudgetYearDto.BudgetYearMonthDto> months = new ArrayList<>(12);
        for (int month = 1; month <= 12; month++) {
            months.add(BudgetYearDto.BudgetYearMonthDto.builder()
                    .month(month)
                    .monthLabel(LocalDate.of(year, month, 1).format(SHORT_MONTH_LABEL))
                    .funding(BigDecimal.ZERO)
                    .spent(BigDecimal.ZERO)
                    .closingBalance(BigDecimal.ZERO)
                    .transactionCount(0L)
                    .build());
        }
        return months;
    }

    // ------------------------------------------------------------------
    // Настройки: какой слиток бюджетный и откуда его финансировать
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public BudgetMonthDto getSettings(User user) {
        LocalDate now = LocalDate.now();
        return getMonth(now.getYear(), now.getMonthValue(), user);
    }

    /**
     * Назначение слитков. {@code null} в поле снимает признак и ничего не
     * назначает взамен — так отчёт отвязывается от слитка.
     * <p>
     * Признак снимается со старого слитка до того, как ставится новому: иначе
     * частичный уникальный индекс увидит два бюджетных слитка сразу.
     */
    @Transactional
    public BudgetMonthDto saveSettings(BudgetSettingsRequest request, User user) {
        if (request.getBudgetBullionId() != null
                && request.getBudgetBullionId().equals(request.getSourceBullionId())) {
            throw new ApplicationException(BUDGET_SAME_BULLION.getCode(), BUDGET_SAME_BULLION.getMessage());
        }

        bullionRepository.findByUserAndBudgetTrueAndArchivedFalse(user).ifPresent(old -> {
            old.setBudget(false);
            bullionRepository.save(old);
        });
        bullionRepository.findByUserAndBudgetSourceTrueAndArchivedFalse(user).ifPresent(old -> {
            old.setBudgetSource(false);
            bullionRepository.save(old);
        });
        bullionRepository.flush();

        if (request.getBudgetBullionId() != null) {
            Bullion bullion = loadBullion(request.getBudgetBullionId(), user);
            bullion.setBudget(true);
            bullionRepository.save(bullion);
        }
        if (request.getSourceBullionId() != null) {
            Bullion source = loadBullion(request.getSourceBullionId(), user);
            source.setBudgetSource(true);
            bullionRepository.save(source);
        }

        log.info("Budget settings saved: userId={}, budgetBullionId={}, sourceBullionId={}",
                user.getId(), request.getBudgetBullionId(), request.getSourceBullionId());

        LocalDate now = LocalDate.now();
        return getMonth(now.getYear(), now.getMonthValue(), user);
    }

    // ------------------------------------------------------------------
    // План месяца
    // ------------------------------------------------------------------

    @Transactional
    public BudgetMonthDto setPlan(int year, int month, BudgetPlanRequest request, User user) {
        YearMonth.of(year, month); // валидация месяца до записи в БД

        BudgetMonth plan = budgetMonthRepository.findByUserAndYearAndMonth(user, year, month)
                .orElseGet(() -> BudgetMonth.builder()
                        .user(user)
                        .year(year)
                        .month(month)
                        .build());

        plan.setPlannedAmount(request.getPlannedAmount().setScale(SCALE, RoundingMode.HALF_UP));
        plan.setComment(request.getComment());
        budgetMonthRepository.save(plan);

        return getMonth(year, month, user);
    }

    // ------------------------------------------------------------------
    // Финансирование и закрытие месяца
    // ------------------------------------------------------------------

    /**
     * Положить денег на бюджетный слиток. Пустой запрос означает «добрать до
     * плана 1-го числа»; явная сумма — докинуть среди месяца, в том числе сверх
     * плана: план это ориентир, а не лимит, и упереться в него законно.
     */
    @Transactional
    public BudgetMonthDto fund(int year, int month, BudgetFundRequest request, User user) {
        YearMonth period = YearMonth.of(year, month);
        Bullion budget = requireBudgetBullion(user);

        Bullion source = request.getSourceBullionId() != null
                ? loadBullion(request.getSourceBullionId(), user)
                : bullionRepository.findByUserAndBudgetSourceTrueAndArchivedFalse(user)
                        .orElseThrow(() -> new ApplicationException(
                                BUDGET_SOURCE_NOT_SET.getCode(), BUDGET_SOURCE_NOT_SET.getMessage()));

        if (source.getId().equals(budget.getId())) {
            throw new ApplicationException(BUDGET_SAME_BULLION.getCode(), BUDGET_SAME_BULLION.getMessage());
        }

        boolean topUpToPlan = request.getAmount() == null;
        BigDecimal amount = topUpToPlan ? amountToReachPlan(period, budget, user) : request.getAmount();

        // Добор до плана датируется 1-м числом, докидывание — «сейчас».
        // Иначе колонка «Остаток» покажет деньги там, где их ещё не было, и
        // день, когда бюджет реально кончился, пропадёт с графика.
        LocalDateTime dateOperation = request.getDateOperation() != null
                ? request.getDateOperation()
                : (topUpToPlan ? period.atDay(1).atStartOfDay() : LocalDateTime.now());

        transactionService.transfer(source.getId(), budget.getId(), amount, user,
                comment(request.getComment(), "Финансирование бюджета за " + period.atDay(1).format(MONTH_LABEL)),
                dateOperation, null, false);

        log.info("Budget funded: userId={}, period={}-{}, amount={}, sourceBullionId={}",
                user.getId(), year, month, amount, source.getId());

        return getMonth(year, month, user);
    }

    private BigDecimal amountToReachPlan(YearMonth period, Bullion budget, User user) {
        BigDecimal planned = budgetMonthRepository
                .findByUserAndYearAndMonth(user, period.getYear(), period.getMonthValue())
                .map(BudgetMonth::getPlannedAmount)
                .orElseThrow(() -> new ApplicationException(
                        BUDGET_PLAN_NOT_SET.getCode(), BUDGET_PLAN_NOT_SET.getMessage()));

        BigDecimal funding = sum(buildDays(period, budget, user, BigDecimal.ZERO), BudgetDayDto::getFunding);
        BigDecimal missing = scaled(planned.subtract(funding));

        if (missing.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApplicationException(BUDGET_ALREADY_FUNDED.getCode(), BUDGET_ALREADY_FUNDED.getMessage());
        }
        return missing;
    }

    /**
     * Увести остаток месяца на другой слиток. Не автоматизируем и не делаем
     * обязательным: остаток можно и оставить, тогда он станет началом следующего
     * месяца — так у бюджета появляется переходящий хвост, и это осознанный выбор.
     */
    @Transactional
    public BudgetMonthDto closeMonth(int year, int month, BudgetCloseRequest request, User user) {
        YearMonth period = YearMonth.of(year, month);
        Bullion budget = requireBudgetBullion(user);
        Bullion target = loadBullion(request.getTargetBullionId(), user);

        if (target.getId().equals(budget.getId())) {
            throw new ApplicationException(BUDGET_SAME_BULLION.getCode(), BUDGET_SAME_BULLION.getMessage());
        }

        BigDecimal remainder = buildMonth(period, budget, user).getClosingBalance();
        if (remainder.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApplicationException(BUDGET_NOTHING_TO_CLOSE.getCode(), BUDGET_NOTHING_TO_CLOSE.getMessage());
        }

        LocalDateTime dateOperation = request.getDateOperation() != null
                ? request.getDateOperation()
                : period.atEndOfMonth().atTime(23, 59);

        transactionService.transfer(budget.getId(), target.getId(), remainder, user,
                comment(request.getComment(), "Остаток бюджета за " + period.atDay(1).format(MONTH_LABEL)),
                dateOperation, null, false);

        log.info("Budget month closed: userId={}, period={}-{}, remainder={}, targetBullionId={}",
                user.getId(), year, month, remainder, target.getId());

        return getMonth(year, month, user);
    }

    // ------------------------------------------------------------------
    // Правка записанных операций
    // ------------------------------------------------------------------

    /**
     * Откат операции прямо из отчёта. Сумму слитка отыгрывает обратная
     * транзакция внутри {@link TransactionService} — здесь только проверка, что
     * операция действительно бюджетная, и пересчёт отчёта для ответа.
     */
    @Transactional
    public BudgetMonthDto rollbackOperation(Long transactionId, int year, int month, User user) {
        Transaction original = loadTransaction(transactionId, user);
        requireTouchesBudget(original, requireBudgetBullion(user));

        transactionService.rollbackTransaction(transactionId, user);
        return getMonth(year, month, user);
    }

    /**
     * Переложить операцию в другую корзину бюджета. Денег не двигает: меняется
     * только классификация, суммы слитков остаются как есть — тождество сходится
     * при любом распределении по корзинам.
     * <p>
     * Откат операции переразмечается вместе с ней. Иначе трата уйдёт в одну
     * корзину, а её откат останется в другой — единственный способ развалить
     * отчёт этой ручкой.
     */
    @Transactional
    public BudgetMonthDto setBucket(Long transactionId, boolean budgetOperation, User user) {
        Transaction transaction = loadTransaction(transactionId, user);
        requireTouchesBudget(transaction, requireBudgetBullion(user));

        for (Transaction related : transactionRepository
                .findByReversalOfIdOrIdOrderByCreatedAtAsc(transactionId, transactionId)) {
            related.setBudgetOperation(budgetOperation);
            transactionRepository.save(related);
        }
        if (transaction.getReversalOfId() != null) {
            transactionRepository.findById(transaction.getReversalOfId()).ifPresent(origin -> {
                origin.setBudgetOperation(budgetOperation);
                transactionRepository.save(origin);
            });
        }

        log.info("Budget bucket changed: userId={}, transactionId={}, budgetOperation={}",
                user.getId(), transactionId, budgetOperation);

        LocalDateTime date = transaction.getDateOperation();
        return getMonth(date.getYear(), date.getMonthValue(), user);
    }

    // ------------------------------------------------------------------
    // Вспомогательное
    // ------------------------------------------------------------------

    private Bullion requireBudgetBullion(User user) {
        return bullionRepository.findByUserAndBudgetTrueAndArchivedFalse(user)
                .orElseThrow(() -> new ApplicationException(
                        BUDGET_BULLION_NOT_SET.getCode(), BUDGET_BULLION_NOT_SET.getMessage()));
    }

    private void requireTouchesBudget(Transaction transaction, Bullion budget) {
        boolean touches = budget.getId().equals(transaction.getSourceBullionId())
                || budget.getId().equals(transaction.getTargetBullionId());
        if (!touches) {
            throw new ApplicationException(TRANSACTION_NOT_BUDGET.getCode(), TRANSACTION_NOT_BUDGET.getMessage());
        }
    }

    private Transaction loadTransaction(Long transactionId, User user) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ApplicationException(
                        TRANSACTION_NOT_FOUND.getCode(), TRANSACTION_NOT_FOUND.getMessage()));
        if (!transaction.getUserId().equals(user.getId())) {
            throw new ApplicationException(ACCESS_DENIED.getCode(), ACCESS_DENIED.getMessage());
        }
        return transaction;
    }

    private Bullion loadBullion(Long bullionId, User user) {
        return bullionRepository.findByIdAndUser(bullionId, user)
                .orElseThrow(() -> new ApplicationException(BULLION_NOT_FOUND.getCode(), BULLION_NOT_FOUND.getMessage()));
    }

    private BigDecimal balanceBefore(User user, Bullion bullion, LocalDateTime before) {
        BigDecimal balance = transactionRepository.getBudgetBalanceBefore(user.getId(), bullion.getId(), before);
        return scaled(balance == null ? BigDecimal.ZERO : balance);
    }

    private BudgetBullionDto toDto(Bullion bullion) {
        Vault vault = bullion.getVault();
        return BudgetBullionDto.builder()
                .id(bullion.getId())
                .title(bullion.getBullionName() == null ? null : bullion.getBullionName().getTitle())
                .vaultName(vault == null ? null : vault.getName())
                .amount(bullion.getAmount())
                .archived(bullion.isArchived())
                .build();
    }

    private static String comment(String userComment, String fallback) {
        return userComment == null || userComment.isBlank() ? fallback : userComment;
    }

    private static BigDecimal sum(List<BudgetDayDto> days, java.util.function.Function<BudgetDayDto, BigDecimal> field) {
        BigDecimal total = BigDecimal.ZERO;
        for (BudgetDayDto day : days) {
            total = total.add(field.apply(day));
        }
        return scaled(total);
    }

    private static BigDecimal max(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static int toInt(Object value) {
        return ((Number) value).intValue();
    }

    private static long toLong(Object value) {
        return ((Number) value).longValue();
    }

    private static BigDecimal toAmount(Object value) {
        return value == null ? BigDecimal.ZERO : scaled(new BigDecimal(value.toString()));
    }
}
