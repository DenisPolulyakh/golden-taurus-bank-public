package ru.money.goldentaurusbank.www.backend.model.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import ru.money.goldentaurusbank.www.backend.model.domain.Bullion;
import ru.money.goldentaurusbank.www.backend.model.domain.Vault;
import ru.money.goldentaurusbank.www.backend.model.dto.response.BullionResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.VaultResponse;

import java.time.LocalDate;
import java.util.List;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.AccountType.TERM;

@Mapper(componentModel = "spring")
public interface BullionMapper {

    BullionMapper INSTANCE = Mappers.getMapper(BullionMapper.class);


    @Mapping(source = "bullionName.id", target = "bullionName.id")
    @Mapping(source = "bullionName.title", target = "bullionName.title")
    @Mapping(source = "vault.id", target = "vault.id")
    @Mapping(source = "vault.name", target = "vault.name")
    @Mapping(source = "vault.interestRate", target = "vault.interestRate")
    BullionResponse toResponse(Bullion bullion);

    List<BullionResponse> toResponseList(List<Bullion> bullions);


    @AfterMapping
    default void enrichVaultInfo (@MappingTarget BullionResponse.VaultInfo response) {
        if (response != null) {
            // Проверяем, срочное ли хранилище
            boolean isTerm = response.getAccountType() != null &&
                    TERM.name().equals(response.getAccountType());
            // Если срочное и дата закрытия ещё не наступила - блокируем операции
            if (isTerm && response.getCloseDate() != null) {
                LocalDate today = LocalDate.now();
                LocalDate closeDate = response.getCloseDate();
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

}