package ru.money.goldentaurusbank.www.backend.model.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SuccessResponse<T> extends TaurusResponse {
    
    private T data;
    
    public SuccessResponse(int code, String message, T data) {
        super(code, message);
        this.data = data;
    }
    public SuccessResponse(int code, String message) {
        this(code, message,null);
    }

    
    public SuccessResponse(String message, T data) {
        this(0, message, data);
    }
    
    public SuccessResponse(String message) {
        this(0, message, null);
    }
    
    public SuccessResponse(T data) {
        this(0, "Успешно", data);
    }
}