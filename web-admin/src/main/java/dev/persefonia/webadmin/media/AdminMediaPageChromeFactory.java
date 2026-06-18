package dev.persefonia.webadmin.media;

import dev.persefonia.webadmin.AdminNavigationFactory;
import dev.persefonia.webadmin.AdminNavigationSection;
import dev.persefonia.webadmin.AuthenticatedAdminViewResolver;
import dev.persefonia.webadmin.LogoutFormViewModel;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

@Component
public final class AdminMediaPageChromeFactory {
    private final AuthenticatedAdminViewResolver adminViews;
    private final AdminNavigationFactory navigation;

    public AdminMediaPageChromeFactory(
            AuthenticatedAdminViewResolver adminViews,
            AdminNavigationFactory navigation) {
        this.adminViews = Objects.requireNonNull(adminViews, "adminViews");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    public AdminMediaPageChrome create(Authentication authentication, CsrfToken csrfToken) {
        Objects.requireNonNull(csrfToken, "csrfToken");
        return new AdminMediaPageChrome(
                adminViews.resolve(authentication),
                navigation.create(AdminNavigationSection.MEDIA),
                new LogoutFormViewModel("/logout", csrfToken.getParameterName(), csrfToken.getToken()));
    }
}
