package dev.persefonia.webadmin.discovery;

public final class AdminRedirectForm {
    private String sourceUrl = "";
    private String targetUrl = "";
    private String statusCode = "301";

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = value(sourceUrl);
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = value(targetUrl);
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = value(statusCode);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
