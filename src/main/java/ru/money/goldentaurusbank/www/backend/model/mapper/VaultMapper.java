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
    @Mapping(target = "categoriesCount", expression = "java(vault.getBullions().size())")
    VaultResponse toResponse(Vault vault);


    @AfterMapping
    default void enrichVaultResponse (@MappingTarget VaultResponse response, Vault vault) {
        if (vault != null) {
            // Проверяем, срочное ли хранилище
            boolean isTerm = vault.getAccountType() != null &&
                    TERM.name().equals(vault.getAccountType().name());
            // Если срочное и дата закрытия ещё не наступила - блокируем операции
            if (isTerm && vault.getCloseDate() != null) {
                LocalDate today = LocalDate.now();
                LocalDate closeDate = vault.getCloseDate();
                boolean isClosed = today.isEqual(closeDate) || today.isAfter(closeDate);

                // Если ещё не закрыто
                if (!isClosed) {
                    response.setAllowedIncome(false);
                    response.setAllowedExpense(false);
                    response.setAllowedChangeAmount(false);
                    response.setAllowedEdit(true);
                    response.setAllowedDelete(true);
                    response.setAllowedTransfer(false);
                    return;
                }
            }
            response.setAllowedIncome(true);
            response.setAllowedExpense(true);
            response.setAllowedChangeAmount(true);
            response.setAllowedEdit(true);
            response.setAllowedDelete(true);
            response.setAllowedTransfer(true);
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