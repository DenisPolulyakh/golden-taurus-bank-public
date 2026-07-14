package ru.money.goldentaurusbank.www.backend.service;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Getter
public class ColorConstants {
    
    private final List<String> allColors;

    public ColorConstants() {
        this.allColors = Collections.unmodifiableList(List.of(
            // Красные (5)
            "#FF6B6B", "#FF4757", "#FF6348", "#FF7F50", "#EE5A24",
            // Оранжевые (5)
            "#FFA502", "#F9CA24", "#F6B93B", "#FFC312", "#F0932B",
            // Желтые (5)
            "#F6E58D", "#FFD93D", "#F9E79F", "#FFD700", "#F4D03F",
            // Зеленые (10)
            "#2ED573", "#26DE81", "#7BED9F", "#A3CB38", "#6AB04C",
            "#BADB57", "#82E0AA", "#58D68D", "#2ECC71", "#1ABC9C",
            // Бирюзовые (5)
            "#00CEC9", "#0ABDE3", "#00B894", "#00A8FF", "#36D399",
            // Синие (10)
            "#1E90FF", "#3742FA", "#4A69BD", "#6A89CC", "#48DBFB",
            "#686DE0", "#30336B", "#4834D4", "#0652DD", "#1289A7",
            // Фиолетовые (10)
            "#A29BFE", "#6C5CE7", "#8E44AD", "#9B59B6", "#D980FA",
            "#B33771", "#6D214F", "#C39BD3", "#AF7AC5", "#7D3C98",
            // Розовые (8)
            "#FD79A8", "#E84393", "#F8A5C2", "#F19066", "#E15F41",
            "#C44569", "#FDA7DF", "#F5B7B1",
            // Серые/Нейтральные (7)
            "#85929E", "#95A5A6", "#7F8C8D", "#BDC3C7", "#A6ACAF",
            "#D5D8DC", "#ABB2B9",
            // Дополнительные яркие (5)
            "#FD7272", "#55E6C1", "#58B19F", "#D6A2E8", "#F97F51"
            // Итого: 70 цветов
        ));
    }

    public int getColorsCount() {
        return allColors.size();
    }

    public boolean containsColor(String color) {
        return color != null && allColors.contains(color);
    }

    public String getColorByIndex(int index) {
        if (index < 0 || index >= allColors.size()) {
            return allColors.isEmpty() ? null : allColors.get(0);
        }
        return allColors.get(index);
    }

    public String getFirstColor() {
        return allColors.isEmpty() ? null : allColors.get(0);
    }

    // Сопоставление hex-цвета с русским названием цветовой группы (по палитре выше)
    private static final Map<String, String> COLOR_NAMES = new LinkedHashMap<>();

    static {
        addColors("Красный", "#FF6B6B", "#FF4757", "#FF6348", "#FF7F50", "#EE5A24");
        addColors("Оранжевый", "#FFA502", "#F9CA24", "#F6B93B", "#FFC312", "#F0932B");
        addColors("Жёлтый", "#F6E58D", "#FFD93D", "#F9E79F", "#FFD700", "#F4D03F");
        addColors("Зелёный", "#2ED573", "#26DE81", "#7BED9F", "#A3CB38", "#6AB04C",
                "#BADB57", "#82E0AA", "#58D68D", "#2ECC71", "#1ABC9C");
        addColors("Бирюзовый", "#00CEC9", "#0ABDE3", "#00B894", "#00A8FF", "#36D399");
        addColors("Синий", "#1E90FF", "#3742FA", "#4A69BD", "#6A89CC", "#48DBFB",
                "#686DE0", "#30336B", "#4834D4", "#0652DD", "#1289A7");
        addColors("Фиолетовый", "#A29BFE", "#6C5CE7", "#8E44AD", "#9B59B6", "#D980FA",
                "#B33771", "#6D214F", "#C39BD3", "#AF7AC5", "#7D3C98");
        addColors("Розовый", "#FD79A8", "#E84393", "#F8A5C2", "#F19066", "#E15F41",
                "#C44569", "#FDA7DF", "#F5B7B1");
        addColors("Серый", "#85929E", "#95A5A6", "#7F8C8D", "#BDC3C7", "#A6ACAF",
                "#D5D8DC", "#ABB2B9");
        addColors("Яркий", "#FD7272", "#55E6C1", "#58B19F", "#D6A2E8", "#F97F51");
    }

    private static void addColors(String name, String... hexes) {
        for (String hex : hexes) {
            COLOR_NAMES.put(hex.toUpperCase(), name);
        }
    }

    /**
     * Возвращает русское название цвета по hex-коду.
     * Если цвет не из палитры или отсутствует — возвращает null.
     */
    public String getColorName(String hex) {
        if (hex == null || hex.isBlank()) {
            return null;
        }
        return COLOR_NAMES.get(hex.trim().toUpperCase());
    }
}