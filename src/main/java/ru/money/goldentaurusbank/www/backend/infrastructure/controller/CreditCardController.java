package ru.money.goldentaurusbank.www.backend.infrastructure.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.CreditCard;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.request.CreditCardOperationRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.CreditCardRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.RepayFromBullionRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CreditCardHistoryResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CreditCardListResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.CreditCardResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.SuccessResponse;
import ru.money.goldentaurusbank.www.backend.model.mapper.CreditCardMapper;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;
import ru.money.goldentaurusbank.www.backend.service.CreditCardService;
import ru.money.goldentaurusbank.www.backend.service.CreditCardTransactionService;

import java.util.List;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.USER_NOT_FOUND;

@RestController
@RequestMapping("/api/credit-cards")
@RequiredArgsConstructor
public class CreditCardController {

    private final CreditCardService creditCardService;
    private final CreditCardTransactionService creditCardTransactionService;
    private final CreditCardMapper creditCardMapper;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<SuccessResponse<CreditCardResponse>> createCard(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreditCardRequest request) {

        User user = getUserFromUserDetails(userDetails);
        CreditCardResponse response = creditCardService.createCard(user, request);

        return ResponseEntity.ok(new SuccessResponse<>(0, "Кредитная карта добавлена", response));
    }

    /** Все карты одной страницей: пагинации нет, поиск и сортировка — на бэке. */
    @GetMapping
    public ResponseEntity<SuccessResponse<CreditCardListResponse>> getAllCards(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {

        User user = getUserFromUserDetails(userDetails);
        return ResponseEntity.ok(new SuccessResponse<>(
                creditCardService.getAllCards(user, search, sortBy, sortOrder)));
    }

    @GetMapping("/available-bullions")
    public ResponseEntity<SuccessResponse<List<CreditCardResponse.AccumulatorInfo>>> getAvailableBullions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Long cardId) {

        User user = getUserFromUserDetails(userDetails);
        return ResponseEntity.ok(new SuccessResponse<>(creditCardService.getAvailableBullions(user, cardId)));
    }

    @GetMapping("/{cardId}")
    public ResponseEntity<SuccessResponse<CreditCardResponse>> getCard(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long cardId) {

        User user = getUserFromUserDetails(userDetails);
        return ResponseEntity.ok(new SuccessResponse<>(creditCardService.getCard(user, cardId)));
    }

    @PutMapping("/{cardId}")
    public ResponseEntity<SuccessResponse<CreditCardResponse>> updateCard(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long cardId,
            @Valid @RequestBody CreditCardRequest request) {

        User user = getUserFromUserDetails(userDetails);
        CreditCardResponse response = creditCardService.updateCard(user, cardId, request);

        return ResponseEntity.ok(new SuccessResponse<>(0, "Кредитная карта обновлена", response));
    }

    /** Архивирует: на карту ссылается её история, физически удалить нельзя. */
    @DeleteMapping("/{cardId}")
    public ResponseEntity<SuccessResponse<Void>> deleteCard(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long cardId) {

        User user = getUserFromUserDetails(userDetails);
        creditCardService.deleteCard(user, cardId);

        return ResponseEntity.ok(new SuccessResponse<>("Кредитная карта удалена"));
    }

    @PostMapping("/{cardId}/spend")
    public ResponseEntity<SuccessResponse<CreditCardResponse>> spend(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long cardId,
            @Valid @RequestBody CreditCardOperationRequest request) {

        User user = getUserFromUserDetails(userDetails);
        CreditCard card = creditCardTransactionService.spend(cardId, request.getAmount(), user,
                request.getComment(), request.getDateOperation());

        return ResponseEntity.ok(new SuccessResponse<>(0, "Списание проведено", creditCardMapper.toResponse(card)));
    }

    @PostMapping("/{cardId}/repay")
    public ResponseEntity<SuccessResponse<CreditCardResponse>> repay(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long cardId,
            @Valid @RequestBody CreditCardOperationRequest request) {

        User user = getUserFromUserDetails(userDetails);
        CreditCard card = creditCardTransactionService.repay(cardId, request.getAmount(), user,
                request.getComment(), request.getDateOperation());

        return ResponseEntity.ok(new SuccessResponse<>(0, "Задолженность погашена", creditCardMapper.toResponse(card)));
    }

    /** Погашение из накопителя: списание со слитка и погашение долга одной операцией. */
    @PostMapping("/repay-from-bullion")
    public ResponseEntity<SuccessResponse<CreditCardResponse>> repayFromBullion(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody RepayFromBullionRequest request) {

        User user = getUserFromUserDetails(userDetails);
        CreditCard card = creditCardTransactionService.repayFromBullion(request, user);

        return ResponseEntity.ok(new SuccessResponse<>(0, "Задолженность погашена из накопителя",
                creditCardMapper.toResponse(card)));
    }

    @GetMapping("/{cardId}/history")
    public ResponseEntity<SuccessResponse<List<CreditCardHistoryResponse>>> getHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long cardId) {

        User user = getUserFromUserDetails(userDetails);
        return ResponseEntity.ok(new SuccessResponse<>(creditCardTransactionService.getHistory(cardId, user)));
    }

    @PostMapping("/history/{historyId}/rollback")
    public ResponseEntity<SuccessResponse<Void>> rollback(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long historyId) {

        User user = getUserFromUserDetails(userDetails);
        creditCardTransactionService.rollback(historyId, user);

        return ResponseEntity.ok(new SuccessResponse<>("Операция откачена"));
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
