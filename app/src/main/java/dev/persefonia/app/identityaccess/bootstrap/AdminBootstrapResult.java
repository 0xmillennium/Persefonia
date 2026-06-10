package dev.persefonia.app.identityaccess.bootstrap;

import java.util.Objects;

import dev.persefonia.identityaccess.domain.admin.AdminAccount;

public record AdminBootstrapResult(AdminAccount account, AdminBootstrapOutcome outcome) {
    public AdminBootstrapResult {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(outcome, "outcome");
    }
}
