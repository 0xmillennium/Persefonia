package dev.persefonia.identityaccess.domain.admin.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcGroup;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;

class AdminAccessPolicyTest {
    @Test
    void admissionRequiresExactGroup() {
        AdminAccessPolicy policy = AdminAccessPolicy.of(OidcGroup.of("admin"), true, false);
        assertThat(policy.evaluateAdmission(claims(Set.of("admin"))).isAllowed()).isTrue();
        assertThat(policy.evaluateAdmission(claims(Set.of("user", "admin"))).isAllowed()).isTrue();
        for (Set<String> groups : Set.of(Set.<String>of(), Set.of("user"), Set.of("Admin"), Set.of("ADMIN"))) {
            assertThat(policy.evaluateAdmission(claims(groups)).denialReason())
                    .contains(AdminAccessDenialReason.REQUIRED_OIDC_GROUP_MISSING);
        }
    }

    @Test
    void provisioningSwitchesAreIndependentOfAdmission() {
        AdminAccessPolicy enabled = AdminAccessPolicy.of(OidcGroup.of("admin"), true, true);
        assertThat(enabled.evaluateInitialOwnerBootstrap().isAllowed()).isTrue();
        assertThat(enabled.evaluateAutomaticProvisioning().isAllowed()).isTrue();
        AdminAccessPolicy disabled = AdminAccessPolicy.of(OidcGroup.of("admin"), false, false);
        assertThat(disabled.evaluateInitialOwnerBootstrap().denialReason())
                .contains(AdminAccessDenialReason.INITIAL_OWNER_BOOTSTRAP_DISABLED);
        assertThat(disabled.evaluateAutomaticProvisioning().denialReason())
                .contains(AdminAccessDenialReason.AUTOMATIC_PROVISIONING_DISABLED);
    }

    @Test
    void denialHasOnlySafeReason() {
        assertThatThrownBy(() -> AdminAccessDecision.denied(AdminAccessDenialReason.REQUIRED_OIDC_GROUP_MISSING)
                .throwIfDenied()).hasMessage("Admin access denied: REQUIRED_OIDC_GROUP_MISSING");
    }

    private static AdminIdentityClaims claims(Set<String> groups) {
        return AdminIdentityClaims.of(OidcSubject.of("opaque-subject"), EmailAddress.of("owner@example.com"),
                DisplayName.of("Owner"), groups.stream().map(OidcGroup::of).collect(Collectors.toSet()));
    }
}
