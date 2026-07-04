package ru.money.goldentaurusbank.www.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankDetailResponse {
    private Long bankId;
    private String bankName;
    private BigDecimal totalAmount;
    private Integer vaultsCount;
    private PageResponse<VaultResponse> vaults;
}