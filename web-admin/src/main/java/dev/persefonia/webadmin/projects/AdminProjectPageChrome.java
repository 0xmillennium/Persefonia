package dev.persefonia.webadmin.projects;

import dev.persefonia.webadmin.AdminNavigationItem;
import dev.persefonia.webadmin.AuthenticatedAdminView;
import dev.persefonia.webadmin.LogoutFormViewModel;
import java.util.List;
import java.util.Objects;

public record AdminProjectPageChrome(
        AuthenticatedAdminView admin,
        List<AdminNavigationItem> navigation,
        LogoutFormViewModel logoutForm) {
    public AdminProjectPageChrome {
        Objects.requireNonNull(admin, "admin");
        navigation = List.copyOf(Objects.requireNonNull(navigation, "navigation"));
        Objects.requireNonNull(logoutForm, "logoutForm");
    }
}
