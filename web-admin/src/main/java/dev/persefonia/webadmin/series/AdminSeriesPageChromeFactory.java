package dev.persefonia.webadmin.series;

import dev.persefonia.webadmin.AdminNavigationItem;
import dev.persefonia.webadmin.AuthenticatedAdminViewResolver;
import dev.persefonia.webadmin.LogoutFormViewModel;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

@Component
public final class AdminSeriesPageChromeFactory {
    private final AuthenticatedAdminViewResolver adminViews;

    public AdminSeriesPageChromeFactory(AuthenticatedAdminViewResolver adminViews) {
        this.adminViews = Objects.requireNonNull(adminViews, "adminViews");
    }

    public AdminSeriesPageChrome create(Authentication authentication, CsrfToken csrfToken) {
        Objects.requireNonNull(csrfToken, "csrfToken");
        return new AdminSeriesPageChrome(
                adminViews.resolve(authentication),
                List.of(
                        AdminNavigationItem.link("Dashboard", "/admin"),
                        AdminNavigationItem.link("Content", "/admin/content"),
                        AdminNavigationItem.activeLink("Series", "/admin/series"),
                        AdminNavigationItem.link("Tags", "/admin/tags"),
                        AdminNavigationItem.link("Redirects", "/admin/discovery/redirects"),
                        AdminNavigationItem.disabled("Projects"),
                        AdminNavigationItem.disabled("Media"),
                        AdminNavigationItem.disabled("Contact"),
                        AdminNavigationItem.disabled("Analytics"),
                        AdminNavigationItem.disabled("Audit"),
                        AdminNavigationItem.disabled("Settings")),
                new LogoutFormViewModel("/logout", csrfToken.getParameterName(), csrfToken.getToken()));
    }
}
