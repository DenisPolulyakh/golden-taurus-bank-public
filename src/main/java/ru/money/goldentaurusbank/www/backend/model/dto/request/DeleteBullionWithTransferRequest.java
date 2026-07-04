package ru.money.goldentaurusbank.www.backend.model.dto.request;

import lombok.Data;

@Data
public class DeleteBullionWithTransferRequest {
    private Long toVaultId;
    private boolean toLiquidityVault;
    private String description;
}