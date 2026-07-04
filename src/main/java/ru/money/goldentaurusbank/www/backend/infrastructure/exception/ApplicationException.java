package ru.money.goldentaurusbank.www.backend.infrastructure.exception;

import lombok.Getter;

@Getter
public class ApplicationException extends RuntimeException {
    private int code;
    private String message;

    public ApplicationException(int code, final String message) {
        super(message);
        this.code = code;
        this.message = message;
    }


}
