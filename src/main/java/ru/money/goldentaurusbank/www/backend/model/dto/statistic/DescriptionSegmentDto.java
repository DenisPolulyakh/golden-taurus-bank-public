package ru.money.goldentaurusbank.www.backend.model.dto.statistic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Часть описания транзакции с собственным цветом текста.
 * Позволяет фронтенду раскрашивать название слитка (цвет категории),
 * банк и хранилище разными цветами. Если {@code color == null} —
 * используется цвет текста по умолчанию.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DescriptionSegmentDto {
    private String text;
    private String color;
}
