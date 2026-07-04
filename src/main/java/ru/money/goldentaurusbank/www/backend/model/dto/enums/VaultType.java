package ru.money.goldentaurusbank.www.backend.model.dto.enums;

public enum VaultType {
    REGULAR,           // обычное хранилище (сейф, копилка) - создается по умолчанию
    LIQUIDITY_BUFFER   // буфер для перераспределения - системный, только один
}