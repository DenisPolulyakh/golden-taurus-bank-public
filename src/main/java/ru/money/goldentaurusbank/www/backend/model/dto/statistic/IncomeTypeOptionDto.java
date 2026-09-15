package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.IncomeType;

/** Строка справочника типов дохода для выпадающего списка на фронте. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeTypeOptionDto {
    private IncomeType code;
    private String displayName;
}
