package dev.persefonia.webadmin.content;

import dev.persefonia.webadmin.AdminNavigationItem;
import dev.persefonia.webadmin.AuthenticatedAdminViewResolver;
import dev.persefonia.webadmin.LogoutFormViewModel;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

@Component
public final class AdminContentPageChromeFactory {
    private final AuthenticatedAdminViewResolver adminViews;

    public AdminContentPageChromeFactory(AuthenticatedAdminViewResolver adminViews) {
        this.adminViews = Objects.requireNonNull(adminViews, "adminViews");
    }

    public AdminContentPageChrome create(Authentication authentication, CsrfToken csrfToken) {
        Objects.requireNonNull(csrfToken, "csrfToken");
        return new AdminContentPageChrome(
                adminViews.resolve(authentication),
                List.of(
                        AdminNavigationItem.link("Dashboard", "/admin"),
                        AdminNavigationItem.activeLink("Content", "/admin/content"),
                        AdminNavigationItem.disabled("Projects"),
                        AdminNavigationItem.disabled("Media"),
                        AdminNavigationItem.disabled("Contact"),
                        AdminNavigationItem.disabled("Analytics"),
                        AdminNavigationItem.disabled("Audit"),
                        AdminNavigationItem.disabled("Settings")),
                new LogoutFormViewModel("/logout", csrfToken.getParameterName(), csrfToken.getToken()));
    }
}
