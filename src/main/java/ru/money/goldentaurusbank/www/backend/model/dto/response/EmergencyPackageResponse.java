package ru.money.goldentaurusbank.www.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmergencyPackageResponse {

    private LocalDateTime generatedAt;

    private List<CardLine> cards;

    private CardRequisitesResponse vault;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CardLine {

        private Long cardId;

        private String name;

        private String last4;

        private BigDecimal debt;

        private BigDecimal limit;

        private LocalDate gracePeriodDate;
    }
}
