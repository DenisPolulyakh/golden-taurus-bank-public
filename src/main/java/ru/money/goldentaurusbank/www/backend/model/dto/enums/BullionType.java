package ru.money.goldentaurusbank.www.backend.model.dto.enums;

/**
 * Дебетовый слиток — свои накопления, кредитный — заёмные средства.
 * На расчёты пока не влияет: заготовка под учёт кредитов.
 */
public enum BullionType {
    DEBIT("Дебетовый"),
    CREDIT("Кредитный");

    private final String displayName;

    BullionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
