package dev.nerviz.bankapp.infrastructure.rest.idempotent;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Canonical JSON serialization of the whole request DTO, not one hand-picked field. */
@Component
class IdempotencyRequestHasher {

    private static final String SHA_256 = "SHA-256";

    private final ObjectMapper objectMapper;

    IdempotencyRequestHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String hash(Object requestBody) {
        byte[] json = objectMapper.writeValueAsBytes(requestBody);
        try {
            byte[] digest = MessageDigest.getInstance(SHA_256).digest(json);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(SHA_256 + " unavailable", e);
        }
    }
}
