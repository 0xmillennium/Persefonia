package dev.persefonia.app.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("persefonia.observability.request-id")
public class RequestIdProperties {
    private static final int MIN_MAX_LENGTH = 16;
    private static final int MAX_MAX_LENGTH = 128;

    private String responseHeader = "X-Request-Id";
    private String incomingHeader = "X-Request-Id";
    private boolean trustIncomingHeader;
    private int maxLength = 80;

    public RequestIdProperties() {
        validate();
    }

    public String getResponseHeader() {
        return responseHeader;
    }

    public void setResponseHeader(String responseHeader) {
        validateHeaderName(responseHeader, "responseHeader");
        this.responseHeader = responseHeader;
    }

    public String getIncomingHeader() {
        return incomingHeader;
    }

    public void setIncomingHeader(String incomingHeader) {
        validateHeaderName(incomingHeader, "incomingHeader");
        this.incomingHeader = incomingHeader;
    }

    public boolean isTrustIncomingHeader() {
        return trustIncomingHeader;
    }

    public void setTrustIncomingHeader(boolean trustIncomingHeader) {
        this.trustIncomingHeader = trustIncomingHeader;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        validateMaxLength(maxLength);
        this.maxLength = maxLength;
    }

    private void validate() {
        validateHeaderName(responseHeader, "responseHeader");
        validateHeaderName(incomingHeader, "incomingHeader");
        validateMaxLength(maxLength);
    }

    private static void validateHeaderName(String headerName, String propertyName) {
        if (headerName == null || headerName.isBlank()) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }
        if (!headerName.chars().allMatch(RequestIdProperties::isHttpTokenCharacter)) {
            throw new IllegalArgumentException(propertyName + " must be a valid HTTP header name");
        }
    }

    private static boolean isHttpTokenCharacter(int character) {
        return character == '!'
                || character == '#'
                || character == '$'
                || character == '%'
                || character == '&'
                || character == '\''
                || character == '*'
                || character == '+'
                || character == '-'
                || character == '.'
                || character == '^'
                || character == '_'
                || character == '`'
                || character == '|'
                || character == '~'
                || (character >= '0' && character <= '9')
                || (character >= 'A' && character <= 'Z')
                || (character >= 'a' && character <= 'z');
    }

    private static void validateMaxLength(int maxLength) {
        if (maxLength < MIN_MAX_LENGTH || maxLength > MAX_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "maxLength must be between " + MIN_MAX_LENGTH + " and " + MAX_MAX_LENGTH);
        }
    }
}
