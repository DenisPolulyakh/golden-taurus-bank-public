package ru.money.goldentaurusbank.www.backend.model.mapper;

import org.mapstruct.*;
import org.mapstruct.factory.Mappers;
import ru.money.goldentaurusbank.www.backend.model.domain.Bullion;
import ru.money.goldentaurusbank.www.backend.model.domain.Vault;
import ru.money.goldentaurusbank.www.backend.model.dto.request.VaultRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.BullionResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.VaultResponse;

import java.time.LocalDate;
import java.util.List;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.AccountType.TERM;

@Mapper(componentModel = "spring", uses = {BullionMapper.class})
public interface VaultMapper {

    VaultMapper INSTANCE = Mappers.getMapper(VaultMapper.class);

    @Mapping(target = "bankId", source = "bank.id")
    @Mapping(target = "bankName", source = "bank.name")
    @Mapping(target = "totalAmount", expression = "java(vault.getBullions().stream().map(b -> b.getAmount()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))")
    @Mapping(target = "bullionNamesCount", expression = "java(vault.getBullions().size())")
    VaultResponse toResponse(Vault vault);


    @AfterMapping
    default void enrichVaultResponse (@MappingTarget VaultResponse response, Vault vault) {
        if (vault != null) {
            response.setSettings(VaultResponse.Settings.builder()
                    .allowedIncome(vault.isAllowedIncome())
                    .allowedExpense(vault.isAllowedExpense())
                    .allowedTransfer(vault.isAllowedTransfer())
                    .build());

            // Срочный вклад до даты закрытия операций не допускает
            boolean isTerm = vault.getAccountType() != null &&
                    TERM.name().equals(vault.getAccountType().name());
            boolean open = !isTerm
                    || vault.getCloseDate() == null
                    || !LocalDate.now().isBefore(vault.getCloseDate());

            // Итоговый флаг = галочка И правило срочного вклада
            response.setAllowedIncome(open && vault.isAllowedIncome());
            response.setAllowedExpense(open && vault.isAllowedExpense());
            response.setAllowedTransfer(open && vault.isAllowedTransfer());

            response.setAllowedChangeAmount(open);
            response.setAllowedEdit(true);
            response.setAllowedDelete(true);
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