package ru.money.goldentaurusbank.www.backend.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ErrorResponse extends TaurusResponse {
    
    private Map<String, String> details;
    
    public ErrorResponse(int code, String message) {
        super(code, message);
        this.details = new HashMap<>();
    }
    
    public ErrorResponse(int code, String message, Map<String, String> details) {
        super(code, message);
        this.details = details != null ? details : new HashMap<>();
    }
    
    public void addDetail(String key, String value) {
        if (details == null) {
            details = new HashMap<>();
        }
        details.put(key, value);
    }
}