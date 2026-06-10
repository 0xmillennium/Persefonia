package dev.persefonia.identityaccess.domain.admin.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;

class AdminIdentityClaimsTest {
    private static final OidcSubject SUBJECT = OidcSubject.of("opaque-subject");
    private static final EmailAddress EMAIL = EmailAddress.of("owner@example.com");
    private static final DisplayName DISPLAY_NAME = DisplayName.of("Owner");

    @Test
    void requiresOidcSubject() {
        assertThatNullPointerException()
                .isThrownBy(() -> AdminIdentityClaims.of(null, EMAIL, DISPLAY_NAME));
    }

    @Test
    void requiresEmail() {
        assertThatNullPointerException()
                .isThrownBy(() -> AdminIdentityClaims.of(SUBJECT, null, DISPLAY_NAME));
    }

    @Test
    void requiresDisplayName() {
        assertThatNullPointerException()
                .isThrownBy(() -> AdminIdentityClaims.of(SUBJECT, EMAIL, null));
    }

    @Test
    void storesNoTokenSessionPasswordOrCredentialState() {
        List<String> forbidden = List.of("token", "session", "password", "credential");

        assertThat(AdminIdentityClaims.class.getDeclaredFields())
                .extracting(Field::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .noneMatch(name -> forbidden.stream().anyMatch(name::contains));
    }
}
