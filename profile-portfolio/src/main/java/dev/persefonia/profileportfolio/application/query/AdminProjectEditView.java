package dev.persefonia.profileportfolio.application.query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminProjectEditView(
        UUID id,
        String status,
        String visibility,
        boolean featured,
        Integer sortOrder,
        String defaultLanguage,
        List<AdminProjectTagView> assignedTags,
        List<AdminProjectLocalizationView> localizations,
        List<AdminProjectTechnologyView> technologies,
        List<AdminProjectLinkView> links,
        Instant updatedAt,
        long version) {
    public AdminProjectEditView {
        assignedTags = List.copyOf(assignedTags);
        localizations = List.copyOf(localizations);
        technologies = List.copyOf(technologies);
        links = List.copyOf(links);
    }
}
