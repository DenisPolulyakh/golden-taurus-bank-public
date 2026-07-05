package ru.money.goldentaurusbank.www.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.*;
import ru.money.goldentaurusbank.www.backend.model.dto.request.RefillBullionRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.WithdrawBullionRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.*;
import ru.money.goldentaurusbank.www.backend.repository.BullionRepository;
import ru.money.goldentaurusbank.www.backend.repository.CategoryRepository;
import ru.money.goldentaurusbank.www.backend.repository.TransactionLogRepository;
import ru.money.goldentaurusbank.www.backend.repository.VaultRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.OperationType.*;
import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.*;
import static ru.money.goldentaurusbank.www.backend.model.dto.enums.TransactionStatus.FAILED;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final BullionRepository bullionRepository;
    private final VaultRepository vaultRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionLogRepository transactionLogRepository;

    @Transactional
    public Bullion refillBullion(RefillBullionRequest request, User user, Long batchId) {
        BigDecimal refillAmount = request.getAmount();
        if (BigDecimal.ZERO.compareTo(refillAmount) == 0) {
            throw new ApplicationException(CHANGE_AMOUNT_ZERO.getCode(), CHANGE_AMOUNT_ZERO.getMessage());
        }
        Bullion existingBullion = bullionRepository.findByUserAndCategoryIdAndVaultId(
                user, request.getCategoryId(), request.getVaultId()).orElseThrow(() -> new ApplicationException(BULLION_NOT_FOUND.getCode(), BULLION_NOT_FOUND.getMessage()));

        TransactionLog.TransactionLogBuilder logBuilder = TransactionLog.builder()
                .operationType(REFILL_BULLION.name())
                .fromBullionId(null)
                .toBullionId(existingBullion.getId())
                .amount(refillAmount)
                .userId(user.getId())
                .batchId(batchId)
                .status("SUCCESS");

        try {
            if (refillAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApplicationException(INSUFFICIENT_FUNDS.getCode(), "Сумма пополнения должна быть больше 0");
            }

            BigDecimal toBefore = existingBullion.getAmount();
            existingBullion.setAmount(toBefore.add(refillAmount).setScale(2, RoundingMode.HALF_UP));
            bullionRepository.save(existingBullion);

            logBuilder.fromBullionAmountBefore(null)
                    .fromBullionAmountAfter(null)
                    .toBullionAmountBefore(toBefore)
                    .toBullionAmountAfter(existingBullion.getAmount())
                    .userComment(request.getUserComment())
                    .description("Пополнение на сумму " + refillAmount + " слитка " + existingBullion.getCategory().getName());

            transactionLogRepository.save(logBuilder.build());
            log.info("Refill amount success: bullion={}, amount={}", existingBullion.getId(), refillAmount);
            return existingBullion;

        } catch (ApplicationException e) {
            logBuilder.status(FAILED.name()).errorMessage(e.getMessage());
            transactionLogRepository.save(logBuilder.build());
            throw e;
        }
    }

    @Transactional
    public Bullion withdrawBullion(WithdrawBullionRequest request, User user, Long batchId) {
        BigDecimal withDrawAmount = request.getAmount();
        if (BigDecimal.ZERO.compareTo(withDrawAmount) == 0) {
            throw new ApplicationException(CHANGE_AMOUNT_ZERO.getCode(), CHANGE_AMOUNT_ZERO.getMessage());
        }
        Bullion existingBullion = bullionRepository.findByUserAndCategoryIdAndVaultId(
                user, request.getCategoryId(), request.getVaultId()).orElseThrow(() -> new ApplicationException(BULLION_NOT_FOUND.getCode(), BULLION_NOT_FOUND.getMessage()));

        TransactionLog.TransactionLogBuilder logBuilder = TransactionLog.builder()
                .operationType(WITHDRAW_BULLION.name())
                .fromBullionId(null)
                .toBullionId(existingBullion.getId())
                .amount(withDrawAmount)
                .userId(user.getId())
                .batchId(batchId)
                .status("SUCCESS");

        try {
            if (withDrawAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApplicationException(INSUFFICIENT_FUNDS.getCode(), "Сумма снятия должна отличаться от 0");
            }
            Bullion bullion = bullionRepository.findByIdAndUser(existingBullion.getId(), user).orElseThrow(() -> new ApplicationException(BULLION_NOT_FOUND.getCode(), BULLION_NOT_FOUND.getMessage()));

            BigDecimal toBefore = bullion.getAmount();
            bullion.setAmount(bullion.getAmount().subtract(withDrawAmount).setScale(2, RoundingMode.HALF_UP));
            bullionRepository.save(bullion);

            logBuilder.fromBullionAmountBefore(null)
                    .fromBullionAmountAfter(null)
                    .toBullionAmountBefore(toBefore)
                    .toBullionAmountAfter(bullion.getAmount())
                    .userComment(request.getUserComment())
                    .description("Снятие суммы " + withDrawAmount + " слитка " + existingBullion.getCategory().getName());

            transactionLogRepository.save(logBuilder.build());
            log.info("Withdrawal amount success: bullion={}, amount={}", existingBullion.getId(), bullion.getAmount());
            return existingBullion;

        } catch (ApplicationException e) {
            logBuilder.status(FAILED.name()).errorMessage(e.getMessage());
            transactionLogRepository.save(logBuilder.build());
            throw e;
        }
    }

    @Transactional
    public void transferAmount(Long fromBullionId, Long toBullionId, BigDecimal amount, User user, String comment) {
        transferAmount(fromBullionId, toBullionId, amount, user, null, comment);
    }

    @Transactional
    public void transferAmount(Long fromBullionId, Long toBullionId, BigDecimal amount, User user, Long batchId, String comment) {
        TransactionLog.TransactionLogBuilder logBuilder = TransactionLog.builder()
                .operationType(TRANSFER_AMOUNT.name())
                .fromBullionId(fromBullionId)
                .toBullionId(toBullionId)
                .amount(amount)
                .userId(user.getId())
                .batchId(batchId)
                .status("SUCCESS");

        try {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApplicationException(INSUFFICIENT_FUNDS.getCode(), "Сумма перемещения должна быть больше 0");
            }

            Bullion fromBullion = bullionRepository.findById(fromBullionId)
                    .orElseThrow(() -> new ApplicationException(BULLION_NOT_FOUND.getCode(), "Слиток-отправитель не найден"));

            Bullion toBullion = bullionRepository.findById(toBullionId)
                    .orElseThrow(() -> new ApplicationException(BULLION_NOT_FOUND.getCode(), "Слиток-получатель не найден"));

            if (!fromBullion.getUser().getId().equals(toBullion.getUser().getId())) {
                throw new ApplicationException(BULLION_ACCESS_DENIED.getCode(), BULLION_ACCESS_DENIED.getMessage());
            }

            if (fromBullion.getAmount().compareTo(amount) < 0) {
                throw new ApplicationException(INSUFFICIENT_FUNDS.getCode(),
                        "Недостаточно средств. Доступно: " + fromBullion.getAmount());
            }

            BigDecimal fromBefore = fromBullion.getAmount();
            BigDecimal toBefore = toBullion.getAmount();

            fromBullion.setAmount(fromBullion.getAmount().subtract(amount).setScale(2, RoundingMode.HALF_UP));
            toBullion.setAmount(toBullion.getAmount().add(amount).setScale(2, RoundingMode.HALF_UP));

            bullionRepository.save(fromBullion);
            bullionRepository.save(toBullion);

            logBuilder.fromBullionAmountBefore(fromBefore)
                    .fromBullionAmountAfter(fromBullion.getAmount())
                    .toBullionAmountBefore(toBefore)
                    .toBullionAmountAfter(toBullion.getAmount())
                    .description("Перевод суммы " + amount + " из слитка " + fromBullionId + " в " + toBullionId);

            transactionLogRepository.save(logBuilder.build());
            log.info("Transfer amount success: from={}, to={}, amount={}", fromBullionId, toBullionId, amount);

        } catch (ApplicationException e) {
            logBuilder.status("FAILED").errorMessage(e.getMessage());
            transactionLogRepository.save(logBuilder.build());
            throw e;
        }
    }

    @Transactional
    public void transferBullion(Long fromBullionId, Long toVaultId, User user) {
        transferBullion(fromBullionId, toVaultId, user, null);
    }

    @Transactional
    public void transferBullion(Long fromBullionId, Long toVaultId, User user, Long batchId) {
        TransactionLog.TransactionLogBuilder logBuilder = TransactionLog.builder()
                .operationType(TRANSFER_BULLION.name())
                .fromBullionId(fromBullionId)
                .toVaultId(toVaultId)
                .userId(user.getId())
                .batchId(batchId)
                .status("SUCCESS");

        try {
            Bullion fromBullion = bullionRepository.findById(fromBullionId)
                    .orElseThrow(() -> new ApplicationException(BULLION_NOT_FOUND.getCode(), "Слиток для перемещения не найден"));

            Vault toVault = vaultRepository.findByIdAndUser(toVaultId, user)
                    .orElseThrow(() -> new ApplicationException(VAULT_NOT_FOUND.getCode(), "Целевое хранилище не найдено"));

            Vault fromVault = fromBullion.getVault();

            if (fromVault.getId().equals(toVault.getId())) {
                throw new ApplicationException(CANNOT_TRANSFER_TO_SAME_VAULT.getCode(), CANNOT_TRANSFER_TO_SAME_VAULT.getMessage());
            }

            logBuilder.fromVaultId(fromVault.getId())
                    .categoryId(fromBullion.getCategory().getId());

            BigDecimal fromAmount = fromBullion.getAmount();
            BigDecimal toBullionAmountBefore = BigDecimal.ZERO;

            var existingBullion = bullionRepository.findByVaultAndCategory(toVault, fromBullion.getCategory());

            if (existingBullion.isPresent()) {
                toBullionAmountBefore = existingBullion.get().getAmount();
                existingBullion.get().setAmount(existingBullion.get().getAmount().add(fromAmount).setScale(2, RoundingMode.HALF_UP));
                bullionRepository.save(existingBullion.get());
                bullionRepository.delete(fromBullion);

                logBuilder.toBullionId(existingBullion.get().getId())
                        .toBullionAmountBefore(toBullionAmountBefore)
                        .toBullionAmountAfter(existingBullion.get().getAmount());
            } else {
                Bullion newBullion = Bullion.builder()
                        .category(fromBullion.getCategory())
                        .vault(toVault)
                        .amount(fromAmount)
                        .description(fromBullion.getDescription())
                        .user(user)
                        .build();
                bullionRepository.save(newBullion);
                bullionRepository.delete(fromBullion);

                logBuilder.toBullionId(newBullion.getId())
                        .toBullionAmountBefore(BigDecimal.ZERO)
                        .toBullionAmountAfter(newBullion.getAmount());
            }

            logBuilder.fromBullionAmountBefore(fromAmount)
                    .fromBullionAmountAfter(BigDecimal.ZERO)
                    .description("Перемещение слитка " + fromBullionId + " в хранилище " + toVaultId);

            transactionLogRepository.save(logBuilder.build());
            log.info("Transfer bullion success: bullionId={}, toVaultId={}", fromBullionId, toVaultId);

        } catch (ApplicationException e) {
            logBuilder.status("FAILED").errorMessage(e.getMessage());
            transactionLogRepository.save(logBuilder.build());
            throw e;
        }
    }

    @Transactional
    public void rollbackLastTransaction(Long userId) {
        List<TransactionLog> lastLogs = transactionLogRepository.findByUserIdOrderByCreatedAtDesc(userId);

        if (lastLogs.isEmpty()) {
            throw new ApplicationException(4000, "Нет транзакций для отката");
        }

        rollbackTransaction(lastLogs.get(0).getId());
    }

    @Transactional
    public void rollbackBatch(Long batchId) {
        List<TransactionLog> batchLogs = transactionLogRepository.findByBatchIdOrderByCreatedAtDesc(batchId);

        if (batchLogs.isEmpty()) {
            throw new ApplicationException(4000, "Batch с id " + batchId + " не найден");
        }

        for (TransactionLog log : batchLogs) {
            if ("SUCCESS".equals(log.getStatus())) {
                rollbackTransaction(log.getId());
            }
        }

        log.info("Rollback batch success: batchId={}", batchId);
    }

    @Transactional
    public void rollbackTransaction(Long transactionLogId) {
        TransactionLog originalLog = transactionLogRepository.findById(transactionLogId)
                .orElseThrow(() -> new ApplicationException(TRANSACTION_NOT_FOUND.getCode(), TRANSACTION_NOT_FOUND.getMessage()));

        if (!"SUCCESS".equals(originalLog.getStatus())) {
            throw new ApplicationException(4000, "Можно откатить только успешную транзакцию. Текущий статус: " + originalLog.getStatus());
        }

        if ("ROLLED_BACK".equals(originalLog.getStatus())) {
            throw new ApplicationException(4000, "Транзакция уже была откатана");
        }

        try {
            switch (originalLog.getOperationType()) {
                case "REFILL_BULLION" -> rollbackRefillBullion(originalLog);
                case "WITHDRAW_BULLION" -> rollbackWithdrawBullion(originalLog);
                case "TRANSFER_AMOUNT" -> rollbackTransferAmount(originalLog);
                case "TRANSFER_BULLION" -> rollbackTransferBullion(originalLog);
                default -> throw new ApplicationException(4000, "Неизвестный тип операции: " + originalLog.getOperationType());
            }

            originalLog.setStatus("ROLLED_BACK");
            transactionLogRepository.save(originalLog);

            TransactionLog rollbackLog = TransactionLog.builder()
                    .operationType(ROLLBACK_.name() + originalLog.getOperationType())
                    .fromBullionId(originalLog.getToBullionId())
                    .toBullionId(originalLog.getFromBullionId())
                    .fromVaultId(originalLog.getToVaultId())
                    .toVaultId(originalLog.getFromVaultId())
                    .categoryId(originalLog.getCategoryId())
                    .amount(originalLog.getAmount())
                    .userId(originalLog.getUserId())
                    .batchId(originalLog.getBatchId())
                    .parentTransactionId(originalLog.getId())
                    .status("SUCCESS")
                    .description("Откат транзакции " + originalLog.getId())
                    .build();

            transactionLogRepository.save(rollbackLog);
            log.info("Rollback transaction success: logId={}", transactionLogId);

        } catch (Exception e) {
            log.error("Rollback failed: {}", e.getMessage());
            throw new ApplicationException(5000, "Ошибка при откате: " + e.getMessage());
        }
    }


    private void rollbackRefillBullion(TransactionLog log) {
        Bullion bullion = bullionRepository.findById(log.getToBullionId())
                .orElseThrow(() -> new ApplicationException(4006, "Слиток не найден"));

        bullion.setAmount(log.getToBullionAmountBefore());
        bullionRepository.save(bullion);
    }

    private void rollbackWithdrawBullion(TransactionLog log) {
        Bullion bullion = bullionRepository.findById(log.getToBullionId())
                .orElseThrow(() -> new ApplicationException(4006, "Слиток не найден"));

        bullion.setAmount(log.getToBullionAmountBefore());
        bullionRepository.save(bullion);
    }

    private void rollbackTransferAmount(TransactionLog log) {
        Bullion fromBullion = bullionRepository.findById(log.getFromBullionId())
                .orElseThrow(() -> new ApplicationException(4006, "Слиток-отправитель не найден"));

        Bullion toBullion = bullionRepository.findById(log.getToBullionId())
                .orElseThrow(() -> new ApplicationException(4006, "Слиток-получатель не найден"));

        fromBullion.setAmount(log.getFromBullionAmountBefore());
        toBullion.setAmount(log.getToBullionAmountBefore());

        bullionRepository.save(fromBullion);
        bullionRepository.save(toBullion);
    }

    private void rollbackTransferBullion(TransactionLog log) {
        if (log.getFromBullionAmountAfter() == null || log.getFromBullionAmountAfter().compareTo(BigDecimal.ZERO) != 0) {
            throw new ApplicationException(4000, "Невозможно откатить: слиток уже был изменен");
        }

        Category category = categoryRepository.findById(log.getCategoryId())
                .orElseThrow(() -> new ApplicationException(4006, "Категория не найдена"));

        Vault fromVault = vaultRepository.findById(log.getFromVaultId())
                .orElseThrow(() -> new ApplicationException(4006, "Исходное хранилище не найдено"));

        User user = new User();
        user.setId(log.getUserId());

        Bullion restoredBullion = Bullion.builder()
                .category(category)
                .vault(fromVault)
                .amount(log.getFromBullionAmountBefore())
                .description("Восстановлен при откате транзакции " + log.getId())
                .user(user)
                .build();

        Bullion savedBullion = bullionRepository.save(restoredBullion);

        if (log.getToBullionId() != null) {
            bullionRepository.findById(log.getToBullionId()).ifPresent(toBullion -> {
                toBullion.setAmount(log.getToBullionAmountBefore());
                bullionRepository.save(toBullion);
            });
        }

        log.setFromBullionId(savedBullion.getId());
    }



    public Long createBatchId() {
        return System.currentTimeMillis();
    }

    public List<TransactionLog> getBullionHistory(Long bullionId) {
        return transactionLogRepository.findByFromBullionIdOrderByCreatedAtDesc(bullionId);
    }

    @Transactional(readOnly = true)
    public DashboardStatisticsDto getDashboardStatistics(Long userId, Integer year) {
        LocalDateTime fromDate;
        LocalDateTime toDate = LocalDateTime.now();

        if (year != null) {
            fromDate = LocalDate.of(year, 1, 1).atStartOfDay();
            toDate = LocalDate.of(year, 12, 31).atTime(23, 59, 59);
        } else {
            fromDate = LocalDate.of(1970, 1, 1).atStartOfDay();
        }

        List<MonthlyDataDto> monthlyData = getMonthlyStatistics(userId, fromDate, toDate);


        BigDecimal startBalance = getBalanceBeforeYear(userId, year);
        BigDecimal cumulative = startBalance;

        for (MonthlyDataDto month : monthlyData) {
            cumulative = cumulative.add(month.getNetChange());
            month.setSavings(cumulative);
        }

        Object[] totalStats = transactionLogRepository.getTotalStatistics(userId, fromDate, toDate);

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        Long totalTransactions = 0L;

        if (totalStats != null && totalStats.length > 0) {
            Object[] row = (Object[]) totalStats[0];
            totalIncome = row[0] != null ? new BigDecimal(row[0].toString()) : BigDecimal.ZERO;
            totalExpense = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
            totalTransactions = row[2] != null ? ((Number) row[2]).longValue() : 0L;
        }

        List<TransactionLog> recentLogs = transactionLogRepository.findLastSuccessfulTransactions(userId, 5);
        List<TransactionLogDto> recentTransactions = recentLogs.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        List<Object[]> opStats = transactionLogRepository.getOperationTypeStatistics(userId, fromDate, toDate);
        List<OperationTypeStatsDto> operationTypeStats = opStats.stream()
                .map(row -> OperationTypeStatsDto.builder()
                        .operationType((String) row[0])
                        .count(((Number) row[1]).longValue())
                        .totalAmount((BigDecimal) row[2])
                        .build())
                .collect(Collectors.toList());

        return DashboardStatisticsDto.builder()
                .monthlyData(monthlyData)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netChange(totalIncome.subtract(totalExpense))
                .totalTransactions(totalTransactions)
                .recentTransactions(recentTransactions)
                .operationTypeStats(operationTypeStats.isEmpty() ? null : operationTypeStats.get(0))
                .build();
    }

    @Transactional(readOnly = true)
    public List<MonthlyDataDto> getMonthlyStatistics(Long userId, LocalDateTime fromDate, LocalDateTime toDate) {
        List<Object[]> results = transactionLogRepository.getMonthlyStatistics(userId, fromDate, toDate);

        if (results.isEmpty()) {
            return Collections.emptyList();
        }

        List<MonthlyDataDto> monthlyData = new ArrayList<>();

        for (Object[] row : results) {
            java.sql.Timestamp timestamp = (java.sql.Timestamp) row[0];
            LocalDateTime monthDate = timestamp.toLocalDateTime();

            BigDecimal income = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
            BigDecimal expense = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
            Long transactionCount = row[3] != null ? ((Number) row[3]).longValue() : 0L;

            String month = monthDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            String monthLabel = monthDate.format(DateTimeFormatter.ofPattern("MMM yyyy"));

            MonthlyDataDto dto = MonthlyDataDto.builder()
                    .month(month)
                    .monthLabel(monthLabel)
                    .income(income)
                    .expense(expense)
                    .netChange(income.subtract(expense))
                    .transactionCount(transactionCount)
                    .build();

            monthlyData.add(dto);
        }

        return monthlyData;
    }

    @Transactional(readOnly = true)
    public TransactionHistoryResponse getTransactionHistory(
            Long userId,
            String operationType,
            String status,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Pageable pageable) {

        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();

        List<TransactionLog> logs = transactionLogRepository.findTransactionHistory(userId, offset, limit);
        long total = transactionLogRepository.countTransactionHistory(userId);

        List<TransactionLogDto> content = logs.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) total / limit);

        return TransactionHistoryResponse.builder()
                .content(content)
                .totalElements(total)
                .totalPages(totalPages)
                .currentPage(pageable.getPageNumber())
                .pageSize(limit)
                .build();
    }

    @Transactional(readOnly = true)
    public List<TransactionLogDto> getTransactionChain(Long transactionLogId) {
        List<TransactionLog> chain = transactionLogRepository
                .findByParentTransactionIdOrIdOrderByCreatedAtAsc(transactionLogId, transactionLogId);

        return chain.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Integer> getAvailableYears(Long userId) {
        Optional<TransactionLog> first = transactionLogRepository
                .findFirstByUserIdOrderByCreatedAtDesc(userId);

        if (first.isEmpty()) {
            return Collections.singletonList(LocalDate.now().getYear());
        }

        int currentYear = LocalDate.now().getYear();
        int firstYear = first.get().getCreatedAt().getYear();

        List<Integer> years = new ArrayList<>();
        for (int year = firstYear; year <= currentYear; year++) {
            years.add(year);
        }
        return years;
    }

    private TransactionLogDto convertToDto(TransactionLog log) {
        boolean canRollback = "SUCCESS".equals(log.getStatus()) &&
                !"ROLLED_BACK".equals(log.getStatus()) &&
                !"REDONE".equals(log.getStatus());

        boolean canRedo = "ROLLED_BACK".equals(log.getStatus());

        String rollbackStatus = null;
        if ("ROLLED_BACK".equals(log.getStatus())) {
            rollbackStatus = "ROLLED_BACK";
        } else if ("REDONE".equals(log.getStatus())) {
            rollbackStatus = "REDONE";
        }

        return TransactionLogDto.builder()
                .id(log.getId())
                .operationType(log.getOperationType())
                .fromBullionId(log.getFromBullionId())
                .toBullionId(log.getToBullionId())
                .amount(log.getAmount())
                .description(log.getDescription())
                .userComment(log.getUserComment())
                .status(log.getStatus())
                .errorMessage(log.getErrorMessage())
                .createdAt(log.getCreatedAt())
                .canRollback(canRollback)
                .canRedo(canRedo)
                .rollbackStatus(rollbackStatus)
                .parentTransactionId(log.getParentTransactionId())
                .build();
    }

    @Transactional(readOnly = true)
    public DashboardDailyStatisticsDto getDailyStatistics(Long userId, int year, int month) {
        List<Object[]> results = transactionLogRepository.getDailyStatistics(userId, year, month);

        String monthName = LocalDate.of(year, month, 1)
                .format(DateTimeFormatter.ofPattern("MMMM yyyy"));

        List<DashboardDailyStatisticsDto.DailyDataDto> dailyData = new ArrayList<>();
        BigDecimal cumulativeTotal = BigDecimal.ZERO;

        // Получаем количество дней в месяце
        int daysInMonth = LocalDate.of(year, month, 1).lengthOfMonth();

        // Создаем мапу для быстрого доступа к данным по дням
        Map<Integer, Object[]> dayMap = results.stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).intValue(),
                        row -> row
                ));

        for (int day = 1; day <= daysInMonth; day++) {
            Object[] row = dayMap.get(day);

            BigDecimal income = BigDecimal.ZERO;
            BigDecimal expense = BigDecimal.ZERO;
            Long transactionCount = 0L;

            if (row != null) {
                income = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
                expense = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
                transactionCount = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            }

            BigDecimal dailyChange = income.subtract(expense);
            cumulativeTotal = cumulativeTotal.add(dailyChange);

            DashboardDailyStatisticsDto.DailyDataDto dto = DashboardDailyStatisticsDto.DailyDataDto.builder()
                    .day(day)
                    .date(String.format("%02d.%02d", day, month))
                    .savings(cumulativeTotal)
                    .dailyChange(dailyChange)
                    .income(income)
                    .expense(expense)
                    .transactionCount(transactionCount)
                    .build();

            dailyData.add(dto);
        }

        return DashboardDailyStatisticsDto.builder()
                .year(year)
                .month(month)
                .monthLabel(monthName)
                .totalAmount(cumulativeTotal)
                .dailyData(dailyData)
                .build();

    }


    @Transactional(readOnly = true)
    public BigDecimal getBalanceBeforeYear(Long userId, int year) {
        LocalDateTime startOfYear = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime endOfPreviousYear = startOfYear.minusNanos(1);

        Object[][] stats = transactionLogRepository.getTotalStatistics(
                userId,
                LocalDate.of(1970, 1, 1).atStartOfDay(),
                endOfPreviousYear
        );

        if (stats != null && stats.length > 0) {
            // Безопасное преобразование через Number
            BigDecimal totalIncome = stats[0][0] != null && stats[0][0] instanceof Number
                    ? BigDecimal.valueOf(((Number) stats[0][0]).doubleValue())
                    : BigDecimal.ZERO;

            BigDecimal totalExpense = stats[0][1] != null && stats[0][1] instanceof Number
                    ? BigDecimal.valueOf(((Number) stats[0][1]).doubleValue())
                    : BigDecimal.ZERO;

            return totalIncome.subtract(totalExpense);
        }

        return BigDecimal.ZERO;
    }
}