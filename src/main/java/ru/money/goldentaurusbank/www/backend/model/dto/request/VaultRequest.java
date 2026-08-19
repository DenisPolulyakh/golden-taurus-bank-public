package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.AccountType;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.VaultType;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class VaultRequest {

    @NotBlank(message = "Название хранилища обязательно")
    @Size(min = 2, max = 100, message = "Название должно содержать от 2 до 100 символов")
    private String name;

    @DecimalMin(value = "0.0", message = "Ставка не может быть отрицательной")
    @DecimalMax(value = "100.0", message = "Ставка не может превышать 100%")
    private BigDecimal interestRate;

    @Size(max = 500, message = "Описание не должно превышать 500 символов")
    private String description;

    private Long bankId;

    private VaultType vaultType;

    private AccountType accountType;

    private LocalDate closeDate;

    private Boolean allowedIncome;

    private Boolean allowedExpense;

    private Boolean allowedTransfer;

}