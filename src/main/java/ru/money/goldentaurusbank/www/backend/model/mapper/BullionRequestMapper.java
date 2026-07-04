package ru.money.goldentaurusbank.www.backend.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import ru.money.goldentaurusbank.www.backend.model.dto.request.BullionRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.RefillBullionRequest;

@Mapper(componentModel = "spring")
public interface BullionRequestMapper {

    BullionRequestMapper INSTANCE = Mappers.getMapper(BullionRequestMapper.class);

    @Mapping(source = "userComment", target = "userComment")
    RefillBullionRequest toRefillBullionRequest(BullionRequest bullionRequest, String userComment);

}