package com.yingshi.server.common.cursor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * R2-E-4: Unified opaque cursor token with HMAC-SHA256 signature.
 * Prevents client-side tampering of pagination cursors.
 */
@Component
public class CursorCodec {

    @Value("${app.cursor.secret:default-cursor-secret-change-me}")
    private String secret;

    public String encode(String payload) {
        String mac = hmac(payload);
        String combined = payload + ":" + mac;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(combined.getBytes(StandardCharsets.UTF_8));
    }

    public String decode(String cursor) {
        try {
            String combined = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int lastColon = combined.lastIndexOf(":");
            if (lastColon < 0) {
                throw new IllegalArgumentException("Invalid cursor format");
            }
            String payload = combined.substring(0, lastColon);
            String mac = combined.substring(lastColon + 1);
            if (!hmac(payload).equals(mac)) {
                throw new IllegalArgumentException("Invalid cursor signature");
            }
            return payload;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor", e);
        }
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(result).substring(0, 16);
        } catch (Exception e) {
            throw new RuntimeException("HMAC failed", e);
        }
    }
}
