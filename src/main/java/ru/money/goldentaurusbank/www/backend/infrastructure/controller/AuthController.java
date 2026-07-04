package ru.money.goldentaurusbank.www.backend.infrastructure.controller;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.money.goldentaurusbank.www.backend.model.dto.request.LoginRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.RegisterRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.JwtResponse;
import ru.money.goldentaurusbank.www.backend.model.dto.response.SuccessResponse;
import ru.money.goldentaurusbank.www.backend.security.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<SuccessResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(new SuccessResponse<>(
                0,
                "Пользователь успешно зарегистрирован. Проверьте почту для подтверждения.",
                null
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<SuccessResponse<JwtResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        JwtResponse jwtResponse = authService.login(request, response);
        return ResponseEntity.ok(new SuccessResponse<>(jwtResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<SuccessResponse<JwtResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        JwtResponse jwtResponse = authService.refreshToken(request, response);
        return ResponseEntity.ok(new SuccessResponse<>(jwtResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<SuccessResponse<Void>> logout(HttpServletResponse response) {
        authService.logout(response);
        return ResponseEntity.ok(new SuccessResponse<>("Выход выполнен успешно"));
    }

    @GetMapping("/verify")
    public ResponseEntity<SuccessResponse<Void>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(new SuccessResponse<>("Email успешно подтверждён!"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<SuccessResponse<Void>> resendVerification(@RequestParam String email) {
        authService.resendVerificationEmail(email);
        return ResponseEntity.ok(new SuccessResponse<>("Письмо с подтверждением отправлено повторно"));
    }
}