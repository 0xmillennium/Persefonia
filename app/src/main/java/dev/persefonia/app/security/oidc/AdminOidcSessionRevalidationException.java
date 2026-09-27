package dev.persefonia.app.security.oidc;

import java.util.Objects;

public final class AdminOidcSessionRevalidationException extends RuntimeException {
    private final AdminOidcSessionFailureReason reason;

    public AdminOidcSessionRevalidationException(AdminOidcSessionFailureReason reason) {
        super("Admin session revalidation failed: " + Objects.requireNonNull(reason, "reason").name());
        this.reason = reason;
    }

    public AdminOidcSessionFailureReason reason() {
        return reason;
    }
}
