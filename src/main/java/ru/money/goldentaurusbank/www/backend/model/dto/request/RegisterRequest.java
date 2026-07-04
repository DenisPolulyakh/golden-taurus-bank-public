package ru.money.goldentaurusbank.www.backend.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный формат email")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 6, message = "Пароль должен содержать минимум 6 символов")
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[%$*]).*$",
            message = "Пароль должен содержать хотя бы одну цифру и один спецсимвол (% $ *)"
    )
    private String password;

    @NotBlank(message = "Имя обязательно")
    private String fullName;
}