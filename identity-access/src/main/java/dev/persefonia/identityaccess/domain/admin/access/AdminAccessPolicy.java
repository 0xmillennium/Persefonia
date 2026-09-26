package dev.persefonia.identityaccess.domain.admin.access;

import java.util.Objects;

import dev.persefonia.identityaccess.domain.admin.OidcGroup;

public final class AdminAccessPolicy {
    private final OidcGroup requiredOidcGroup;
    private final boolean initialOwnerBootstrapEnabled;
    private final boolean automaticProvisioningEnabled;

    private AdminAccessPolicy(
            OidcGroup requiredOidcGroup,
            boolean initialOwnerBootstrapEnabled,
            boolean automaticProvisioningEnabled) {
        this.requiredOidcGroup = Objects.requireNonNull(requiredOidcGroup, "requiredOidcGroup");
        this.initialOwnerBootstrapEnabled = initialOwnerBootstrapEnabled;
        this.automaticProvisioningEnabled = automaticProvisioningEnabled;
    }

    public static AdminAccessPolicy of(
            OidcGroup requiredOidcGroup,
            boolean initialOwnerBootstrapEnabled,
            boolean automaticProvisioningEnabled) {
        return new AdminAccessPolicy(requiredOidcGroup, initialOwnerBootstrapEnabled, automaticProvisioningEnabled);
    }

    public AdminAccessDecision evaluateAdmission(AdminIdentityClaims claims) {
        Objects.requireNonNull(claims, "claims");
        return claims.oidcGroups().contains(requiredOidcGroup)
                ? AdminAccessDecision.allowed()
                : AdminAccessDecision.denied(AdminAccessDenialReason.REQUIRED_OIDC_GROUP_MISSING);
    }

    public AdminAccessDecision evaluateInitialOwnerBootstrap() {
        return initialOwnerBootstrapEnabled
                ? AdminAccessDecision.allowed()
                : AdminAccessDecision.denied(AdminAccessDenialReason.INITIAL_OWNER_BOOTSTRAP_DISABLED);
    }

    public AdminAccessDecision evaluateAutomaticProvisioning() {
        return automaticProvisioningEnabled
                ? AdminAccessDecision.allowed()
                : AdminAccessDecision.denied(AdminAccessDenialReason.AUTOMATIC_PROVISIONING_DISABLED);
    }

    public boolean initialOwnerBootstrapEnabled() {
        return initialOwnerBootstrapEnabled;
    }

    public boolean automaticProvisioningEnabled() {
        return automaticProvisioningEnabled;
    }
}
