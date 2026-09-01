package ru.money.goldentaurusbank.www.backend.model.dto.request;

import lombok.Data;

/**
 * Настройки отчёта о бюджете. Оба поля необязательные: {@code null} снимает
 * признак, ничего не назначая взамен — так отчёт отвязывается от слитка.
 */
@Data
public class BudgetSettingsRequest {

    /** Слиток, по которому считается бюджет */
    private Long budgetBullionId;

    /** Слиток, с которого бюджет финансируется кнопкой */
    private Long sourceBullionId;
}
