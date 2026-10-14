package dev.nerviz.bankapp.infrastructure.rest.idempotent;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Canonical JSON serialization of the whole request DTO, not one hand-picked field. */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class IdempotencyRequestHasher {

    private static final String SHA_256 = "SHA-256";

    ObjectMapper objectMapper;

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
