package ru.money.goldentaurusbank.www.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.*;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.TransactionKind;
import ru.money.goldentaurusbank.www.backend.model.dto.request.RefillBullionRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.TransferRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.WithdrawBullionRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.*;
import ru.money.goldentaurusbank.www.backend.repository.BullionRepository;
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
        return record(null, targetBullionId, amount, user, comment, dateOperation, batchId, false, false, null);
    }

    @Transactional
    public Transaction withdraw(Long sourceBullionId, BigDecimal amount, User user,
                                String comment, LocalDateTime dateOperation, Long batchId) {
        return record(sourceBullionId, null, amount, user, comment, dateOperation, batchId, false, false, null);
    }

    @Transactional
    public Transaction transfer(Long sourceBullionId, Long targetBullionId, BigDecimal amount, User user,
                                String comment, LocalDateTime dateOperation, Long batchId) {
        return record(sourceBullionId, targetBullionId, amount, user, comment, dateOperation, batchId, false, false, null);
    }

    /**
     * Стартовый остаток: двигает сам слиток, но не двигает график накоплений —
     * иначе месяц создания слитка показал бы доход на всю сумму накоплений.
     */
    @Transactional
    public Transaction openingBalance(Long targetBullionId, BigDecimal amount, User user,
                                      String comment, LocalDateTime dateOperation) {
        return record(null, targetBullionId, amount, user, comment, dateOperation, null, true, false, null);
    }

    /**
     * Единственная точка записи в таблицу транзакций: проверяет ноги, двигает остатки, сохраняет запись.
     * Неуспешные операции в таблицу не пишутся — ошибка уходит в лог приложения и в HTTP-ответ.
     */
    private Transaction record(Long sourceBullionId, Long targetBullionId, BigDecimal amount, User user,
                               String comment, LocalDateTime dateOperation, Long batchId,
                               boolean openingBalance, boolean imported, Long reversalOfId) {

        if (sourceBullionId == null && targetBullionId == null) {
            throw new ApplicationException(BULLION_NOT_FOUND.getCode(), "Не указан ни один слиток операции");
        }
        if (Objects.equals(sourceBullionId, targetBullionId)) {
            throw new ApplicationException(CANNOT_TRANSFER_TO_SAME_VAULT.getCode(),
                    "Слиток-отправитель и слиток-получатель совпадают");
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
        deposit(bullion.getId(), request.getAmount(), user, request.getUserComment(), request.getDateOperation(), batchId);
        return bullion;
    }

    @Transactional
    public Bullion withdrawBullion(WithdrawBullionRequest request, User user, Long batchId) {
        Bullion bullion = resolveBullion(user, request.getBullionNameId(), request.getVaultId());
        requireExpenseAllowed(bullion);
        withdraw(bullion.getId(), request.getAmount(), user, request.getUserComment(), request.getDateOperation(), batchId);
        return bullion;
    }

    /**
     * Перевод между своими слитками — одна запись с двумя ногами.
     * Раньше здесь писались две несвязанные операции, из-за чего перевод давал
     * одновременно доход и расход на одну и ту же сумму.
     */
    @Transactional
    public Bullion transferAmount(TransferRequest request, User user) {
        requireTransferAllowed(loadBullion(request.getFromBullionId(), user),
                loadBullion(request.getToBullionId(), user));
        Transaction transaction = transfer(request.getFromBullionId(), request.getToBullionId(),
                request.getAmount(), user, request.getComment(), request.getDateOperation(), null);
        return loadBullion(transaction.getSourceBullionId(), user);
    }

    // ------------------------------------------------------------------
    // Галочки хранилища. Гашение кнопок на фронте — подсказка, запрет здесь:
    // запрос в обход интерфейса должен падать, а не двигать деньги.
    // Правки суммы слитка, стартового остатка и перемещения слитка целиком
    // это не касается — там свои флаги.
    // ------------------------------------------------------------------

    private void requireIncomeAllowed(Bullion bullion) {
        Vault vault = bullion.getVault();
        if (vault != null && !vault.isAllowedIncome()) {
            throw new ApplicationException(VAULT_INCOME_NOT_ALLOWED.getCode(),
                    "В хранилище " + describeVault(vault) + " вносить нельзя: снята галочка «Можно вносить»");
        }
    }

    private void requireExpenseAllowed(Bullion bullion) {
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

        Vault toVault = vaultRepository.findByIdAndUser(toVaultId, user)
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
            transfer(source.getId(), target.getId(), amount, user,
                    "Перемещение слитка в хранилище " + describeVault(toVault), dateOperation, batchId);
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
                transactionId
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
        Map<String, Object[]> byMonth = new HashMap<>();
        List<Object[]> rows = transactionRepository.getMonthlyStatistics(
                userId, LocalDate.of(year, 1, 1).atStartOfDay(), LocalDate.of(year, 12, 31).atTime(LocalTime.MAX));
        for (Object[] row : rows) {
            byMonth.put(toLocalDateTime(row[0]).format(DateTimeFormatter.ofPattern("yyyy-MM")), row);
        }

        BigDecimal savings = balanceAt(userId, LocalDate.of(year, 1, 1).atStartOfDay());

        List<MonthlyDataDto> result = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            LocalDate monthDate = LocalDate.of(year, m, 1);
            String month = monthDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            Object[] row = byMonth.get(month);

            BigDecimal netChange = row == null ? BigDecimal.ZERO : toBigDecimal(row[3]);
            savings = savings.add(netChange);

            result.add(MonthlyDataDto.builder()
                    .month(month)
                    .monthLabel(monthDate.format(DateTimeFormatter.ofPattern("MMM yyyy")))
                    .income(row == null ? BigDecimal.ZERO : toBigDecimal(row[1]))
                    .expense(row == null ? BigDecimal.ZERO : toBigDecimal(row[2]))
                    .netChange(netChange)
                    .savings(savings)
                    .transactionCount(row == null ? 0L : toLong(row[4]))
                    .build());
        }
        return result;
    }

    private List<MonthlyDataDto> buildSparseMonths(Long userId, LocalDateTime fromDate, LocalDateTime toDate) {
        List<Object[]> rows = transactionRepository.getMonthlyStatistics(userId, fromDate, toDate);
        BigDecimal savings = balanceAt(userId, fromDate);

        List<MonthlyDataDto> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            LocalDateTime monthDate = toLocalDateTime(row[0]);
            BigDecimal netChange = toBigDecimal(row[3]);
            savings = savings.add(netChange);

            result.add(MonthlyDataDto.builder()
                    .month(monthDate.format(DateTimeFormatter.ofPattern("yyyy-MM")))
                    .monthLabel(monthDate.format(DateTimeFormatter.ofPattern("MMM yyyy")))
                    .income(toBigDecimal(row[1]))
                    .expense(toBigDecimal(row[2]))
                    .netChange(netChange)
                    .savings(savings)
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

    @Transactional(readOnly = true)
    public DashboardDailyStatisticsDto getDailyStatistics(Long userId, int year, int month) {
        Map<Integer, Object[]> byDay = transactionRepository.getDailyStatistics(userId, year, month).stream()
                .collect(Collectors.toMap(row -> toLong(row[0]).intValue(), row -> row));

        int daysInMonth = LocalDate.of(year, month, 1).lengthOfMonth();
        BigDecimal savings = balanceAt(userId, LocalDate.of(year, month, 1).atStartOfDay());

        List<DashboardDailyStatisticsDto.DailyDataDto> dailyData = new ArrayList<>(daysInMonth);
        for (int day = 1; day <= daysInMonth; day++) {
            Object[] row = byDay.get(day);

            BigDecimal income = row == null ? BigDecimal.ZERO : toBigDecimal(row[1]);
            BigDecimal expense = row == null ? BigDecimal.ZERO : toBigDecimal(row[2]);
            BigDecimal dailyChange = row == null ? BigDecimal.ZERO : toBigDecimal(row[3]);
            savings = savings.add(dailyChange);

            dailyData.add(DashboardDailyStatisticsDto.DailyDataDto.builder()
                    .day(day)
                    .date(String.format("%02d.%02d", day, month))
                    .savings(savings)
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

    @Transactional(readOnly = true)
    public List<TransactionDto> getBullionHistory(Long bullionId) {
        return toDtos(transactionRepository.findBullionHistory(bullionId));
    }

    // ------------------------------------------------------------------
    // Преобразование в DTO
    // ------------------------------------------------------------------

    private List<TransactionDto> toDtos(List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return List.of();
        }

        Set<Long> ids = transactions.stream().map(Transaction::getId).collect(Collectors.toSet());
        Map<Long, Long> reversedBy = transactionRepository.findReversalsOf(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        return transactions.stream()
                .map(transaction -> toDto(transaction, reversedBy.get(transaction.getId())))
                .collect(Collectors.toList());
    }

    private TransactionDto toDto(Transaction transaction, Long reversedById) {
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
                .createdAt(transaction.getCreatedAt())
                .dateOperation(transaction.getDateOperation())
                .imported(transaction.isImported())
                .canRollback(reversedById == null)
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
            segments.add(segment(vault.getName(), VAULT_COLOR));
        }

        return segments;
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
