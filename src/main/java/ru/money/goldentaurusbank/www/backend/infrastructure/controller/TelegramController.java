package ru.money.goldentaurusbank.www.backend.infrastructure.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.response.SuccessResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.TelegramLinkCodeResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.TelegramLinkResponse;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;
import ru.money.goldentaurusbank.www.backend.service.telegram.TelegramLinkService;

import java.util.List;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.USER_NOT_FOUND;

@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
public class TelegramController {

    private final TelegramLinkService telegramLinkService;
    private final UserRepository userRepository;

    @PostMapping("/link-code")
    public ResponseEntity<SuccessResponse<TelegramLinkCodeResponse>> createCode(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserFromUserDetails(userDetails);
        return ResponseEntity.ok(new SuccessResponse<>(
                "Код создан", telegramLinkService.createCode(user)));
    }

    @GetMapping("/links")
    public ResponseEntity<SuccessResponse<List<TelegramLinkResponse>>> links(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = getUserFromUserDetails(userDetails);
        return ResponseEntity.ok(new SuccessResponse<>(telegramLinkService.links(user)));
    }

    @DeleteMapping("/links/{linkId}")
    public ResponseEntity<SuccessResponse<Void>> removeLink(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long linkId) {

        User user = getUserFromUserDetails(userDetails);
        telegramLinkService.removeLink(user, linkId);

        return ResponseEntity.ok(new SuccessResponse<>("Чат отвязан"));
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
