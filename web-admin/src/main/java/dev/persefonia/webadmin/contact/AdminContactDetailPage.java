package dev.persefonia.webadmin.contact;

import dev.persefonia.communication.application.query.ContactMessageAdminDetail;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import java.util.List;
import java.util.Objects;

public record AdminContactDetailPage(
        AdminContactPageChrome chrome,
        ContactMessageAdminDetail message,
        List<AdminContactStatusAction> statusActions,
        String flashMessage,
        String errorMessage) {
    public AdminContactDetailPage {
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(message, "message");
        statusActions = List.copyOf(Objects.requireNonNull(statusActions, "statusActions"));
    }

    public boolean hasFlashMessage() {
        return flashMessage != null && !flashMessage.isBlank();
    }

    public boolean hasErrorMessage() {
        return errorMessage != null && !errorMessage.isBlank();
    }

    public boolean currentStatus(ContactMessageStatus status) {
        return message.status() == status;
    }
}
