package ru.money.goldentaurusbank.www.backend.infrastructure.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.BullionName;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.request.*;
import ru.money.goldentaurusbank.www.backend.model.dto.response.BullionResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.GroupedBullionResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.SuccessResponse;
import ru.money.goldentaurusbank.www.backend.repository.BullionNameRepository;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;
import ru.money.goldentaurusbank.www.backend.service.BullionService;

import java.math.BigDecimal;
import java.util.List;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.*;

@RestController
@RequestMapping("/api/bullions")
@RequiredArgsConstructor
public class BullionController {

    private final BullionService bullionService;
    private final UserRepository userRepository;
    private final BullionNameRepository bullionNameRepository;

    @PostMapping
    public ResponseEntity<SuccessResponse<BullionResponse>> createBullion(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BullionRequest request) {

        User user = getUserFromUserDetails(userDetails);
        BullionResponse response = bullionService.createBullion(user, request);

        return ResponseEntity.ok(new SuccessResponse<>(
                SUCCESS.getCode(),
                "Слиток успешно добавлен",
                response
        ));
    }


    @PostMapping("/refill")
    public ResponseEntity<SuccessResponse<BullionResponse>> refillBullion(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody RefillBullionRequest request) {

        User user = getUserFromUserDetails(userDetails);
        BullionResponse response = bullionService.refillBullion(user, request);

        return ResponseEntity.ok(new SuccessResponse<>(
                SUCCESS.getCode(),
                "Слиток успешно пополнен",
                response
        ));
    }


    @PostMapping("/withdraw")
    public ResponseEntity<SuccessResponse<BullionResponse>> withDrawBullion(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody WithdrawBullionRequest request) {

        User user = getUserFromUserDetails(userDetails);
        BullionResponse response = bullionService.withDrawBullion(user, request);

        return ResponseEntity.ok(new SuccessResponse<>(
                SUCCESS.getCode(),
                "Cумма из слитка успешно списана",
                response
        ));
    }

    @PostMapping("/transfer")
    public ResponseEntity<SuccessResponse<Void>> transferAmount(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TransferRequest request) {

        User user = getUserFromUserDetails(userDetails);
        bullionService.transferAmountBullion(
                user, request
        );

        return ResponseEntity.ok(new SuccessResponse<>(
                0,
                "Перевод выполнен успешно",
                null
        ));
    }


    @GetMapping
    public ResponseEntity<SuccessResponse<List<BullionResponse>>> getAllBullions(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserFromUserDetails(userDetails);
        List<BullionResponse> bullions = bullionService.getAllBullions(user);

        return ResponseEntity.ok(new SuccessResponse<>(bullions));
    }

    @GetMapping("/{bullionId}")
    public ResponseEntity<SuccessResponse<BullionResponse>> getBullionById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long bullionId) {

        User user = getUserFromUserDetails(userDetails);
        BullionResponse bullion = bullionService.getBullionById(user, bullionId);

        return ResponseEntity.ok(new SuccessResponse<>(bullion));
    }

    @PutMapping("/{bullionId}")
    public ResponseEntity<SuccessResponse<BullionResponse>> updateBullion(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long bullionId,
            @Valid @RequestBody BullionRequest request) {

        User user = getUserFromUserDetails(userDetails);
        BullionResponse response = bullionService.updateBullion(user, bullionId, request);

        return ResponseEntity.ok(new SuccessResponse<>(
                SUCCESS.getCode(),
                "Слиток успешно обновлён",
                response
        ));
    }

    @DeleteMapping("/{bullionId}")
    public ResponseEntity<SuccessResponse<Void>> deleteBullion(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long bullionId) {

        User user = getUserFromUserDetails(userDetails);
        bullionService.deleteBullion(user, bullionId);

        return ResponseEntity.ok(new SuccessResponse<>("Слиток успешно удалён"));
    }

    @GetMapping("/total")
    public ResponseEntity<SuccessResponse<BigDecimal>> getTotalAmount(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserFromUserDetails(userDetails);
        BigDecimal total = bullionService.getTotalAmount(user);

        return ResponseEntity.ok(new SuccessResponse<>(total));
    }

    @GetMapping("/bullion-name/{bullionNameId}")
    public ResponseEntity<SuccessResponse<BigDecimal>> getTotalAmountByBullionName(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long bullionNameId) {

        User user = getUserFromUserDetails(userDetails);

        BullionName bullionName = bullionNameRepository.findByIdAndUser(bullionNameId, user)
                .orElseThrow(() -> new ApplicationException(
                        BULLION_NAME_NOT_FOUND.getCode(),
                        BULLION_NAME_NOT_FOUND.getMessage()
                ));

        BigDecimal total = bullionService.getTotalAmountByBullionName(user, bullionName);

        return ResponseEntity.ok(new SuccessResponse<>(total));
    }


    @DeleteMapping("/{bullionId}/transfer")
    public ResponseEntity<SuccessResponse<Void>> deleteBullionWithTransfer(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long bullionId,
            @Valid @RequestBody DeleteBullionWithTransferRequest request) {

        User user = getUserFromUserDetails(userDetails);
        bullionService.deleteBullionWithTransfer(user, bullionId, request);

        return ResponseEntity.ok(new SuccessResponse<>(
                SUCCESS.getCode(),
                "Слиток успешно удалён, средства перенесены",
                null
        ));
    }


    @GetMapping("/grouped")
    public ResponseEntity<SuccessResponse<GroupedBullionResponse>> getGroupedBullions(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserFromUserDetails(userDetails);
        GroupedBullionResponse grouped = bullionService.getGroupedBullions(user);

        return ResponseEntity.ok(new SuccessResponse<>(grouped));
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