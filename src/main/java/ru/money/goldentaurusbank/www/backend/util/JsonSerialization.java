package ru.money.goldentaurusbank.www.backend.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@NoArgsConstructor
public class JsonSerialization {
    private static final Logger log = LoggerFactory.getLogger(JsonSerialization.class);
    private static final ObjectMapper mapper = createObjectMapper();
    static {
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }




    public static String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            log.error("Error during object {} serialization to json", o);
            throw new RuntimeException(e);
        }
    }

    public static <T> Optional<T> optionalFromJson(String value, Class<T> clazz) {
        try {
            return Optional.of(mapper.readValue(value, clazz));
        } catch (Exception e) {
            log.error("Error during deserialization object of class {} and value {}", clazz.getSimpleName(), value);
            return Optional.empty();
        }
    }

}

