package ru.money.goldentaurusbank.www.backend.security.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.money.goldentaurusbank.www.backend.infrastructure.exception.ApplicationException;
import ru.money.goldentaurusbank.www.backend.model.domain.User;
import ru.money.goldentaurusbank.www.backend.model.dto.request.LoginRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.request.RegisterRequest;
import ru.money.goldentaurusbank.www.backend.model.dto.response.JwtResponse;
import ru.money.goldentaurusbank.www.backend.repository.UserRepository;
import ru.money.goldentaurusbank.www.backend.security.JwtTokenProvider;
import ru.money.goldentaurusbank.www.backend.service.EmailService;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApplicationException(EMAIL_ALREADY_EXISTS.getCode(), EMAIL_ALREADY_EXISTS.getMessage());
        }

        String verificationToken = UUID.randomUUID().toString();

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .emailVerified(false)
                .verificationToken(verificationToken)
                .role(User.Role.USER)
                .isActive(true)
                .build();

        userRepository.save(user);

        // Отправляем письмо с подтверждением
        emailService.sendVerificationEmail(user.getEmail(), verificationToken);
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new ApplicationException(INVALID_VERIFICATION_TOKEN.getCode(), INVALID_VERIFICATION_TOKEN.getMessage()));

        // Идемпотентность: повторный переход по той же ссылке не считается ошибкой
        if (user.isEmailVerified()) {
            return;
        }

        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(USER_NOT_FOUND.getCode(), USER_NOT_FOUND.getMessage()));

        if (user.isEmailVerified()) {
            throw new ApplicationException(EMAIL_ALREADY_VERIFIED.getCode(), EMAIL_ALREADY_VERIFIED.getMessage());
        }

        String newToken = UUID.randomUUID().toString();
        user.setVerificationToken(newToken);
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), newToken);
    }

    public JwtResponse login(LoginRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);


        Cookie refreshTokenCookie = new Cookie("refresh_token", refreshToken);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(false);
        refreshTokenCookie.setPath("/api/auth");
        refreshTokenCookie.setMaxAge((int) (tokenProvider.getRefreshExpirationMs() / 1000));
        response.addCookie(refreshTokenCookie);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        return JwtResponse.builder()
                .token(accessToken)
                .type("Bearer")
                .id(userDetails.getId())
                .email(userDetails.getUsername())
                .fullName(userDetails.getFullName())
                .role(userDetails.getRole())
                .build();
    }


    public JwtResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshTokenFromCookies(request);

        if (refreshToken == null || !tokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("Невалидный refresh token");
        }

        String email = tokenProvider.getEmailFromToken(refreshToken);
        Long userId = tokenProvider.getUserIdFromToken(refreshToken);
        String role = tokenProvider.getRoleFromToken(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(USER_NOT_FOUND.getCode(), USER_NOT_FOUND.getMessage()));

        CustomUserDetails userDetails = CustomUserDetails.fromUser(user);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        String newAccessToken = tokenProvider.generateAccessToken(authentication);
        String newRefreshToken = tokenProvider.generateRefreshToken(email, userId, role);

        Cookie refreshTokenCookie = new Cookie("refresh_token", newRefreshToken);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(false);
        refreshTokenCookie.setPath("/api/auth");
        refreshTokenCookie.setMaxAge((int) (tokenProvider.getRefreshExpirationMs() / 1000));
        response.addCookie(refreshTokenCookie);

        return JwtResponse.builder()
                .token(newAccessToken)
                .type("Bearer")
                .id(userId)
                .email(email)
                .fullName(user.getFullName())
                .role(role)
                .build();
    }

    public void logout(HttpServletResponse response) {
        Cookie refreshTokenCookie = new Cookie("refresh_token", null);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(true);
        refreshTokenCookie.setPath("/api/auth");
        refreshTokenCookie.setMaxAge(0);
        response.addCookie(refreshTokenCookie);
    }

    private String extractRefreshTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> "refresh_token".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}