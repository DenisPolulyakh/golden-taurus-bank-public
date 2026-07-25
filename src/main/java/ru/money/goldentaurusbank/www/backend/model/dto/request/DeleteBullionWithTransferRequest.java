package ru.money.goldentaurusbank.www.backend.model.dto.request;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DeleteBullionWithTransferRequest {
    private Long toVaultId;
    private boolean toLiquidityVault;
    private String description;
    private LocalDate dateOperation;
}