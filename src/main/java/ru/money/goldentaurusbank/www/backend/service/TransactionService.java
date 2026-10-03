package ru.money.goldentaurusbank.www.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.*;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.IncomeType;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.TransactionKind;
import ru.money.goldentaurusbank.www.backend.model.dto.request.RefillBullionRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.UpdateIncomeTypeRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.TransferRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.WithdrawBullionRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.PageResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.*;
import ru.money.goldentaurusbank.www.backend.repository.BullionRepository;
import ru.money.goldentaurusbank.www.backend.repository.CreditCardHistoryRepository;
import ru.money.goldentaurusbank.www.backend.repository.CreditCardRepository;
import ru.money.goldentaurusbank.www.backend.repository.TransactionRepository;
import ru.money.goldentaurusbank.www.backend.repository.VaultRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final BullionRepository bullionRepository;
    private final VaultRepository vaultRepository;
    private final TransactionRepository transactionRepository;
    // Только репозиторий, не сервис карт: обратная зависимость шла бы в цикл —
    // CreditCardTransactionService уже зависит от этого сервиса
    private final CreditCardHistoryRepository creditCardHistoryRepository;
    private final CreditCardRepository creditCardRepository;
    private final ColorConstants colorConstants;

    private static final String BANK_COLOR = "#FFA502";  // оранжевый
    private static final String VAULT_COLOR = "#FFD93D"; // жёлтый
    private static final LocalDateTime EPOCH = LocalDate.of(1970, 1, 1).atStartOfDay();

    // ------------------------------------------------------------------
    // Запись операций
    // ------------------------------------------------------------------

    @Transactional
    public Transaction deposit(Long targetBullionId, BigDecimal amount, User user,
                               String comment, LocalDateTime dateOperation, Long batchId) {
        return deposit(targetBullionId, amount, user, comment, dateOperation, batchId, true);
    }

    @Transactional
    public Transaction deposit(Long targetBullionId, BigDecimal amount, User user,
                               String comment, LocalDateTime dateOperation, Long batchId,
                               boolean budgetOperation) {
        return deposit(targetBullionId, amount, user, comment, dateOperation, batchId, budgetOperation, null);
    }

    @Transactional
    public Transaction deposit(Long targetBullionId, BigDecimal amount, User user,
                               String comment, LocalDateTime dateOperation, Long batchId,
                               boolean budgetOperation, IncomeType incomeType) {
        return record(null, targetBullionId, amount, user, comment, dateOperation, batchId, false, false, null, budgetOperation, incomeType);
    }

    @Transactional
    public Transaction withdraw(Long sourceBullionId, BigDecimal amount, User user,
                                String comment, LocalDateTime dateOperation, Long batchId) {
        return withdraw(sourceBullionId, amount, user, comment, dateOperation, batchId, true);
    }

    @Transactional
    public Transaction withdraw(Long sourceBullionId, BigDecimal amount, User user,
                                String comment, LocalDateTime dateOperation, Long batchId,
                                boolean budgetOperation) {
        return record(sourceBullionId, null, amount, user, comment, dateOperation, batchId, false, false, null, budgetOperation, null);
    }

    @Transactional
    public Transaction transfer(Long sourceBullionId, Long targetBullionId, BigDecimal amount, User user,
                                String comment, LocalDateTime dateOperation, Long batchId) {
        return transfer(sourceBullionId, targetBullionId, amount, user, comment, dateOperation, batchId, true);
    }

    @Transactional
    public Transaction transfer(Long sourceBullionId, Long targetBullionId, BigDecimal amount, User user,
                                String comment, LocalDateTime dateOperation, Long batchId,
                                boolean budgetOperation) {
        return record(sourceBullionId, targetBullionId, amount, user, comment, dateOperation, batchId, false, false, null, budgetOperation, null);
    }

    /**
     * Стартовый остаток: двигает сам слиток, но не двигает график накоплений —
     * иначе месяц создания слитка показал бы доход на всю сумму накоплений.
     * Для бюджета это всегда движение, а не трата: иначе первый день истории
     * слитка показал бы расход на всю сумму уже накопленного.
     */
    @Transactional
    public Transaction openingBalance(Long targetBullionId, BigDecimal amount, User user,
                                      String comment, LocalDateTime dateOperation) {
        return record(null, targetBullionId, amount, user, comment, dateOperation, null, true, false, null, false, null);
    }

    /**
     * Корзина бюджета из формы. {@code null} от клиента, который про поле не
     * знает, разрешается умолчанием по типу операции:
     * <ul>
     *   <li><b>наличные</b> (снял / внёс сдачу) — трата: именно из них состоит
     *       день, и забытая галочка выкинула бы его из отчёта молча;</li>
     *   <li><b>перевод</b> — движение бюджета: перевод внутрь почти всегда
     *       финансирование, а не возмещение. С обратным умолчанием забытая
     *       галочка на финансировании уводит день в глубокий минус.</li>
     * </ul>
     * Редкое возмещение переводом отмечается галочкой руками — таких единицы
     * в месяц, а финансирований столько же, но ошибка в них заметнее.
     */
    private static boolean budgetOperationOrDefault(Boolean budgetOperation, boolean fallback) {
        return budgetOperation == null ? fallback : budgetOperation;
    }

    /**
     * Единственная точка записи в таблицу транзакций: проверяет ноги, двигает остатки, сохраняет запись.
     * Неуспешные операции в таблицу не пишутся — ошибка уходит в лог приложения и в HTTP-ответ.
     */
    private Transaction record(Long sourceBullionId, Long targetBullionId, BigDecimal amount, User user,
                               String comment, LocalDateTime dateOperation, Long batchId,
                               boolean openingBalance, boolean imported, Long reversalOfId,
                               boolean budgetOperation, IncomeType incomeType) {

        if (sourceBullionId == null && targetBullionId == null) {
            throw new ApplicationException(BULLION_NOT_FOUND.getCode(), "Не указан ни один слиток операции");
        }
        if (Objects.equals(sourceBullionId, targetBullionId)) {
            throw new ApplicationException(CANNOT_TRANSFER_TO_SAME_VAULT.getCode(),
                    "Слиток-отправитель и слиток-получатель совпадают");
        }
        boolean isDeposit = sourceBullionId == null && targetBullionId != null && !openingBalance;
        if (incomeType != null && !isDeposit) {
            throw new ApplicationException(INCOME_TYPE_NOT_ALLOWED.getCode(),
                    "Тип дохода можно указать только у пополнения слитка");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApplicationException(CHANGE_AMOUNT_ZERO.getCode(), CHANGE_AMOUNT_ZERO.getMessage());
        }

        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);

        Bullion source = sourceBullionId == null ? null : loadBullion(sourceBullionId, user);
        Bullion target = targetBullionId == null ? null : loadBullion(targetBullionId, user);

        if (source != null) {
            if (source.getAmount().compareTo(scaled) < 0) {
                throw new ApplicationException(INSUFFICIENT_FUNDS.getCode(),
                        "Недостаточно средств в слитке " + describeBullion(source) + ". Доступно: " + source.getAmount());
            }
            source.setAmount(source.getAmount().subtract(scaled).setScale(2, RoundingMode.HALF_UP));
            bullionRepository.save(source);
        }

        if (target != null) {
            target.setAmount(target.getAmount().add(scaled).setScale(2, RoundingMode.HALF_UP));
            bullionRepository.save(target);
        }

        Transaction transaction = transactionRepository.save(Transaction.builder()
                .userId(user.getId())
                .dateOperation(dateOperation == null ? LocalDateTime.now() : dateOperation)
                .amount(scaled)
                .sourceBullionId(sourceBullionId)
                .targetBullionId(targetBullionId)
                .openingBalance(openingBalance)
                .imported(imported)
                .comment(comment)
                .reversalOfId(reversalOfId)
                .batchId(batchId)
                .budgetOperation(budgetOperation)
                .incomeType(incomeType)
                .build());

        log.info("Transaction recorded: id={}, kind={}, source={}, target={}, amount={}",
                transaction.getId(), transaction.getKind(), sourceBullionId, targetBullionId, scaled);
        return transaction;
    }

    private Bullion loadBullion(Long bullionId, User user) {
        return bullionRepository.findByIdAndUser(bullionId, user)
                .orElseThrow(() -> new ApplicationException(BULLION_NOT_FOUND.getCode(), BULLION_NOT_FOUND.getMessage()));
    }

    // ------------------------------------------------------------------
    // Операции, вызываемые из BullionService (форма запроса с фронта)
    // ------------------------------------------------------------------

    @Transactional
    public Bullion refillBullion(RefillBullionRequest request, User user, Long batchId) {
        Bullion bullion = resolveBullion(user, request.getBullionNameId(), request.getVaultId());
        requireIncomeAllowed(bullion);
        deposit(bullion.getId(), request.getAmount(), user, request.getUserComment(), request.getDateOperation(), batchId,
                budgetOperationOrDefault(request.getBudgetOperation(), true), request.getIncomeType());
        return bullion;
    }

    /**
     * Правка или сброс типа дохода у уже проведённого пополнения — этим же
     * путём размечаются задним числом старые операции, заведённые до появления
     * классификации.
     */
    @Transactional
    public TransactionDto updateIncomeType(Long transactionId, User user, UpdateIncomeTypeRequest request) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ApplicationException(TRANSACTION_NOT_FOUND.getCode(), TRANSACTION_NOT_FOUND.getMessage()));

        if (!transaction.getUserId().equals(user.getId())) {
            throw new ApplicationException(ACCESS_DENIED.getCode(), ACCESS_DENIED.getMessage());
        }
        if (transaction.getKind() != TransactionKind.DEPOSIT || transaction.getReversalOfId() != null) {
            throw new ApplicationException(INCOME_TYPE_UPDATE_NOT_ALLOWED.getCode(), INCOME_TYPE_UPDATE_NOT_ALLOWED.getMessage());
        }
        if (transactionRepository.existsByReversalOfId(transactionId)) {
            throw new ApplicationException(INCOME_TYPE_UPDATE_NOT_ALLOWED.getCode(), INCOME_TYPE_UPDATE_NOT_ALLOWED.getMessage());
        }

        transaction.setIncomeType(request.getIncomeType());
        transactionRepository.save(transaction);
        log.info("Income type updated: transactionId={}, incomeType={}", transactionId, request.getIncomeType());

        return toDtos(List.of(transaction)).get(0);
    }

    @Transactional
    public Bullion withdrawBullion(WithdrawBullionRequest request, User user, Long batchId) {
        Bullion bullion = resolveBullion(user, request.getBullionNameId(), request.getVaultId());
        requireExpenseAllowed(bullion);
        withdraw(bullion.getId(), request.getAmount(), user, request.getUserComment(), request.getDateOperation(), batchId,
                budgetOperationOrDefault(request.getBudgetOperation(), true));
        return bullion;
    }

    /**
     * Перевод между своими слитками — одна запись с двумя ногами.
     * Раньше здесь писались две несвязанные операции, из-за чего перевод давал
     * одновременно доход и расход на одну и ту же сумму.
     */
    @Transactional
    public Bullion transferAmount(TransferRequest request, User user) {
        return transferAmount(request, user, null);
    }

    @Transactional
    public Bullion transferAmount(TransferRequest request, User user, Long batchId) {
        requireTransferAllowed(loadBullion(request.getFromBullionId(), user),
                loadBullion(request.getToBullionId(), user));
        Transaction transaction = transfer(request.getFromBullionId(), request.getToBullionId(),
                request.getAmount(), user, request.getComment(), request.getDateOperation(), batchId,
                budgetOperationOrDefault(request.getBudgetOperation(), false));
        return loadBullion(transaction.getSourceBullionId(), user);
    }

    // ------------------------------------------------------------------
    // Галочки хранилища. Гашение кнопок на фронте — подсказка, запрет здесь:
    // запрос в обход интерфейса должен падать, а не двигать деньги.
    // Проверки стоят на том, что пользователь жмёт кнопкой; правку суммы слитка
    // проверяет BullionService.updateBullion по направлению дельты, поэтому
    // методы публичные. Стартовый остаток нового слитка, архивация и импорт
    // не проверяются: это регистрация уже накопленного, а не операция.
    // ------------------------------------------------------------------

    public void requireIncomeAllowed(Bullion bullion) {
        Vault vault = bullion.getVault();
        if (vault != null && !vault.isAllowedIncome()) {
            throw new ApplicationException(VAULT_INCOME_NOT_ALLOWED.getCode(),
                    "В хранилище " + describeVault(vault) + " вносить нельзя: снята галочка «Можно вносить»");
        }
    }

    public void requireExpenseAllowed(Bullion bullion) {
        Vault vault = bullion.getVault();
        if (vault != null && !vault.isAllowedExpense()) {
            throw new ApplicationException(VAULT_EXPENSE_NOT_ALLOWED.getCode(),
                    "Из хранилища " + describeVault(vault) + " снимать нельзя: снята галочка «Можно снимать»");
        }
    }

    /**
     * Перевод — это снятие с одной стороны и внесение с другой, поэтому мало
     * галочки «Можно переводить»: у отправителя нужна ещё «Можно снимать»,
     * у получателя — «Можно вносить».
     */
    private void requireTransferAllowed(Bullion from, Bullion to) {
        Vault fromVault = from.getVault();
        Vault toVault = to.getVault();
        if (fromVault != null && !fromVault.isAllowedTransfer()) {
            throw new ApplicationException(VAULT_TRANSFER_NOT_ALLOWED.getCode(),
                    "Из хранилища " + describeVault(fromVault) + " переводить нельзя: снята галочка «Можно переводить»");
        }
        if (toVault != null && !toVault.isAllowedTransfer()) {
            throw new ApplicationException(VAULT_TRANSFER_NOT_ALLOWED.getCode(),
                    "В хранилище " + describeVault(toVault) + " переводить нельзя: снята галочка «Можно переводить»");
        }
        requireExpenseAllowed(from);
        requireIncomeAllowed(to);
    }

    private Bullion resolveBullion(User user, Long bullionNameId, Long vaultId) {
        return bullionRepository.findByUserAndBullionNameIdAndVaultId(user, bullionNameId, vaultId)
                .orElseThrow(() -> new ApplicationException(BULLION_NOT_FOUND.getCode(), BULLION_NOT_FOUND.getMessage()));
    }

    /**
     * Перемещение слитка целиком в другое хранилище: сумма уходит переводом в слиток
     * с тем же наименованием в целевом хранилище, исходный слиток архивируется.
     * Физически удалить его нельзя — на него ссылается история.
     */
    @Transactional
    public void transferBullion(Long sourceBullionId, Long toVaultId, User user, Long batchId, LocalDateTime dateOperation) {
        Bullion source = loadBullion(sourceBullionId, user);

        Vault toVault = vaultRepository.findByIdAndUserAndArchivedFalse(toVaultId, user)
                .orElseThrow(() -> new ApplicationException(VAULT_NOT_FOUND.getCode(), "Целевое хранилище не найдено"));

        if (source.getVault() != null && source.getVault().getId().equals(toVault.getId())) {
            throw new ApplicationException(CANNOT_TRANSFER_TO_SAME_VAULT.getCode(), CANNOT_TRANSFER_TO_SAME_VAULT.getMessage());
        }

        Bullion target = bullionRepository.findByVaultAndBullionName(toVault, source.getBullionName())
                .orElseGet(() -> bullionRepository.save(Bullion.builder()
                        .bullionName(source.getBullionName())
                        .vault(toVault)
                        .amount(BigDecimal.ZERO)
                        .description(source.getDescription())
                        .user(user)
                        .build()));

        if (target.isArchived()) {
            target.setArchived(false);
            bullionRepository.save(target);
        }

        BigDecimal amount = source.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            // Перемещение слитка целиком — не трата: деньги остались у пользователя,
            // сменилось только хранилище.
            transfer(source.getId(), target.getId(), amount, user,
                    "Перемещение слитка в хранилище " + describeVault(toVault), dateOperation, batchId, false);
        }

        archive(source);
        log.info("Transfer bullion success: bullionId={}, toVaultId={}", sourceBullionId, toVaultId);
    }

    /**
     * Слиток с историей не удаляется физически — на него ссылаются транзакции.
     * Архивный слиток пропадает из списков и из суммы накоплений, но история читается целиком.
     */
    @Transactional
    public void archive(Bullion bullion) {
        bullion.setArchived(true);
        bullionRepository.save(bullion);
    }

    public Long createBatchId() {
        return System.currentTimeMillis();
    }

    // ------------------------------------------------------------------
    // Откат
    // ------------------------------------------------------------------

    /**
     * Откат — обратная транзакция, а не восстановление снимка остатка:
     * более поздние операции не затираются, арифметика сходится всегда.
     */
    @Transactional
    public Transaction rollbackTransaction(Long transactionId, User user) {
        // Погашение по карте — две ноги в одной операции. Откат одной ноги вернул бы
        // деньги на слиток, оставив долг погашенным, поэтому такие транзакции
        // откатываются только целиком, из истории карты.
        if (creditCardHistoryRepository.existsByBullionTransactionId(transactionId)) {
            throw new ApplicationException(TRANSACTION_LOCKED_BY_CARD.getCode(),
                    TRANSACTION_LOCKED_BY_CARD.getMessage());
        }
        return rollbackCardLinkedTransaction(transactionId, user);
    }

    /**
     * Тот же откат, но без замка на связку с картой: им пользуется
     * {@code CreditCardTransactionService}, когда отыгрывает погашение целиком.
     */
    @Transactional
    public Transaction rollbackCardLinkedTransaction(Long transactionId, User user) {
        Transaction original = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ApplicationException(TRANSACTION_NOT_FOUND.getCode(), TRANSACTION_NOT_FOUND.getMessage()));

        if (!original.getUserId().equals(user.getId())) {
            throw new ApplicationException(ACCESS_DENIED.getCode(), ACCESS_DENIED.getMessage());
        }

        if (transactionRepository.existsByReversalOfId(transactionId)) {
            throw new ApplicationException(TRANSACTION_ALREADY_REVERSED.getCode(), TRANSACTION_ALREADY_REVERSED.getMessage());
        }

        return record(
                original.getTargetBullionId(),
                original.getSourceBullionId(),
                original.getAmount(),
                user,
                "Откат операции #" + transactionId,
                LocalDateTime.now(),
                original.getBatchId(),
                original.isOpeningBalance(),
                original.isImported(),
                transactionId,
                // Корзину копируем обязательно: иначе трата уйдёт в одну сумму,
                // а её откат — в другую, и остаток бюджета разъедется с суммой
                // слитка при верной сумме слитка. Молчаливый разрыв тождества.
                original.isBudgetOperation(),
                // Тип дохода не копируем: результат отката — списание (withdraw),
                // тип дохода бывает только у пополнения, и запись отменяет операцию,
                // а не повторяет её смысл.
                null
        );
    }

    @Transactional
    public void rollbackLastTransaction(User user) {
        Transaction last = transactionRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new ApplicationException(TRANSACTION_NOT_FOUND.getCode(), "Нет транзакций для отката"));

        rollbackTransaction(last.getId(), user);
    }

    @Transactional
    public void rollbackBatch(Long batchId, User user) {
        List<Transaction> batch = transactionRepository.findByBatchIdOrderByCreatedAtAsc(batchId);

        if (batch.isEmpty()) {
            throw new ApplicationException(TRANSACTION_NOT_FOUND.getCode(), "Batch с id " + batchId + " не найден");
        }

        // С конца: откат более поздней операции возвращает средства, нужные для отката ранней.
        for (int i = batch.size() - 1; i >= 0; i--) {
            Transaction transaction = batch.get(i);
            if (transaction.getReversalOfId() == null && !transactionRepository.existsByReversalOfId(transaction.getId())) {
                rollbackTransaction(transaction.getId(), user);
            }
        }

        log.info("Rollback batch success: batchId={}", batchId);
    }

    // ------------------------------------------------------------------
    // Статистика
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DashboardStatisticsDto getDashboardStatistics(Long userId, Integer year) {
        LocalDateTime fromDate;
        LocalDateTime toDate;

        if (year != null) {
            fromDate = LocalDate.of(year, 1, 1).atStartOfDay();
            toDate = LocalDate.of(year, 12, 31).atTime(LocalTime.MAX);
        } else {
            fromDate = EPOCH;
            toDate = LocalDateTime.now();
        }

        List<MonthlyDataDto> monthlyData = year != null
                ? buildFullYearMonths(userId, year)
                : buildSparseMonths(userId, fromDate, toDate);

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        Long totalTransactions = 0L;

        List<Object[]> totalStats = transactionRepository.getTotalStatistics(userId, fromDate, toDate);
        if (!totalStats.isEmpty()) {
            Object[] row = totalStats.get(0);
            totalIncome = toBigDecimal(row[0]);
            totalExpense = toBigDecimal(row[1]);
            totalTransactions = toLong(row[2]);
        }

        List<TransactionDto> recentTransactions =
                toDtos(transactionRepository.findRecentTransactions(userId, 5));

        List<KindStatsDto> kindStats = transactionRepository.getKindStatistics(userId, fromDate, toDate).stream()
                .map(row -> KindStatsDto.builder()
                        .kind(TransactionKind.valueOf((String) row[0]))
                        .count(toLong(row[1]))
                        .totalAmount(toBigDecimal(row[2]))
                        .build())
                .collect(Collectors.toList());

        return DashboardStatisticsDto.builder()
                .monthlyData(monthlyData)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netChange(totalIncome.subtract(totalExpense))
                .totalAmount(bullionRepository.getTotalAmountByUserId(userId))
                .totalDebt(creditCardRepository.getTotalDebtByUserId(userId))
                .totalTransactions(totalTransactions)
                .recentTransactions(recentTransactions)
                .kindStats(kindStats)
                .build();
    }

    /**
     * Полные 12 месяцев года: месяц без операций сохраняет накопления предыдущего,
     * а не проваливается в ноль. Стартовая точка берётся обратным ходом от фактической
     * суммы слитков, поэтому правый край графика по построению равен «Всего накоплений»
     * на том же дашборде — дрейф невозможен.
     */
    private List<MonthlyDataDto> buildFullYearMonths(Long userId, int year) {
        LocalDateTime yearStart = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime yearEnd = LocalDate.of(year, 12, 31).atTime(LocalTime.MAX);

        Map<String, Object[]> byMonth = new HashMap<>();
        List<Object[]> rows = transactionRepository.getMonthlyStatistics(userId, yearStart, yearEnd);
        for (Object[] row : rows) {
            byMonth.put(toLocalDateTime(row[0]).format(DateTimeFormatter.ofPattern("yyyy-MM")), row);
        }

        Map<String, BigDecimal> debtByMonth = debtDeltasByMonth(userId, yearStart, yearEnd);

        BigDecimal savings = balanceAt(userId, yearStart);
        BigDecimal debt = debtAt(userId, yearStart);

        List<MonthlyDataDto> result = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            LocalDate monthDate = LocalDate.of(year, m, 1);
            String month = monthDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            Object[] row = byMonth.get(month);

            BigDecimal netChange = row == null ? BigDecimal.ZERO : toBigDecimal(row[3]);
            savings = savings.add(netChange);
            debt = debt.add(debtByMonth.getOrDefault(month, BigDecimal.ZERO));

            result.add(MonthlyDataDto.builder()
                    .month(month)
                    .monthLabel(monthDate.format(DateTimeFormatter.ofPattern("MMM yyyy")))
                    .income(row == null ? BigDecimal.ZERO : toBigDecimal(row[1]))
                    .expense(row == null ? BigDecimal.ZERO : toBigDecimal(row[2]))
                    .netChange(netChange)
                    .savings(savings)
                    .debt(debt)
                    .transactionCount(row == null ? 0L : toLong(row[4]))
                    .build());
        }
        return result;
    }

    private List<MonthlyDataDto> buildSparseMonths(Long userId, LocalDateTime fromDate, LocalDateTime toDate) {
        List<Object[]> rows = transactionRepository.getMonthlyStatistics(userId, fromDate, toDate);
        Map<String, BigDecimal> debtByMonth = debtDeltasByMonth(userId, fromDate, toDate);

        BigDecimal savings = balanceAt(userId, fromDate);
        BigDecimal debt = debtAt(userId, fromDate);

        List<MonthlyDataDto> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            LocalDateTime monthDate = toLocalDateTime(row[0]);
            String month = monthDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            BigDecimal netChange = toBigDecimal(row[3]);
            savings = savings.add(netChange);
            debt = debt.add(debtByMonth.getOrDefault(month, BigDecimal.ZERO));

            result.add(MonthlyDataDto.builder()
                    .month(month)
                    .monthLabel(monthDate.format(DateTimeFormatter.ofPattern("MMM yyyy")))
                    .income(toBigDecimal(row[1]))
                    .expense(toBigDecimal(row[2]))
                    .netChange(netChange)
                    .savings(savings)
                    .debt(debt)
                    .transactionCount(toLong(row[4]))
                    .build());
        }
        return result;
    }

    /**
     * Накопления на указанный момент = фактическая сумма слитков минус изменения,
     * произошедшие после него. Источник истины — сами слитки, а не лог операций.
     */
    private BigDecimal balanceAt(Long userId, LocalDateTime moment) {
        BigDecimal current = bullionRepository.getTotalAmountByUserId(userId);
        BigDecimal deltaAfter = transactionRepository.getDeltaAfter(userId, moment);
        return current.subtract(deltaAfter == null ? BigDecimal.ZERO : deltaAfter);
    }

    /**
     * Задолженность по картам на момент — тем же обратным ходом, что и накопления:
     * текущий долг карт минус всё, что случилось после. Правый край графика по
     * построению совпадает с «Текущим долгом» на странице кредитных карт.
     */
    private BigDecimal debtAt(Long userId, LocalDateTime moment) {
        BigDecimal current = creditCardRepository.getTotalDebtByUserId(userId);
        BigDecimal deltaAfter = creditCardHistoryRepository.getDebtDeltaAfter(userId, moment);
        return current.subtract(deltaAfter == null ? BigDecimal.ZERO : deltaAfter);
    }

    private Map<String, BigDecimal> debtDeltasByMonth(Long userId, LocalDateTime fromDate, LocalDateTime toDate) {
        Map<String, BigDecimal> deltas = new HashMap<>();
        for (Object[] row : creditCardHistoryRepository.getMonthlyDebtStatistics(userId, fromDate, toDate)) {
            deltas.put(toLocalDateTime(row[0]).format(DateTimeFormatter.ofPattern("yyyy-MM")), toBigDecimal(row[1]));
        }
        return deltas;
    }

    @Transactional(readOnly = true)
    public DashboardDailyStatisticsDto getDailyStatistics(Long userId, int year, int month) {
        Map<Integer, Object[]> byDay = transactionRepository.getDailyStatistics(userId, year, month).stream()
                .collect(Collectors.toMap(row -> toLong(row[0]).intValue(), row -> row));

        Map<Integer, BigDecimal> debtByDay = creditCardHistoryRepository
                .getDailyDebtStatistics(userId, year, month).stream()
                .collect(Collectors.toMap(row -> toLong(row[0]).intValue(), row -> toBigDecimal(row[1])));

        int daysInMonth = LocalDate.of(year, month, 1).lengthOfMonth();
        BigDecimal savings = balanceAt(userId, LocalDate.of(year, month, 1).atStartOfDay());
        BigDecimal debt = debtAt(userId, LocalDate.of(year, month, 1).atStartOfDay());

        List<DashboardDailyStatisticsDto.DailyDataDto> dailyData = new ArrayList<>(daysInMonth);
        for (int day = 1; day <= daysInMonth; day++) {
            Object[] row = byDay.get(day);

            BigDecimal income = row == null ? BigDecimal.ZERO : toBigDecimal(row[1]);
            BigDecimal expense = row == null ? BigDecimal.ZERO : toBigDecimal(row[2]);
            BigDecimal dailyChange = row == null ? BigDecimal.ZERO : toBigDecimal(row[3]);
            savings = savings.add(dailyChange);
            debt = debt.add(debtByDay.getOrDefault(day, BigDecimal.ZERO));

            dailyData.add(DashboardDailyStatisticsDto.DailyDataDto.builder()
                    .day(day)
                    .date(String.format("%02d.%02d", day, month))
                    .savings(savings)
                    .debt(debt)
                    .dailyChange(dailyChange)
                    .income(income)
                    .expense(expense)
                    .transactionCount(row == null ? 0L : toLong(row[4]))
                    .build());
        }

        return DashboardDailyStatisticsDto.builder()
                .year(year)
                .month(month)
                .monthLabel(LocalDate.of(year, month, 1).format(DateTimeFormatter.ofPattern("MMMM yyyy")))
                .totalAmount(savings)
                .totalDebt(debt)
                .dailyData(dailyData)
                .build();
    }

    @Transactional(readOnly = true)
    public List<Integer> getAvailableYears(Long userId) {
        List<Integer> years = transactionRepository.getAvailableYears(userId);
        return years.isEmpty() ? List.of(LocalDate.now().getYear()) : years;
    }

    // ------------------------------------------------------------------
    // История
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TransactionHistoryResponse getTransactionHistory(
            Long userId, String kind, LocalDate fromDate, LocalDate toDate, Pageable pageable) {

        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();
        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? null : toDate.atTime(LocalTime.MAX);
        String kindFilter = StringUtils.isBlank(kind) ? null : TransactionKind.valueOf(kind).name();

        List<Transaction> transactions = transactionRepository.findTransactionHistory(
                userId, offset, limit, from, to, kindFilter);
        long total = transactionRepository.countTransactionHistory(userId, from, to, kindFilter);

        return TransactionHistoryResponse.builder()
                .content(toDtos(transactions))
                .totalElements(total)
                .totalPages((int) Math.ceil((double) total / limit))
                .currentPage(pageable.getPageNumber())
                .pageSize(limit)
                .build();
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactionChain(Long transactionId) {
        return toDtos(transactionRepository.findByReversalOfIdOrIdOrderByCreatedAtAsc(transactionId, transactionId));
    }

    /**
     * Страница истории слитка с поиском по комментарию и «Остатком после» у
     * каждой операции. Проверяет владение слитком.
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionDto> getBullionHistory(User user, Long bullionId, String search, int page, int size) {
        Bullion bullion = loadBullion(bullionId, user);
        int pageNumber = Math.max(page - 1, 0);
        int pageSize = size > 0 ? Math.min(size, 100) : 10;
        String searchFilter = StringUtils.isBlank(search) ? null : search.trim();

        List<TransactionDto> content = toDtos(transactionRepository.findBullionHistory(
                user.getId(), bullionId, searchFilter, pageNumber * pageSize, pageSize));
        long total = transactionRepository.countBullionHistory(user.getId(), bullionId, searchFilter);

        if (!content.isEmpty()) {
            Set<Long> ids = content.stream().map(TransactionDto::getId).collect(Collectors.toSet());
            Map<Long, BigDecimal> newerDeltas = new HashMap<>();
            for (Object[] row : transactionRepository.findBullionNewerDeltas(user.getId(), bullionId, ids)) {
                newerDeltas.put(toLong(row[0]), toBigDecimal(row[1]));
            }
            content.forEach(dto -> dto.setBalanceAfter(
                    bullion.getAmount().subtract(newerDeltas.getOrDefault(dto.getId(), BigDecimal.ZERO))));
        }

        int totalPages = (int) Math.ceil((double) total / pageSize);
        return PageResponse.<TransactionDto>builder()
                .content(content)
                .pageNumber(pageNumber + 1)
                .pageSize(pageSize)
                .totalElements(total)
                .totalPages(totalPages)
                .first(pageNumber == 0)
                .last(pageNumber + 1 >= totalPages)
                .build();
    }

    // ------------------------------------------------------------------
    // Преобразование в DTO
    // ------------------------------------------------------------------

    /** Публичный: тем же преобразованием пользуется отчёт о бюджете. */
    public List<TransactionDto> toDtos(List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return List.of();
        }

        Set<Long> ids = transactions.stream().map(Transaction::getId).collect(Collectors.toSet());
        Map<Long, Long> reversedBy = transactionRepository.findReversalsOf(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        // Ноги погашений по карте: у них своя кнопка отката — в истории карты
        Set<Long> lockedByCard = creditCardHistoryRepository.findBullionTransactionIds(ids);

        return transactions.stream()
                .map(transaction -> toDto(transaction, reversedBy.get(transaction.getId()),
                        lockedByCard.contains(transaction.getId())))
                .collect(Collectors.toList());
    }

    private TransactionDto toDto(Transaction transaction, Long reversedById, boolean lockedByCard) {
        return TransactionDto.builder()
                .id(transaction.getId())
                .kind(transaction.getKind())
                .sourceBullionId(transaction.getSourceBullionId())
                .targetBullionId(transaction.getTargetBullionId())
                .amount(transaction.getAmount())
                .signedAmount(transaction.getSignedAmount())
                .description(buildDescription(transaction))
                .descriptionSegments(buildDescriptionSegments(transaction))
                .comment(transaction.getComment())
                .incomeType(transaction.getIncomeType())
                .canChangeIncomeType(transaction.getKind() == TransactionKind.DEPOSIT
                        && transaction.getReversalOfId() == null
                        && reversedById == null)
                .createdAt(transaction.getCreatedAt())
                .dateOperation(transaction.getDateOperation())
                .imported(transaction.isImported())
                .budgetOperation(transaction.isBudgetOperation())
                .canRollback(reversedById == null && !lockedByCard)
                .lockedByCard(lockedByCard)
                .reversalOfId(transaction.getReversalOfId())
                .reversedById(reversedById)
                .build();
    }

    private String buildDescription(Transaction transaction) {
        BigDecimal amount = transaction.getAmount();
        return switch (transaction.getKind()) {
            case DEPOSIT -> "Пополнение на сумму " + amount + " слитка " + describeBullionById(transaction.getTargetBullionId());
            case WITHDRAWAL -> "Снятие суммы " + amount + " со слитка " + describeBullionById(transaction.getSourceBullionId());
            case TRANSFER -> "Перевод суммы " + amount + " из слитка " + describeBullionById(transaction.getSourceBullionId())
                    + " в " + describeBullionById(transaction.getTargetBullionId());
            case OPENING_BALANCE -> "Начальный остаток " + amount + " слитка "
                    + describeBullionById(transaction.getTargetBullionId() != null
                        ? transaction.getTargetBullionId() : transaction.getSourceBullionId());
        };
    }

    /**
     * Цветные сегменты описания: наименование слитка — своим цветом,
     * банк — оранжевым, хранилище — жёлтым, служебный текст без цвета.
     */
    private List<DescriptionSegmentDto> buildDescriptionSegments(Transaction transaction) {
        BigDecimal amount = transaction.getAmount();
        List<DescriptionSegmentDto> segments = new ArrayList<>();

        switch (transaction.getKind()) {
            case DEPOSIT -> {
                segments.add(segment("Пополнение на сумму " + amount + " слитка ", null));
                segments.addAll(describeBullionSegmentsById(transaction.getTargetBullionId()));
            }
            case WITHDRAWAL -> {
                segments.add(segment("Снятие суммы " + amount + " со слитка ", null));
                segments.addAll(describeBullionSegmentsById(transaction.getSourceBullionId()));
            }
            case TRANSFER -> {
                segments.add(segment("Перевод суммы " + amount + " из слитка ", null));
                segments.addAll(describeBullionSegmentsById(transaction.getSourceBullionId()));
                segments.add(segment(" в ", null));
                segments.addAll(describeBullionSegmentsById(transaction.getTargetBullionId()));
            }
            case OPENING_BALANCE -> {
                segments.add(segment("Начальный остаток " + amount + " слитка ", null));
                segments.addAll(describeBullionSegmentsById(transaction.getTargetBullionId() != null
                        ? transaction.getTargetBullionId() : transaction.getSourceBullionId()));
            }
        }

        return segments;
    }

    /**
     * Человекочитаемое описание слитка: «Наименование (Цвет) | Банк | Хранилище».
     * Банк опускается, если у хранилища его нет.
     */
    private String describeBullion(Bullion bullion) {
        if (bullion == null) {
            return "—";
        }

        StringBuilder sb = new StringBuilder();

        BullionName bullionName = bullion.getBullionName();
        if (bullionName != null) {
            sb.append(bullionName.getTitle());
            String colorName = colorConstants.getColorName(bullionName.getColor());
            if (colorName != null) {
                sb.append(" (").append(colorName).append(")");
            }
        } else {
            sb.append("слиток #").append(bullion.getId());
        }

        Vault vault = bullion.getVault();
        if (vault != null) {
            if (vault.getBank() != null && vault.getBank().getName() != null) {
                sb.append(" | ").append(vault.getBank().getName());
            }
            sb.append(" | ").append(vault.getName());
            if (vault.isArchived()) {
                sb.append(" (удалено)");
            }
        }

        return sb.toString();
    }

    private List<DescriptionSegmentDto> describeBullionSegments(Bullion bullion) {
        List<DescriptionSegmentDto> segments = new ArrayList<>();
        if (bullion == null) {
            segments.add(segment("—", null));
            return segments;
        }

        BullionName bullionName = bullion.getBullionName();
        if (bullionName != null) {
            segments.add(segment(bullionName.getTitle(), bullionName.getColor()));
        } else {
            segments.add(segment("слиток #" + bullion.getId(), null));
        }

        Vault vault = bullion.getVault();
        if (vault != null) {
            segments.add(segment(" | ", null));
            if (vault.getBank() != null && vault.getBank().getName() != null) {
                segments.add(segment(vault.getBank().getName(), BANK_COLOR));
                segments.add(segment(" | ", null));
            }
            segments.add(vaultSegment(vault));
        }

        return segments;
    }

    /** Хранилища может уже не быть — история про это говорит, а не молчит. */
    private DescriptionSegmentDto vaultSegment(Vault vault) {
        return DescriptionSegmentDto.builder()
                .text(vault.isArchived() ? vault.getName() + " (удалено)" : vault.getName())
                .color(VAULT_COLOR)
                .archived(vault.isArchived())
                .build();
    }

    private String describeBullionById(Long bullionId) {
        if (bullionId == null) {
            return "—";
        }
        return bullionRepository.findById(bullionId)
                .map(this::describeBullion)
                .orElse("слиток #" + bullionId);
    }

    private List<DescriptionSegmentDto> describeBullionSegmentsById(Long bullionId) {
        if (bullionId == null) {
            return List.of(segment("—", null));
        }
        return bullionRepository.findById(bullionId)
                .map(this::describeBullionSegments)
                .orElse(List.of(segment("слиток #" + bullionId, null)));
    }

    private String describeVault(Vault vault) {
        if (vault.getBank() != null && vault.getBank().getName() != null) {
            return vault.getBank().getName() + " | " + vault.getName();
        }
        return vault.getName();
    }

    private DescriptionSegmentDto segment(String text, String color) {
        return DescriptionSegmentDto.builder().text(text).color(color).build();
    }

    // ------------------------------------------------------------------

    private static BigDecimal toBigDecimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private static Long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.time.Instant instant) {
            return LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        }
        throw new ApplicationException(INTERNAL_ERROR.getCode(),
                "Неожиданный тип колонки date_operation: " + value.getClass().getName());
    }
}
