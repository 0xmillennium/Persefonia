package dev.persefonia.webadmin.profile;

import dev.persefonia.webadmin.AdminNavigationItem;
import dev.persefonia.webadmin.AuthenticatedAdminView;
import dev.persefonia.webadmin.LogoutFormViewModel;
import java.util.List;
import java.util.Objects;

public record AdminPersonalProfilePageChrome(
        AuthenticatedAdminView admin,
        List<AdminNavigationItem> navigation,
        LogoutFormViewModel logoutForm) {
    public AdminPersonalProfilePageChrome {
        Objects.requireNonNull(admin, "admin");
        navigation = List.copyOf(Objects.requireNonNull(navigation, "navigation"));
        Objects.requireNonNull(logoutForm, "logoutForm");
    }
}
