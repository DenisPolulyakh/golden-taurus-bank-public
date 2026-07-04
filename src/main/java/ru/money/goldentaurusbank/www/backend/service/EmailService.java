package ru.money.goldentaurusbank.www.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendVerificationEmail(String to, String token) {
        String verificationUrl = frontendUrl + "/verify?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Подтверждение регистрации в GoldenTaurusBank");
        message.setText(
                "Здравствуйте!\n\n" +
                "Вы зарегистрировались в Твой Личный банк — вашем домашнем финансовом помощнике.\n\n" +
                "Для подтверждения email перейдите по ссылке:\n" +
                verificationUrl + "\n\n" +
                "Ссылка действительна 24 часа.\n\n" +
                "Если вы не регистрировались, просто проигнорируйте это письмо.\n\n" +
                "С уважением,\n" +
                "Команда Твой Личный Банк"
        );

        try {
            mailSender.send(message);
            log.info("Verification email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send verification email to: {}", to, e);
            throw new RuntimeException("Не удалось отправить письмо подтверждения", e);
        }
    }

    public void sendPasswordResetEmail(String to, String token) {
        String resetUrl = frontendUrl + "/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Сброс пароля в GoldenTaurusBank");
        message.setText(
                "Здравствуйте!\n\n" +
                "Вы запросили сброс пароля в Твой Личный Банк.\n\n" +
                "Для установки нового пароля перейдите по ссылке:\n" +
                resetUrl + "\n\n" +
                "Ссылка действительна 1 час.\n\n" +
                "Если вы не запрашивали сброс пароля, проигнорируйте это письмо.\n\n" +
                "С уважением,\n" +
                "Команда Твой Личный Банк"
        );

        try {
            mailSender.send(message);
            log.info("Password reset email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", to, e);
            throw new RuntimeException("Не удалось отправить письмо для сброса пароля", e);
        }
    }
}