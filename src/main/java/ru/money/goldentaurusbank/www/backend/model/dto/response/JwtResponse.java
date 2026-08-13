package ru.money.goldentaurusbank.www.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtResponse {
    private String token;
    // Оба вызова билдера в AuthService выставляют type явно, но без
    // @Builder.Default объявленное значение всё равно не работало бы
    @Builder.Default
    private String type = "Bearer";
    private Long id;
    private String email;
    private String fullName;
    private String role;
    private boolean emailVerified;
    
    public JwtResponse(String token, Long id, String email, String fullName, String role, boolean emailVerified) {
        this.token = token;
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.emailVerified = emailVerified;
    }
}