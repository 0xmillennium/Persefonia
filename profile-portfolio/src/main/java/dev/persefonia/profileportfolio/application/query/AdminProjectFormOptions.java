package dev.persefonia.profileportfolio.application.query;

import dev.persefonia.profileportfolio.application.port.ProjectTagOption;
import java.util.List;

public record AdminProjectFormOptions(String defaultLanguage, List<ProjectTagOption> assignableTags) {
    public AdminProjectFormOptions {
        assignableTags = List.copyOf(assignableTags);
    }
}
