package dev.persefonia.communication.application.query;

import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import java.util.Optional;

public record ContactMessageAdminListRequest(ContactMessageStatus statusFilter, int page, int pageSize) {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public ContactMessageAdminListRequest {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be at least 1");
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be at most " + MAX_PAGE_SIZE);
        }
    }

    public static ContactMessageAdminListRequest firstPage() {
        return new ContactMessageAdminListRequest(null, 1, DEFAULT_PAGE_SIZE);
    }

    public Optional<ContactMessageStatus> statusFilterOptional() {
        return Optional.ofNullable(statusFilter);
    }

    public int offset() {
        return (page - 1) * pageSize;
    }
}
