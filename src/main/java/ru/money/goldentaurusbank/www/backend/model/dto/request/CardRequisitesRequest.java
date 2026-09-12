package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CardRequisitesRequest {

    @NotBlank(message = "Реквизиты не переданы")
    @Size(max = 1_048_576, message = "Реквизиты слишком большие")
    private String payload;

    private Long version;
}
