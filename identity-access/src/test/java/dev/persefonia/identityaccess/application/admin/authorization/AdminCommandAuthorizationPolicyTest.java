package dev.persefonia.identityaccess.application.admin.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;

class AdminCommandAuthorizationPolicyTest {
    private static final AdminAccountId ACCOUNT_ID =
            AdminAccountId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final AdminCommand COMMAND = AdminCommand.named("test.admin.mutate");

    private final AdminCommandAuthorizationPolicy policy = new AdminCommandAuthorizationPolicy();

    @Test
    void activeOwnerIsAllowed() {
        AdminCommandAuthorizationDecision decision = policy.evaluateOwnerRequired(active(AdminRole.OWNER), COMMAND);

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.denialReason()).isEmpty();
    }

    @Test
    void activeEditorIsDeniedOwnerRequired() {
        assertDenied(
                policy.evaluateOwnerRequired(active(AdminRole.EDITOR), COMMAND),
                AdminCommandAuthorizationDenialReason.OWNER_REQUIRED);
    }

    @Test
    void disabledOwnerIsDeniedInactiveAdmin() {
        assertDenied(
                policy.evaluateOwnerRequired(actor(AdminAccountStatus.DISABLED, AdminRole.OWNER), COMMAND),
                AdminCommandAuthorizationDenialReason.INACTIVE_ADMIN);
    }

    @Test
    void missingActorIsDenied() {
        assertDenied(
                policy.evaluateOwnerRequired(null, COMMAND),
                AdminCommandAuthorizationDenialReason.MISSING_ACTOR);
    }

    @Test
    void nullCommandIsRejected() {
        assertThatThrownBy(() -> policy.evaluateOwnerRequired(active(AdminRole.OWNER), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requireOwnerReturnsForActiveOwner() {
        policy.requireOwner(active(AdminRole.OWNER), COMMAND);
    }

    @Test
    void requireOwnerThrowsForEditor() {
        assertThatThrownBy(() -> policy.requireOwner(active(AdminRole.EDITOR), COMMAND))
                .isInstanceOf(AdminCommandAuthorizationException.class)
                .extracting("reason")
                .isEqualTo(AdminCommandAuthorizationDenialReason.OWNER_REQUIRED);
    }

    @Test
    void requireOwnerThrowsForDisabledOwner() {
        assertThatThrownBy(() -> policy.requireOwner(actor(AdminAccountStatus.DISABLED, AdminRole.OWNER), COMMAND))
                .isInstanceOf(AdminCommandAuthorizationException.class)
                .extracting("reason")
                .isEqualTo(AdminCommandAuthorizationDenialReason.INACTIVE_ADMIN);
    }

    @Test
    void denialDecisionThrowsSafeException() {
        AdminCommandAuthorizationDecision decision =
                AdminCommandAuthorizationDecision.denied(AdminCommandAuthorizationDenialReason.OWNER_REQUIRED);

        assertThatThrownBy(() -> decision.throwIfDenied(COMMAND))
                .isInstanceOf(AdminCommandAuthorizationException.class)
                .hasMessage("Admin command authorization denied: OWNER_REQUIRED")
                .hasMessageNotContaining(COMMAND.name());
    }

    @Test
    void exceptionMessageDoesNotExposeAccountId() {
        assertThatThrownBy(() -> policy.requireOwner(active(AdminRole.EDITOR), COMMAND))
                .isInstanceOf(AdminCommandAuthorizationException.class)
                .hasMessageNotContaining(ACCOUNT_ID.value().toString());
    }

    @Test
    void policyHasNoSpringSecurityJdbcOrWebFields() {
        assertThat(AdminCommandAuthorizationPolicy.class.getDeclaredFields()).isEmpty();
    }

    private static void assertDenied(
            AdminCommandAuthorizationDecision decision,
            AdminCommandAuthorizationDenialReason reason) {
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.denialReason()).contains(reason);
    }

    private static AdminCommandActor active(AdminRole role) {
        return actor(AdminAccountStatus.ACTIVE, role);
    }

    private static AdminCommandActor actor(AdminAccountStatus status, AdminRole role) {
        return new AdminCommandActor(ACCOUNT_ID, status, Set.of(role));
    }
}
