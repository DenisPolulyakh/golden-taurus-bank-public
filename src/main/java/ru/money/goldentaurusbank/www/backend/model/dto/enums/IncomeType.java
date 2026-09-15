package ru.money.goldentaurusbank.www.backend.model.dto.enums;

public enum IncomeType {
    ADVANCE("Аванс"),
    SALARY("Зарплата"),
    DEPOSIT_INTEREST("% по вкладу"),
    DIVIDENDS("Дивиденды"),
    CASHBACK("Кэшбек"),
    TAX_DEDUCTION("Налоговый вычет"),
    WINDFALL("Внезапный доход"),
    GIFTS("Подарки"),
    OTHER("Прочее");

    private final String displayName;

    IncomeType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
