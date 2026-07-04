package ru.money.goldentaurusbank.www.backend.infrastructure.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.response.SuccessResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.UserResponse;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.USER_NOT_FOUND;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {


        String email = userDetails.getUsername();


        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(USER_NOT_FOUND.getCode(),USER_NOT_FOUND.getMessage()));

        UserResponse userResponse = UserResponse.fromUser(user);
        return ResponseEntity.ok(new SuccessResponse<>(userResponse));
    }
}