package ru.money.goldentaurusbank.www.backend.model.mapper;

import org.mapstruct.*;
import org.mapstruct.factory.Mappers;
import ru.money.goldentaurusbank.www.backend.model.domain.Bullion;
import ru.money.goldentaurusbank.www.backend.model.domain.Vault;
import ru.money.goldentaurusbank.www.backend.model.dto.request.VaultRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.BullionResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.VaultResponse;

import java.util.List;

@Mapper(componentModel = "spring", uses = {BullionMapper.class})
public interface VaultMapper {

    VaultMapper INSTANCE = Mappers.getMapper(VaultMapper.class);

    @Mapping(target = "bankId", source = "bank.id")
    @Mapping(target = "bankName", source = "bank.name")
    @Mapping(target = "totalAmount", expression = "java(vault.getBullions().stream().map(b -> b.getAmount()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))")
    @Mapping(target = "bullionNamesCount", expression = "java(vault.getBullions().size())")
    VaultResponse toResponse(Vault vault);


    // Разрешённость операций задают только галочки хранилища: верхние allowed*
    // MapStruct переносит из сущности сам, здесь остаются сырые значения для формы правки
    @AfterMapping
    default void enrichVaultResponse (@MappingTarget VaultResponse response, Vault vault) {
        if (vault != null) {
            response.setSettings(VaultResponse.Settings.builder()
                    .allowedIncome(vault.isAllowedIncome())
                    .allowedExpense(vault.isAllowedExpense())
                    .allowedTransfer(vault.isAllowedTransfer())
                    .build());
            response.setAllowedTransferOut(vault.isAllowedTransfer() && vault.isAllowedExpense());
            response.setAllowedTransferIn(vault.isAllowedTransfer() && vault.isAllowedIncome());
        }

    }


    @Mapping(source = "vaultType", target = "vaultType")
    @Mapping(source = "bullions", target = "bullions")
    List<VaultResponse> toResponseList(List<Vault> vaults);

    // Маппинг из VaultRequest в Vault
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "bullions", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Vault toEntity(VaultRequest request);

    // Обновление существующей сущности
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "bullions", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(VaultRequest request, @MappingTarget Vault vault);
}