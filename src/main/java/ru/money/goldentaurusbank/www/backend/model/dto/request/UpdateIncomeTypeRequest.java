package ru.money.goldentaurusbank.www.backend.model.dto.request;

import lombok.Data;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.IncomeType;

@Data
public class UpdateIncomeTypeRequest {

    /** {@code null} сбрасывает классификацию — операция снова без типа дохода. */
    private IncomeType incomeType;
}
