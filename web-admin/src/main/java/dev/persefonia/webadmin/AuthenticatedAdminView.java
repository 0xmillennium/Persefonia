package dev.persefonia.webadmin;

import java.util.List;
import java.util.Objects;

public record AuthenticatedAdminView(
        String displayName,
        List<String> roleLabels,
        boolean owner,
        boolean editor) {
    public AuthenticatedAdminView {
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }

        roleLabels = List.copyOf(Objects.requireNonNull(roleLabels, "roleLabels"));
        if (roleLabels.stream().anyMatch(role -> role == null || role.isBlank())) {
            throw new IllegalArgumentException("roleLabels must not contain null or blank entries");
        }
    }
}
