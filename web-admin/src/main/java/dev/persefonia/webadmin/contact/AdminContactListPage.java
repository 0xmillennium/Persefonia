package dev.persefonia.webadmin.contact;

import dev.persefonia.communication.application.query.ContactMessageAdminListPage;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import java.util.List;
import java.util.Objects;

public record AdminContactListPage(
        AdminContactPageChrome chrome,
        ContactMessageAdminListPage messages,
        String selectedStatus,
        List<String> statusOptions) {
    public AdminContactListPage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(messages, "messages");
        selectedStatus = selectedStatus == null || selectedStatus.isBlank() ? "all" : selectedStatus;
        statusOptions = List.copyOf(Objects.requireNonNull(statusOptions, "statusOptions"));
    }

    public long totalPages() {
        if (messages.totalItems() == 0) {
            return 1;
        }
        return (long) Math.ceil((double) messages.totalItems() / messages.pageSize());
    }

    public boolean hasPreviousPage() {
        return messages.page() > 1;
    }

    public boolean hasNextPage() {
        return messages.page() < totalPages();
    }

    public int previousPage() {
        return Math.max(1, messages.page() - 1);
    }

    public int nextPage() {
        return messages.page() + 1;
    }

    public boolean selected(String value) {
        return selectedStatus.equals(value);
    }

    public boolean statusSelected(ContactMessageStatus status) {
        return selectedStatus.equals(status.name());
    }
}
