package ru.money.goldentaurusbank.www.backend.infrastructure.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.money.goldentaurusbank.www.backend.model.dto.enums.ResponseCodes;
import ru.money.goldentaurusbank.www.backend.model.dto.response.ErrorResponse;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledException(DisabledException e) {
        log.warn("DisabledException: {}", e.getMessage());
        return ResponseEntity
                .status(ResponseCodes.NO_ACTIVE_ACCOUNT.getHttpStatus())
                .body(new ErrorResponse(
                        ResponseCodes.NO_ACTIVE_ACCOUNT.getCode(),
                        ResponseCodes.NO_ACTIVE_ACCOUNT.getMessage()
                ));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException e) {
        log.warn("BadCredentialsException: {}", e.getMessage());
        return ResponseEntity
                .status(ResponseCodes.WRONG_CREDENTIALS.getHttpStatus())
                .body(new ErrorResponse(
                        ResponseCodes.WRONG_CREDENTIALS.getCode(),
                        ResponseCodes.WRONG_CREDENTIALS.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        
        return ResponseEntity
                .status(ResponseCodes.VALIDATION_ERROR.getHttpStatus())
                .body(new ErrorResponse(
                        ResponseCodes.VALIDATION_ERROR.getCode(),
                        ResponseCodes.VALIDATION_ERROR.getMessage(),
                        errors
                ));
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplicationException(ApplicationException e) {
        log.warn("ApplicationException: {}", e.getMessage());
        return ResponseEntity
                .status(ResponseCodes.getByCode(e.getCode()).getHttpStatus())
                .body(new ErrorResponse(
                        e.getCode(),
                        e.getMessage()
                ));
    }
}