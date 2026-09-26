package dev.persefonia.identityaccess.domain.admin.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import dev.persefonia.identityaccess.domain.admin.OidcGroup;

import org.junit.jupiter.api.Test;

import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;

class AdminIdentityClaimsTest {
    private static final OidcSubject SUBJECT = OidcSubject.of("opaque-subject");
    private static final EmailAddress EMAIL = EmailAddress.of("owner@example.com");
    private static final DisplayName DISPLAY_NAME = DisplayName.of("Owner");

    @Test
    void groupsAreRequiredAndDefensivelyCopied() {
        assertThatNullPointerException().isThrownBy(() -> AdminIdentityClaims.of(SUBJECT, EMAIL, DISPLAY_NAME, null));
        Set<OidcGroup> groups = new HashSet<>();
        groups.add(OidcGroup.of("admin"));
        groups.add(OidcGroup.of("admin"));
        AdminIdentityClaims claims = AdminIdentityClaims.of(SUBJECT, EMAIL, DISPLAY_NAME, groups);
        groups.clear();
        assertThat(claims.oidcGroups()).containsExactly(OidcGroup.of("admin"));
        assertThatThrownBy(() -> claims.oidcGroups().clear()).isInstanceOf(UnsupportedOperationException.class);
        groups.add(null);
        assertThatNullPointerException().isThrownBy(() -> AdminIdentityClaims.of(SUBJECT, EMAIL, DISPLAY_NAME, groups));
    }

    @Test
    void requiresOidcSubject() {
        assertThatNullPointerException()
                .isThrownBy(() -> AdminIdentityClaims.of(null, EMAIL, DISPLAY_NAME, Set.of()));
    }

    @Test
    void requiresEmail() {
        assertThatNullPointerException()
                .isThrownBy(() -> AdminIdentityClaims.of(SUBJECT, null, DISPLAY_NAME, Set.of()));
    }

    @Test
    void requiresDisplayName() {
        assertThatNullPointerException()
                .isThrownBy(() -> AdminIdentityClaims.of(SUBJECT, EMAIL, null, Set.of()));
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
