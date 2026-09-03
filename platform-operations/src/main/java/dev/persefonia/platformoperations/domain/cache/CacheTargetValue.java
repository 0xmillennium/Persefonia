package dev.persefonia.platformoperations.domain.cache;

import java.util.Objects;
import java.util.regex.Pattern;

public record CacheTargetValue(String value) {
    private static final Pattern CACHE_TAG = Pattern.compile("^[a-z][a-z0-9:_-]{0,127}$");

    public CacheTargetValue {
        Objects.requireNonNull(value, "value");
    }

    public static CacheTargetValue of(CacheTargetType type, String value) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case URL -> url(value);
            case CACHE_TAG -> cacheTag(value);
        };
    }

    public static CacheTargetValue url(String value) {
        requireCommon(value, 2048);
        if (!value.startsWith("/")
                || value.startsWith("//")
                || value.contains("//")
                || value.contains("?")
                || value.contains("#")
                || value.contains("\\")
                || value.contains("%")
                || value.chars().anyMatch(Character::isWhitespace)
                || value.length() > 1 && value.endsWith("/")
                || hasDotSegment(value)
                || isSensitiveRoute(value)) {
            throw invalid("URL target must be a safe canonical site-root-relative path");
        }
        return new CacheTargetValue(value);
    }

    public static CacheTargetValue cacheTag(String value) {
        requireCommon(value, 128);
        if (!CACHE_TAG.matcher(value).matches()) {
            throw invalid("cache tag must use the provider-independent lowercase token grammar");
        }
        return new CacheTargetValue(value);
    }

    private static void requireCommon(String value, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw invalid("cache target value is blank or exceeds its maximum length");
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index)) || value.charAt(index) == '\n' || value.charAt(index) == '\r') {
                throw invalid("cache target value must be a single line without control characters");
            }
        }
    }

    private static boolean hasDotSegment(String value) {
        for (String segment : value.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSensitiveRoute(String value) {
        return isPathAtOrBelow(value, "/admin")
                || isPathAtOrBelow(value, "/oauth2")
                || isPathAtOrBelow(value, "/login")
                || isPathAtOrBelow(value, "/logout")
                || isPathAtOrBelow(value, "/actuator");
    }

    private static boolean isPathAtOrBelow(String value, String root) {
        return value.equals(root) || value.startsWith(root + "/");
    }

    private static CacheInvalidationValidationException invalid(String message) {
        return new CacheInvalidationValidationException(message);
    }
}
