package dev.persefonia.discovery.application.contract;

public enum RedirectStatusCode {
    MOVED_PERMANENTLY_301(301),
    FOUND_302(302),
    TEMPORARY_REDIRECT_307(307),
    PERMANENT_REDIRECT_308(308);

    private final int value;

    RedirectStatusCode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
