package dev.persefonia.webadmin.cv;

import dev.persefonia.webadmin.AdminNavigationFactory;
import dev.persefonia.webadmin.AdminNavigationSection;
import dev.persefonia.webadmin.AuthenticatedAdminViewResolver;
import dev.persefonia.webadmin.LogoutFormViewModel;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

@Component
public final class AdminCvPageChromeFactory {
    private final AuthenticatedAdminViewResolver adminViews;
    private final AdminNavigationFactory navigation;

    public AdminCvPageChromeFactory(
            AuthenticatedAdminViewResolver adminViews,
            AdminNavigationFactory navigation) {
        this.adminViews = Objects.requireNonNull(adminViews, "adminViews");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    public AdminCvPageChrome create(Authentication authentication, CsrfToken csrfToken) {
        Objects.requireNonNull(csrfToken, "csrfToken");
        return new AdminCvPageChrome(
                adminViews.resolve(authentication),
                navigation.create(AdminNavigationSection.CV),
                new LogoutFormViewModel("/logout", csrfToken.getParameterName(), csrfToken.getToken()));
    }
}
