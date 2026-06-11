package dev.persefonia.webadmin.content;

public record AdminContentListItemView(
        String contentId,
        String type,
        String language,
        String status,
        String visibility,
        String slug,
        String title,
        String updatedAt,
        String editLink,
        String previewLink) {
    public boolean previewAvailable() {
        return previewLink != null;
    }
}
