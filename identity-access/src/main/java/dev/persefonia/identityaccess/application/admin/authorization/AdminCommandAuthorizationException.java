package dev.persefonia.identityaccess.application.admin.authorization;

import java.util.Objects;
import java.util.Optional;

public final class AdminCommandAuthorizationException extends RuntimeException {
    private final AdminCommandAuthorizationDenialReason reason;
    private final AdminCommand command;

    public AdminCommandAuthorizationException(
            AdminCommandAuthorizationDenialReason reason,
            AdminCommand command) {
        super("Admin command authorization denied: " + Objects.requireNonNull(reason, "reason"));
        this.reason = reason;
        this.command = Objects.requireNonNull(command, "command");
    }

    public AdminCommandAuthorizationDenialReason reason() {
        return reason;
    }

    public Optional<AdminCommand> command() {
        return Optional.of(command);
    }
}
