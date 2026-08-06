package ru.money.goldentaurusbank.www.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.money.goldentaurusbank.www.backend.model.domain.BullionName;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BullionNameResponse {
    private Long id;
    private String title;
    private String color;

    public static BullionNameResponse fromBullionName(BullionName bullionName) {
        return BullionNameResponse.builder()
                .id(bullionName.getId())
                .title(bullionName.getTitle())
                .color(bullionName.getColor())
                .build();
    }
}
