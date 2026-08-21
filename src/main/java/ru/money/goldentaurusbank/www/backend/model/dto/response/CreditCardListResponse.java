package ru.money.goldentaurusbank.www.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Все карты одной страницей — пагинации нет. Итоги считаются по всем активным
 * картам пользователя, а не по результату поиска: в шапке стоит «Текущий долг»,
 * а не «долг найденного».
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditCardListResponse {

    private BigDecimal totalDebt;
    private BigDecimal totalLimit;
    private Integer count;
    private List<CreditCardResponse> cards;
}
