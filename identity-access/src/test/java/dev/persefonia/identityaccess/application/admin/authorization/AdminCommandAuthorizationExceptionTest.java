package dev.persefonia.identityaccess.application.admin.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AdminCommandAuthorizationExceptionTest {
    private static final AdminCommand COMMAND = AdminCommand.named("test.admin.mutate");

    @Test
    void requiresReason() {
        assertThatThrownBy(() -> new AdminCommandAuthorizationException(null, COMMAND))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void exposesReason() {
        AdminCommandAuthorizationException exception = exception();

        assertThat(exception.reason()).isEqualTo(AdminCommandAuthorizationDenialReason.OWNER_REQUIRED);
        assertThat(exception.command()).contains(COMMAND);
    }

    @Test
    void messageContainsReason() {
        assertThat(exception()).hasMessageContaining("OWNER_REQUIRED");
    }

    @Test
    void messageDoesNotExposeAccountIdEmailSubjectTokenOrSession() {
        assertThat(exception())
                .hasMessageNotContaining("00000000-0000-0000-0000-000000000001")
                .hasMessageNotContaining("admin@example.com")
                .hasMessageNotContaining("opaque-subject")
                .hasMessageNotContaining("fake-id-token-value")
                .hasMessageNotContaining("JSESSIONID")
                .hasMessageNotContaining(COMMAND.name());
    }

    private static AdminCommandAuthorizationException exception() {
        return new AdminCommandAuthorizationException(
                AdminCommandAuthorizationDenialReason.OWNER_REQUIRED,
                COMMAND);
    }
}
