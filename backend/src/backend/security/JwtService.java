package backend.security;

import backend.config.AppConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Small HS256 JWT implementation used by the built-in Java server.
 */
public final class JwtService {
    private final byte[] secret;
    private final long expirationMillis;
    private final ObjectMapper mapper;

    public JwtService(AppConfig config, ObjectMapper mapper) {
        this.secret = config.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        this.expirationMillis = config.getJwtExpirationMillis();
        this.mapper = mapper;
    }

    public String generate(String username, String role) throws Exception {
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        long now = System.currentTimeMillis() / 1000;
        long expiration = now + (expirationMillis / 1000);

        String payload = mapper.createObjectNode()
            .put("sub", username)
            .put("role", role)
            .put("iat", now)
            .put("exp", expiration)
            .toString();

        String encodedHeader = encode(header.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = encode(payload.getBytes(StandardCharsets.UTF_8));
        String content = encodedHeader + "." + encodedPayload;
        String signature = sign(content);
        return content + "." + signature;
    }

    public String validateAndGetUsername(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String content = parts[0] + "." + parts[1];
            byte[] expected = sign(content).getBytes(StandardCharsets.US_ASCII);
            byte[] actual = parts[2].getBytes(StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(expected, actual)) {
                return null;
            }

            String payloadJson = new String(decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode payload = mapper.readTree(payloadJson);
            if (!payload.hasNonNull("sub") || !payload.hasNonNull("exp")) {
                return null;
            }
            if (System.currentTimeMillis() / 1000 > payload.get("exp").asLong()) {
                return null;
            }
            return payload.get("sub").asText();
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return encode(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    private String encode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private byte[] decode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }
}
