package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MoveBullionsRequest {

    @NotNull(message = "Хранилище-получатель обязательно")
    private Long toVaultId;

    private LocalDateTime dateOperation;

    @NotEmpty(message = "Не выбран ни один слиток")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {

        @NotNull(message = "ID слитка обязателен")
        private Long bullionId;

        @NotNull(message = "Сумма переноса обязательна")
        @Positive(message = "Сумма должна быть больше 0")
        private BigDecimal amount;
    }
}
