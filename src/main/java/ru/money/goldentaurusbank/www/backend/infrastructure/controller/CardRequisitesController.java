package ru.money.goldentaurusbank.www.backend.infrastructure.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.request.CardRequisitesRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CardRequisitesResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.EmergencyPackageResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.SuccessResponse;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;
import ru.money.goldentaurusbank.www.backend.service.CardRequisitesService;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.USER_NOT_FOUND;

@RestController
@RequestMapping("/api/card-requisites")
@RequiredArgsConstructor
public class CardRequisitesController {

    private final CardRequisitesService cardRequisitesService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<SuccessResponse<CardRequisitesResponse>> get(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserFromUserDetails(userDetails);
        return ResponseEntity.ok(new SuccessResponse<>(cardRequisitesService.get(user)));
    }

    @PutMapping
    public ResponseEntity<SuccessResponse<CardRequisitesResponse>> save(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CardRequisitesRequest request) {

        User user = getUserFromUserDetails(userDetails);
        return ResponseEntity.ok(new SuccessResponse<>(
                "Реквизиты сохранены", cardRequisitesService.save(user, request)));
    }

    @GetMapping("/emergency-package")
    public ResponseEntity<SuccessResponse<EmergencyPackageResponse>> emergencyPackage(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserFromUserDetails(userDetails);
        return ResponseEntity.ok(new SuccessResponse<>(cardRequisitesService.buildEmergencyPackage(user)));
    }

    private User getUserFromUserDetails(UserDetails userDetails) {
        String email = userDetails.getUsername();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(
                        USER_NOT_FOUND.getCode(),
                        USER_NOT_FOUND.getMessage()
                ));
    }
}
