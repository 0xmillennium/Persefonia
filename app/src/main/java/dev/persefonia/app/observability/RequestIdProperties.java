package dev.persefonia.app.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("persefonia.observability.request-id")
public class RequestIdProperties {
    private String responseHeader = "X-Request-Id";
    private String incomingHeader = "X-Request-Id";
    private boolean trustIncomingHeader;
    private int maxLength = 80;

    public String getResponseHeader() {
        return responseHeader;
    }

    public void setResponseHeader(String responseHeader) {
        this.responseHeader = responseHeader;
    }

    public String getIncomingHeader() {
        return incomingHeader;
    }

    public void setIncomingHeader(String incomingHeader) {
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
        this.maxLength = maxLength;
    }
}
