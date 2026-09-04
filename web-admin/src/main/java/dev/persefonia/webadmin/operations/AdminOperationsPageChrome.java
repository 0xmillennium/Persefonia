package dev.persefonia.webadmin.operations;

import dev.persefonia.webadmin.*;
import java.util.List;
import java.util.Objects;

public record AdminOperationsPageChrome(
        AuthenticatedAdminView admin,
        List<AdminNavigationItem> navigation,
        LogoutFormViewModel logoutForm) {
    public AdminOperationsPageChrome {
        Objects.requireNonNull(admin, "admin");
        navigation = List.copyOf(Objects.requireNonNull(navigation, "navigation"));
        Objects.requireNonNull(logoutForm, "logoutForm");
    }
}
