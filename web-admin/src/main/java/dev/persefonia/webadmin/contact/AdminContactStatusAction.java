package dev.persefonia.webadmin.contact;

import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import java.util.List;
import java.util.Objects;

public record AdminContactStatusAction(ContactMessageStatus status, String label, String actionPath) {
    public AdminContactStatusAction {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(actionPath, "actionPath");
    }

    public static List<AdminContactStatusAction> forMessage(ContactMessageId id, ContactMessageStatus currentStatus) {
        return List.of(
                        action(id, ContactMessageStatus.READ, "Mark as read", "read"),
                        action(id, ContactMessageStatus.REPLIED, "Mark as replied", "replied"),
                        action(id, ContactMessageStatus.SPAM, "Mark as spam", "spam"),
                        action(id, ContactMessageStatus.ARCHIVED, "Archive", "archive"))
                .stream()
                .filter(action -> action.status() != currentStatus)
                .toList();
    }

    private static AdminContactStatusAction action(
            ContactMessageId id,
            ContactMessageStatus status,
            String label,
            String pathSuffix) {
        return new AdminContactStatusAction(
                status,
                label,
                "/admin/contact/" + id.value() + "/" + pathSuffix);
    }
}
