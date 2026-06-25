package dev.persefonia.communication.application.query;

import java.util.List;

public record ContactMessageAdminListPage(
        List<ContactMessageAdminListItem> items,
        int page,
        int pageSize,
        long totalItems) {
    public ContactMessageAdminListPage {
        items = List.copyOf(items);
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be at least 1");
        }
        if (totalItems < 0) {
            throw new IllegalArgumentException("totalItems must not be negative");
        }
    }
}
