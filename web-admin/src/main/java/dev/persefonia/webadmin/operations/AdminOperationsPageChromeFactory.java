package dev.persefonia.webadmin.operations;

import dev.persefonia.webadmin.*;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

@Component
public final class AdminOperationsPageChromeFactory {
    private final AuthenticatedAdminViewResolver adminViews;
    private final AdminNavigationFactory navigation;

    public AdminOperationsPageChromeFactory(
            AuthenticatedAdminViewResolver adminViews,
            AdminNavigationFactory navigation) {
        this.adminViews = Objects.requireNonNull(adminViews, "adminViews");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    public AdminOperationsPageChrome create(Authentication authentication, CsrfToken csrfToken) {
        Objects.requireNonNull(csrfToken, "csrfToken");
        return new AdminOperationsPageChrome(
                adminViews.resolve(authentication),
                navigation.create(AdminNavigationSection.OPERATIONS),
                new LogoutFormViewModel("/logout", csrfToken.getParameterName(), csrfToken.getToken()));
    }
}
