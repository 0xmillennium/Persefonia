package dev.persefonia.identityaccess.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class AdminAccountForbiddenStateTest {
    private static final List<String> FORBIDDEN_FIELD_NAMES = List.of(
            "password",
            "passwordhash",
            "token",
            "accesstoken",
            "refreshtoken",
            "idtoken",
            "session",
            "securitycontext",
            "authentication",
            "credentials");

    @Test
    void aggregateDoesNotStoreForbiddenAuthenticationState() {
        assertThat(AdminAccount.class.getDeclaredFields())
                .extracting(Field::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .noneMatch(name -> FORBIDDEN_FIELD_NAMES.stream().anyMatch(name::contains));

        assertThat(AdminAccount.class.getDeclaredFields())
                .extracting(Field::getType)
                .extracting(Class::getPackageName)
                .noneMatch(packageName -> packageName.startsWith("org.springframework.security"));
    }

    @Test
    void aggregateDoesNotExposeForbiddenAuthenticationBehavior() {
        assertThat(AdminAccount.class.getDeclaredMethods())
                .extracting(Method::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .noneMatch(name -> name.contains("password")
                        || name.contains("token")
                        || name.contains("credentials"));
    }
}
