package dev.persefonia.webadmin;

import java.util.List;
import java.util.Objects;

public record AdminDashboardViewModel(
        String title,
        String heading,
        AuthenticatedAdminView admin,
        List<AdminNavigationItem> navigation,
        LogoutFormViewModel logoutForm) {
    public AdminDashboardViewModel {
        requireNonBlank(title, "title");
        requireNonBlank(heading, "heading");
        Objects.requireNonNull(admin, "admin");
        navigation = List.copyOf(Objects.requireNonNull(navigation, "navigation"));
        Objects.requireNonNull(logoutForm, "logoutForm");
    }

    public static AdminDashboardViewModel shell(
            AuthenticatedAdminView admin,
            LogoutFormViewModel logoutForm) {
        return new AdminDashboardViewModel(
                "Persefonia Admin",
                "Dashboard",
                admin,
                List.of(
                        AdminNavigationItem.activeLink("Dashboard", "/admin"),
                        AdminNavigationItem.disabled("Content"),
                        AdminNavigationItem.disabled("Projects"),
                        AdminNavigationItem.disabled("Media"),
                        AdminNavigationItem.disabled("Contact"),
                        AdminNavigationItem.disabled("Analytics"),
                        AdminNavigationItem.disabled("Audit"),
                        AdminNavigationItem.disabled("Settings")),
                logoutForm);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
