package ru.money.goldentaurusbank.www.backend.model.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import ru.money.goldentaurusbank.www.backend.model.domain.Bullion;
import ru.money.goldentaurusbank.www.backend.model.domain.Vault;
import ru.money.goldentaurusbank.www.backend.model.dto.response.BullionResponse;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BullionMapper {

    BullionMapper INSTANCE = Mappers.getMapper(BullionMapper.class);


    // allowedIncome/Expense/Transfer MapStruct переносит из хранилища сам — это галочки,
    // других правил нет. У остальных флагов источника нет, поэтому константы:
    // без них allowedChangeAmount (примитив) стал бы false и правка суммы отвалилась
    @Mapping(source = "bullionName.id", target = "bullionName.id")
    @Mapping(source = "bullionName.title", target = "bullionName.title")
    @Mapping(source = "vault.id", target = "vault.id")
    @Mapping(source = "vault.name", target = "vault.name")
    @Mapping(source = "vault.interestRate", target = "vault.interestRate")
    @Mapping(target = "vault.allowedEdit", constant = "true")
    @Mapping(target = "vault.allowedDelete", constant = "true")
    @Mapping(target = "vault.allowedChangeAmount", constant = "true")
    BullionResponse toResponse(Bullion bullion);

    List<BullionResponse> toResponseList(List<Bullion> bullions);

    // Производные флаги перевода: перевести из хранилища = снять оттуда,
    // перевести в него = внести туда. Через expression не получается —
    // вложенный vault MapStruct собирает отдельным методом, слитка там уже нет.
    @AfterMapping
    default void enrichTransferFlags(@MappingTarget BullionResponse response, Bullion bullion) {
        if (response.getVault() == null || bullion.getVault() == null) {
            return;
        }
        Vault vault = bullion.getVault();
        response.getVault().setAllowedTransferOut(vault.isAllowedTransfer() && vault.isAllowedExpense());
        response.getVault().setAllowedTransferIn(vault.isAllowedTransfer() && vault.isAllowedIncome());
    }

}
