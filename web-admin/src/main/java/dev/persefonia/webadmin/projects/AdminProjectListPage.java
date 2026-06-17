package dev.persefonia.webadmin.projects;

import dev.persefonia.profileportfolio.application.query.AdminProjectListItem;
import java.util.List;

public record AdminProjectListPage(
        AdminProjectPageChrome chrome,
        List<AdminProjectListItem> projects,
        String successMessage) {
    public AdminProjectListPage {
        projects = List.copyOf(projects);
    }
}
