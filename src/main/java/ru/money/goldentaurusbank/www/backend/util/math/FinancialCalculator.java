package ru.money.goldentaurusbank.www.backend.util.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;


/**
 * Утилитарный класс для финансовых расчетов
 */
public final class FinancialCalculator {
    
    private static final int DEFAULT_SCALE = 4;
    private static final int OUTPUT_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal(100);
    
    private FinancialCalculator() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Расчет средневзвешенной ставки
     * 
     * @param items Коллекция объектов
     * @param amountExtractor Функция извлечения суммы
     * @param rateExtractor Функция извлечения ставки (в процентах)
     * @return Средневзвешенная ставка в процентах
     */
    public static <T> BigDecimal calculateWeightedAverageRate(
            Collection<T> items,
            ToBigDecimalFunction<T> amountExtractor,
            ToBigDecimalFunction<T> rateExtractor) {
        
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        
        for (T item : items) {
            BigDecimal amount = amountExtractor.applyAsBigDecimal(item);
            BigDecimal rate = rateExtractor.applyAsBigDecimal(item);
            
            weightedSum = weightedSum.add(amount.multiply(rate));
            totalAmount = totalAmount.add(amount);
        }
        
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // Формула: Σ(amount × rate) / total_amount
        // Результат уже в процентах, т.к. rate в процентах
        return weightedSum
                .divide(totalAmount, DEFAULT_SCALE, RoundingMode.HALF_UP)
                .setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }
    
    /**
     * Расчет средневзвешенной ставки с делением на 100
     * 
     * @param items Коллекция объектов
     * @param amountExtractor Функция извлечения суммы
     * @param rateExtractor Функция извлечения ставки (в процентах)
     * @return Средневзвешенная ставка в процентах
     */
    public static <T> AverageData calculateWeightedAverageRateWithDecimal(
            Collection<T> items,
            ToBigDecimalFunction<T> amountExtractor,
            ToBigDecimalFunction<T> rateExtractor) {
        
        if (items == null || items.isEmpty()) {
            return new AverageData(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        
        for (T item : items) {
            BigDecimal amount = amountExtractor.applyAsBigDecimal(item);
            BigDecimal rate = rateExtractor.applyAsBigDecimal(item);
            BigDecimal rateAsDecimal = rate.divide(ONE_HUNDRED, DEFAULT_SCALE, RoundingMode.HALF_UP);
            
            weightedSum = weightedSum.add(amount.multiply(rateAsDecimal));
            totalAmount = totalAmount.add(amount);
        }
        
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            return new AverageData(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        // Средняя в десятичном виде, затем переводим в проценты
        BigDecimal decimalRate = weightedSum.divide(totalAmount, DEFAULT_SCALE, RoundingMode.HALF_UP);
        return new AverageData(totalAmount, decimalRate.multiply(ONE_HUNDRED).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP));
    }
    
    /**
     * Расчет средневзвешенной доходности портфеля
     * 
     * @param items Коллекция объектов
     * @param amountExtractor Функция извлечения суммы
     * @param yieldExtractor Функция извлечения доходности (в процентах)
     * @return Средневзвешенная доходность в процентах
     */
    public static <T> BigDecimal calculateWeightedAverageYield(
            Collection<T> items,
            ToBigDecimalFunction<T> amountExtractor,
            ToBigDecimalFunction<T> yieldExtractor) {
        return calculateWeightedAverageRate(items, amountExtractor, yieldExtractor);
    }
    
    /**
     * Расчет общей суммы
     */
    public static <T> BigDecimal calculateTotalAmount(
            Collection<T> items,
            ToBigDecimalFunction<T> amountExtractor) {
        
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        return items.stream()
                .map(amountExtractor::applyAsBigDecimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Расчет процентного соотношения
     * 
     * @param part Часть
     * @param total Общее
     * @return Процентное соотношение
     */
    public static BigDecimal calculatePercentage(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (part == null) {
            return BigDecimal.ZERO;
        }
        
        return part.multiply(ONE_HUNDRED)
                .divide(total, OUTPUT_SCALE, RoundingMode.HALF_UP);
    }
    
    /**
     * Расчет сложного процента (будущая стоимость)
     * 
     * @param principal Начальная сумма
     * @param rate Годовая ставка (в процентах)
     * @param periods Количество периодов
     * @return Будущая стоимость
     */
    public static BigDecimal calculateFutureValue(BigDecimal principal, BigDecimal rate, int periods) {
        if (principal == null || rate == null || periods <= 0) {
            return principal != null ? principal : BigDecimal.ZERO;
        }
        
        BigDecimal rateDecimal = rate.divide(ONE_HUNDRED, DEFAULT_SCALE, RoundingMode.HALF_UP);
        BigDecimal factor = BigDecimal.ONE.add(rateDecimal).pow(periods);
        return principal.multiply(factor).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }
    
    /**
     * Расчет эффективной процентной ставки
     * 
     * @param nominalRate Номинальная ставка (в процентах)
     * @param periodsPerYear Количество периодов капитализации в год
     * @return Эффективная ставка в процентах
     */
    public static BigDecimal calculateEffectiveRate(BigDecimal nominalRate, int periodsPerYear) {
        if (nominalRate == null || periodsPerYear <= 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal rateDecimal = nominalRate.divide(ONE_HUNDRED, DEFAULT_SCALE, RoundingMode.HALF_UP);
        BigDecimal factor = BigDecimal.ONE.add(rateDecimal.divide(new BigDecimal(periodsPerYear), DEFAULT_SCALE, RoundingMode.HALF_UP));
        BigDecimal result = factor.pow(periodsPerYear).subtract(BigDecimal.ONE);
        
        return result.multiply(ONE_HUNDRED).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }
    
    /**
     * Расчет доходности к погашению (упрощенная формула)
     * 
     * @param faceValue Номинальная стоимость
     * @param price Цена покупки
     * @param coupon Купонный доход
     * @param yearsToMaturity Лет до погашения
     * @return Доходность к погашению в процентах
     */
    public static BigDecimal calculateYieldToMaturity(
            BigDecimal faceValue, 
            BigDecimal price, 
            BigDecimal coupon, 
            int yearsToMaturity) {
        
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal annualReturn = coupon.add(
                faceValue.subtract(price).divide(new BigDecimal(yearsToMaturity), DEFAULT_SCALE, RoundingMode.HALF_UP)
        );
        
        BigDecimal avgPrice = faceValue.add(price).divide(new BigDecimal(2), DEFAULT_SCALE, RoundingMode.HALF_UP);
        
        return annualReturn.divide(avgPrice, DEFAULT_SCALE, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }

    public record AverageData(BigDecimal totalAmount, BigDecimal avgRate) {}
}

