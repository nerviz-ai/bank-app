package dev.nerviz.bankapp.infrastructure.rest.idempotent;

import dev.nerviz.bankapp.application.port.StoredResponse;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/** Turns a {@code ResponseEntity} into a {@link StoredResponse} and back, on replay. */
@Component
class IdempotencyResponseCodec {

    private final ObjectMapper objectMapper;

    IdempotencyResponseCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    StoredResponse encode(ResponseEntity<?> response) {
        return new StoredResponse(
                response.getStatusCode().value(),
                objectMapper.writeValueAsString(response.getBody()),
                encodeHeaders(response.getHeaders()));
    }

    ResponseEntity<Object> decode(StoredResponse stored, Type bodyType) {
        JavaType javaType = objectMapper.getTypeFactory().constructType(bodyType);
        Object body = objectMapper.readValue(stored.body(), javaType);
        return ResponseEntity.status(stored.status())
                .headers(decodeHeaders(stored.headers()))
                .body(body);
    }

    private String encodeHeaders(HttpHeaders headers) {
        Map<String, List<String>> plain = new LinkedHashMap<>();
        headers.forEach(plain::put);
        return objectMapper.writeValueAsString(plain);
    }

    private HttpHeaders decodeHeaders(String json) {
        JavaType listOfString = objectMapper.getTypeFactory().constructCollectionType(List.class, String.class);
        JavaType mapType = objectMapper
                .getTypeFactory()
                .constructMapType(
                        LinkedHashMap.class, objectMapper.getTypeFactory().constructType(String.class), listOfString);
        Map<String, List<String>> raw = objectMapper.readValue(json, mapType);
        HttpHeaders headers = new HttpHeaders();
        raw.forEach(headers::addAll);
        return headers;
    }
}
