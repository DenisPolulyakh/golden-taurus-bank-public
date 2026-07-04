package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankRequest {

    @NotBlank(message = "Название банка обязательно")
    @Size(min = 2, max = 100, message = "Название банка должно содержать от 2 до 100 символов")
    private String name;
}