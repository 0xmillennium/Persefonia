package dev.persefonia.webadmin.content;

public record AdminContentRevisionListItemView(
        String revisionNumber,
        String revisionType,
        String title,
        String slug,
        String createdBy,
        String createdAt,
        String changeNote,
        boolean renderedHtmlPresent) {
}
