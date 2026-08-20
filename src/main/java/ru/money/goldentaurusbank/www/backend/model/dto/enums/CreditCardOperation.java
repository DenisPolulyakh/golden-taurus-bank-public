package ru.money.goldentaurusbank.www.backend.model.dto.enums;

/**
 * Операция по кредитной карте. Долг двигают все три, но по-разному:
 * {@code SPEND} и {@code OPENING_DEBT} его увеличивают, {@code REPAY} уменьшает.
 */
public enum CreditCardOperation {

    /** Трата по карте: списание лимита, долг растёт. */
    SPEND,

    /** Погашение задолженности: долг падает. */
    REPAY,

    /**
     * Стартовая задолженность при заведении карты. Отделена от траты по той же
     * причине, что {@code OPENING_BALANCE} у слитков: это регистрация уже
     * существующего долга, а не новая трата.
     */
    OPENING_DEBT;

    /** Знак операции относительно задолженности. */
    public boolean increasesDebt() {
        return this != REPAY;
    }
}
