package dev.persefonia.app.security.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import dev.persefonia.identityaccess.application.admin.authorization.AdminCommand;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationDenialReason;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationException;

class AdminCommandAuthorizationExceptionHandlerTest {
    private final AdminCommandAuthorizationExceptionHandler handler =
            new AdminCommandAuthorizationExceptionHandler();

    @Test
    void mapsAuthorizationExceptionTo403() {
        assertThat(handler.handle(exception()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void responseBodyDoesNotExposeReasonOrIdentity() {
        var response = handler.handle(exception());

        assertThat(response.getBody()).isNull();
        assertThat(response.toString())
                .doesNotContain("OWNER_REQUIRED")
                .doesNotContain("00000000-0000-0000-0000-000000000001")
                .doesNotContain("admin@example.com")
                .doesNotContain("opaque-subject")
                .doesNotContain("fake-id-token-value");
    }

    private static AdminCommandAuthorizationException exception() {
        return new AdminCommandAuthorizationException(
                AdminCommandAuthorizationDenialReason.OWNER_REQUIRED,
                AdminCommand.named("test.admin.mutate"));
    }
}
