package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreditCardRequest {

    @NotBlank(message = "Название карты обязательно")
    @Size(min = 2, max = 100, message = "Название должно содержать от 2 до 100 символов")
    private String name;

    /**
     * При создании обязателен, при правке пустой означает «оставить прежний»:
     * наружу номер не отдаётся, подставить его в форму нечем.
     */
    private String cardNumber;

    private LocalDate gracePeriodDate;

    @DecimalMin(value = "0.0", message = "Лимит не может быть отрицательным")
    private BigDecimal limit;

    @DecimalMin(value = "0.0", message = "Задолженность не может быть отрицательной")
    private BigDecimal debt;

    /** Слитки-накопители целиком: чего нет в списке — то отвязывается. */
    private List<Long> bullionIds;
}
