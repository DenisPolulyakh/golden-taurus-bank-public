package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BullionNameRequest {

    @NotBlank(message = "Название наименования обязательно")
    @Size(min = 2, max = 100, message = "Название должно быть от 2 до 100 символов")
    private String title;

    @Size(min = 7, max = 7, message = "Цвет должен быть в формате HEX (#FFFFFF)")
    private String color;
}
