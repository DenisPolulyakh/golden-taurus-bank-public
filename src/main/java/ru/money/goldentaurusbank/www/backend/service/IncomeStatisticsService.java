package ru.money.goldentaurusbank.www.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.IncomeStatGranularity;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.IncomeType;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.IncomeStatPointDto;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.IncomeStatisticsDto;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.IncomeTypeOptionDto;
import ru.money.goldentaurusbank.www.backend.model.dto.statistic.IncomeTypeTotalDto;
import ru.money.goldentaurusbank.www.backend.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.VALIDATION_ERROR;

/**
 * Статистика доходов по типу (см. {@link IncomeType}) для графиков на
 * странице «Доходы». Отдельный сервис, а не часть {@link TransactionService}:
 * своя ось группировки (день/месяц/год) и свой диапазон дат, не пересекается
 * с дашбордом и месячным бюджетом.
 */
@Service
@RequiredArgsConstructor
public class IncomeStatisticsService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("d MMMM");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("LLLL yyyy");

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public IncomeStatisticsDto getIncomeStatistics(Long userId, String granularityParam, Integer year, Integer month) {
        IncomeStatGranularity granularity = parseGranularity(granularityParam);

        LocalDate from;
        LocalDate to;
        switch (granularity) {
            case DAY -> {
                YearMonth ym = YearMonth.of(
                        year != null ? year : LocalDate.now().getYear(),
                        month != null ? month : LocalDate.now().getMonthValue());
                from = ym.atDay(1);
                to = ym.atEndOfMonth();
            }
            case MONTH -> {
                int y = year != null ? year : LocalDate.now().getYear();
                from = LocalDate.of(y, 1, 1);
                to = LocalDate.of(y, 12, 31);
            }
            default -> {
                LocalDateTime earliest = transactionRepository.findEarliestIncomeDate(userId);
                int startYear = earliest != null ? earliest.getYear() : LocalDate.now().getYear();
                from = LocalDate.of(startYear, 1, 1);
                to = LocalDate.of(LocalDate.now().getYear(), 12, 31);
            }
        }

        List<Object[]> rows = transactionRepository.getIncomeStatistics(
                userId, granularity.name().toLowerCase(), from.atStartOfDay(), to.atTime(LocalTime.MAX));

        // bucket key -> (тип -> сумма); bucket приходит из БД уже усечённым date_trunc'ом,
        // им же будет усечён и сгенерированный пустой период — ключи совпадут.
        Map<LocalDate, Map<IncomeType, BigDecimal>> byBucket = new LinkedHashMap<>();
        Map<IncomeType, BigDecimal> totalsByType = new EnumMap<>(IncomeType.class);
        Map<IncomeType, Long> countsByType = new EnumMap<>(IncomeType.class);

        for (Object[] row : rows) {
            LocalDate bucket = toLocalDateTime(row[0]).toLocalDate();
            IncomeType type = IncomeType.valueOf((String) row[1]);
            BigDecimal amount = toBigDecimal(row[2]);
            Long count = toLong(row[3]);

            byBucket.computeIfAbsent(bucket, k -> new EnumMap<>(IncomeType.class)).merge(type, amount, BigDecimal::add);
            totalsByType.merge(type, amount, BigDecimal::add);
            countsByType.merge(type, count, Long::sum);
        }

        List<IncomeStatPointDto> points = buildPoints(granularity, from, to, byBucket);

        BigDecimal total = totalsByType.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<IncomeTypeTotalDto> byType = new ArrayList<>();
        for (IncomeType type : IncomeType.values()) {
            BigDecimal amount = totalsByType.get(type);
            if (amount == null) {
                continue;
            }
            byType.add(IncomeTypeTotalDto.builder()
                    .type(type)
                    .displayName(type.getDisplayName())
                    .amount(amount)
                    .count(countsByType.get(type))
                    .build());
        }

        return IncomeStatisticsDto.builder()
                .granularity(granularity)
                .from(from)
                .to(to)
                .total(total)
                .byType(byType)
                .points(points)
                .build();
    }

    public List<IncomeTypeOptionDto> getIncomeTypes() {
        return java.util.Arrays.stream(IncomeType.values())
                .map(type -> IncomeTypeOptionDto.builder().code(type).displayName(type.getDisplayName()).build())
                .toList();
    }

    private List<IncomeStatPointDto> buildPoints(IncomeStatGranularity granularity, LocalDate from, LocalDate to,
                                                  Map<LocalDate, Map<IncomeType, BigDecimal>> byBucket) {
        List<IncomeStatPointDto> points = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            Map<IncomeType, BigDecimal> byType = byBucket.getOrDefault(cursor, Map.of());
            BigDecimal total = byType.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

            points.add(IncomeStatPointDto.builder()
                    .period(formatPeriod(granularity, cursor))
                    .periodLabel(formatLabel(granularity, cursor))
                    .total(total)
                    .byType(byType)
                    .build());

            cursor = switch (granularity) {
                case DAY -> cursor.plusDays(1);
                case MONTH -> cursor.plusMonths(1);
                case YEAR -> cursor.plusYears(1);
            };
        }
        return points;
    }

    private String formatPeriod(IncomeStatGranularity granularity, LocalDate date) {
        return switch (granularity) {
            case DAY -> date.toString();
            case MONTH -> date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            case YEAR -> String.valueOf(date.getYear());
        };
    }

    private String formatLabel(IncomeStatGranularity granularity, LocalDate date) {
        return switch (granularity) {
            case DAY -> date.format(DAY_LABEL);
            case MONTH -> date.format(MONTH_LABEL);
            case YEAR -> String.valueOf(date.getYear());
        };
    }

    private IncomeStatGranularity parseGranularity(String value) {
        if (value == null) {
            throw new ApplicationException(VALIDATION_ERROR.getCode(), "Не указан granularity");
        }
        try {
            return IncomeStatGranularity.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(VALIDATION_ERROR.getCode(),
                    "Неизвестный granularity: " + value + ". Допустимо: DAY, MONTH, YEAR");
        }
    }

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
        throw new ApplicationException(ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.INTERNAL_ERROR.getCode(),
                "Неожиданный тип колонки bucket: " + value.getClass().getName());
    }
}
