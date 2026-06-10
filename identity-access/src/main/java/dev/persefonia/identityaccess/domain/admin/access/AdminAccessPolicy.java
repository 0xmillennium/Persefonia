package dev.persefonia.identityaccess.domain.admin.access;

import java.util.Objects;
import java.util.Set;

import dev.persefonia.identityaccess.domain.admin.NormalizedEmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;

public final class AdminAccessPolicy {
    private final Set<OidcSubject> allowlistedSubjects;
    private final Set<NormalizedEmailAddress> allowlistedEmails;
    private final boolean initialOwnerBootstrapEnabled;
    private final boolean automaticProvisioningEnabled;

    private AdminAccessPolicy(
            Set<OidcSubject> allowlistedSubjects,
            Set<NormalizedEmailAddress> allowlistedEmails,
            boolean initialOwnerBootstrapEnabled,
            boolean automaticProvisioningEnabled) {
        this.allowlistedSubjects = Set.copyOf(Objects.requireNonNull(allowlistedSubjects, "allowlistedSubjects"));
        this.allowlistedEmails = Set.copyOf(Objects.requireNonNull(allowlistedEmails, "allowlistedEmails"));
        this.initialOwnerBootstrapEnabled = initialOwnerBootstrapEnabled;
        this.automaticProvisioningEnabled = automaticProvisioningEnabled;
    }

    public static AdminAccessPolicy of(
            Set<OidcSubject> allowlistedSubjects,
            Set<NormalizedEmailAddress> allowlistedEmails,
            boolean initialOwnerBootstrapEnabled,
            boolean automaticProvisioningEnabled) {
        return new AdminAccessPolicy(
                allowlistedSubjects,
                allowlistedEmails,
                initialOwnerBootstrapEnabled,
                automaticProvisioningEnabled);
    }

    public boolean isAllowlisted(AdminIdentityClaims claims) {
        Objects.requireNonNull(claims, "claims");
        return allowlistedSubjects.contains(claims.oidcSubject())
                || allowlistedEmails.contains(NormalizedEmailAddress.from(claims.email()));
    }

    public AdminAccessDecision evaluateInitialOwnerBootstrap(
            AdminIdentityClaims claims,
            boolean anyAdminAccountExists) {
        if (!isAllowlisted(claims)) {
            return AdminAccessDecision.denied(AdminAccessDenialReason.NOT_ALLOWLISTED);
        }
        if (anyAdminAccountExists) {
            return AdminAccessDecision.denied(AdminAccessDenialReason.AUTOMATIC_PROVISIONING_DISABLED);
        }
        if (!initialOwnerBootstrapEnabled) {
            return AdminAccessDecision.denied(AdminAccessDenialReason.INITIAL_OWNER_BOOTSTRAP_DISABLED);
        }
        return AdminAccessDecision.allowed();
    }

    public AdminAccessDecision evaluateAutomaticProvisioning(
            AdminIdentityClaims claims,
            boolean anyAdminAccountExists) {
        if (!isAllowlisted(claims)) {
            return AdminAccessDecision.denied(AdminAccessDenialReason.NOT_ALLOWLISTED);
        }
        if (!anyAdminAccountExists) {
            return evaluateInitialOwnerBootstrap(claims, false);
        }
        if (!automaticProvisioningEnabled) {
            return AdminAccessDecision.denied(AdminAccessDenialReason.AUTOMATIC_PROVISIONING_DISABLED);
        }
        return AdminAccessDecision.allowed();
    }

    public boolean initialOwnerBootstrapEnabled() {
        return initialOwnerBootstrapEnabled;
    }

    public boolean automaticProvisioningEnabled() {
        return automaticProvisioningEnabled;
    }
}
