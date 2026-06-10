package dev.persefonia.identityaccess.domain.admin.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.NormalizedEmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;

class AdminAccessPolicyTest {
    private static final String RAW_SUBJECT = "opaque-owner-subject";
    private static final String RAW_EMAIL = "Owner@Example.COM";
    private static final AdminIdentityClaims CLAIMS = AdminIdentityClaims.of(
            OidcSubject.of(RAW_SUBJECT),
            EmailAddress.of(RAW_EMAIL),
            DisplayName.of("Owner"));

    @Test
    void subjectAllowlistAllowsIdentity() {
        assertThat(policy(Set.of(CLAIMS.oidcSubject()), Set.of(), true, false).isAllowlisted(CLAIMS)).isTrue();
    }

    @Test
    void emailAllowlistAllowsIdentityUsingNormalizedEmail() {
        assertThat(policy(Set.of(), Set.of(NormalizedEmailAddress.of("OWNER@example.com")), true, false)
                .isAllowlisted(CLAIMS)).isTrue();
    }

    @Test
    void unlistedIdentityIsNotAllowlisted() {
        assertThat(policy(Set.of(), Set.of(), true, false).isAllowlisted(CLAIMS)).isFalse();
    }

    @Test
    void initialOwnerBootstrapAllowedWhenNoAccountsAndAllowlisted() {
        assertThat(subjectPolicy(true, false).evaluateInitialOwnerBootstrap(CLAIMS, false).isAllowed()).isTrue();
    }

    @Test
    void initialOwnerBootstrapDeniedWhenBootstrapDisabled() {
        assertDenied(
                subjectPolicy(false, false).evaluateInitialOwnerBootstrap(CLAIMS, false),
                AdminAccessDenialReason.INITIAL_OWNER_BOOTSTRAP_DISABLED);
    }

    @Test
    void initialOwnerBootstrapDeniedWhenIdentityNotAllowlisted() {
        assertDenied(
                policy(Set.of(), Set.of(), true, false).evaluateInitialOwnerBootstrap(CLAIMS, false),
                AdminAccessDenialReason.NOT_ALLOWLISTED);
    }

    @Test
    void initialOwnerBootstrapDeniedWhenAccountAlreadyExists() {
        assertDenied(
                subjectPolicy(true, false).evaluateInitialOwnerBootstrap(CLAIMS, true),
                AdminAccessDenialReason.AUTOMATIC_PROVISIONING_DISABLED);
    }

    @Test
    void automaticProvisioningDeniedByDefaultAfterBootstrap() {
        assertDenied(
                subjectPolicy(true, false).evaluateAutomaticProvisioning(CLAIMS, true),
                AdminAccessDenialReason.AUTOMATIC_PROVISIONING_DISABLED);
    }

    @Test
    void automaticProvisioningAllowedWhenEnabledAndAllowlisted() {
        assertThat(subjectPolicy(true, true).evaluateAutomaticProvisioning(CLAIMS, true).isAllowed()).isTrue();
    }

    @Test
    void allowlistSetsAreDefensivelyCopied() {
        Set<OidcSubject> subjects = new HashSet<>(Set.of(CLAIMS.oidcSubject()));
        Set<NormalizedEmailAddress> emails = new HashSet<>();
        AdminAccessPolicy policy = policy(subjects, emails, true, false);

        subjects.clear();
        emails.add(NormalizedEmailAddress.from(CLAIMS.email()));

        assertThat(policy.isAllowlisted(CLAIMS)).isTrue();
    }

    @Test
    void deniedDecisionThrowsSafeException() {
        AdminAccessDecision decision = AdminAccessDecision.denied(AdminAccessDenialReason.NOT_ALLOWLISTED);

        assertThatThrownBy(decision::throwIfDenied)
                .isInstanceOf(AdminAccessDeniedException.class)
                .hasMessage("Admin access denied: NOT_ALLOWLISTED")
                .hasMessageNotContaining(RAW_SUBJECT)
                .hasMessageNotContaining(RAW_EMAIL);
    }

    private static AdminAccessPolicy subjectPolicy(boolean bootstrap, boolean provisioning) {
        return policy(Set.of(CLAIMS.oidcSubject()), Set.of(), bootstrap, provisioning);
    }

    private static AdminAccessPolicy policy(
            Set<OidcSubject> subjects,
            Set<NormalizedEmailAddress> emails,
            boolean bootstrap,
            boolean provisioning) {
        return AdminAccessPolicy.of(subjects, emails, bootstrap, provisioning);
    }

    private static void assertDenied(AdminAccessDecision decision, AdminAccessDenialReason reason) {
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.denialReason()).contains(reason);
    }
}
