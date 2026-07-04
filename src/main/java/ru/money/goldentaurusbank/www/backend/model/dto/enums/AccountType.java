package ru.money.goldentaurusbank.www.backend.model.dto.enums;

public enum AccountType {
    SAVINGS("Накопительный"),
    TERM("Срочный");

    private final String displayName;

    AccountType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}