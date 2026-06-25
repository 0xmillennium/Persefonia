package dev.persefonia.app.platformoperations.ratelimit;

import dev.persefonia.platformoperations.application.port.RateLimitKey;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ContactRateLimitKeyFactory {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String KEY_PREFIX = "contact-form";

    private final byte[] secret;

    public ContactRateLimitKeyFactory(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public RateLimitKey derive(Enum<?> scope, String transientSignal) {
        Objects.requireNonNull(scope, "scope must not be null");
        if (transientSignal == null || transientSignal.isBlank()) {
            throw new IllegalArgumentException("transient signal must not be blank");
        }
        String normalizedSignal = transientSignal.trim();
        String material = scope.name().toLowerCase(Locale.ROOT) + '\n' + normalizedSignal;
        return new RateLimitKey(KEY_PREFIX + ":" + hmac(material));
    }

    private String hmac(String material) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Could not derive contact rate-limit key", exception);
        }
    }
}
