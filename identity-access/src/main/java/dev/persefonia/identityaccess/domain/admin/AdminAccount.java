package dev.persefonia.identityaccess.domain.admin;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class AdminAccount {
    private final AdminAccountId id;
    private final OidcSubject oidcSubject;
    private final EmailAddress email;
    private final NormalizedEmailAddress normalizedEmail;
    private final DisplayName displayName;
    private final Set<AdminRole> roles;
    private final AdminAccountStatus status;
    private final Instant lastLoginAt;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Version version;

    private AdminAccount(
            AdminAccountId id,
            OidcSubject oidcSubject,
            EmailAddress email,
            NormalizedEmailAddress normalizedEmail,
            DisplayName displayName,
            Set<AdminRole> roles,
            AdminAccountStatus status,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        this.id = Objects.requireNonNull(id, "id");
        this.oidcSubject = Objects.requireNonNull(oidcSubject, "oidcSubject");
        this.email = Objects.requireNonNull(email, "email");
        this.normalizedEmail = Objects.requireNonNull(normalizedEmail, "normalizedEmail");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        this.status = Objects.requireNonNull(status, "status");
        this.lastLoginAt = lastLoginAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = Objects.requireNonNull(version, "version");

        validateInvariants();
    }

    public static AdminAccount create(
            AdminAccountId id,
            OidcSubject oidcSubject,
            EmailAddress email,
            DisplayName displayName,
            Set<AdminRole> roles,
            Instant now) {
        Objects.requireNonNull(now, "now");
        return new AdminAccount(
                id,
                oidcSubject,
                email,
                NormalizedEmailAddress.from(email),
                displayName,
                roles,
                AdminAccountStatus.ACTIVE,
                null,
                now,
                now,
                Version.initial());
    }

    public static AdminAccount rehydrate(
            AdminAccountId id,
            OidcSubject oidcSubject,
            EmailAddress email,
            NormalizedEmailAddress normalizedEmail,
            DisplayName displayName,
            Set<AdminRole> roles,
            AdminAccountStatus status,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt,
            Version version) {
        return new AdminAccount(
                id,
                oidcSubject,
                email,
                normalizedEmail,
                displayName,
                roles,
                status,
                lastLoginAt,
                createdAt,
                updatedAt,
                version);
    }

    private void validateInvariants() {
        if (!normalizedEmail.equals(NormalizedEmailAddress.from(email))) {
            throw new IllegalArgumentException("normalizedEmail must be derived from email");
        }
        if (status == AdminAccountStatus.ACTIVE && roles.isEmpty()) {
            throw new IllegalArgumentException("active account must have at least one role");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (lastLoginAt != null && lastLoginAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("lastLoginAt must not be before createdAt");
        }
    }

    public AdminAccountId id() {
        return id;
    }

    public OidcSubject oidcSubject() {
        return oidcSubject;
    }

    public EmailAddress email() {
        return email;
    }

    public NormalizedEmailAddress normalizedEmail() {
        return normalizedEmail;
    }

    public DisplayName displayName() {
        return displayName;
    }

    public Set<AdminRole> roles() {
        return Set.copyOf(roles);
    }

    public AdminAccountStatus status() {
        return status;
    }

    public Optional<Instant> lastLoginAt() {
        return Optional.ofNullable(lastLoginAt);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Version version() {
        return version;
    }

    public boolean isActive() {
        return status == AdminAccountStatus.ACTIVE;
    }

    public boolean isDisabled() {
        return status == AdminAccountStatus.DISABLED;
    }

    public boolean hasRole(AdminRole role) {
        return roles.contains(Objects.requireNonNull(role, "role"));
    }

    public boolean canReceiveAdminSession() {
        return isActive();
    }

    public AdminAccount recordSuccessfulLogin(Instant now) {
        Objects.requireNonNull(now, "now");
        if (!isActive()) {
            throw new IllegalStateException("disabled account cannot record a successful login");
        }
        return rehydrate(
                id,
                oidcSubject,
                email,
                normalizedEmail,
                displayName,
                roles,
                status,
                now,
                createdAt,
                now,
                version.next());
    }

    public AdminAccount disable(Instant now) {
        Objects.requireNonNull(now, "now");
        if (isDisabled()) {
            return this;
        }
        return rehydrate(
                id,
                oidcSubject,
                email,
                normalizedEmail,
                displayName,
                roles,
                AdminAccountStatus.DISABLED,
                lastLoginAt,
                createdAt,
                now,
                version.next());
    }
}
