package dev.persefonia.communication.application.port;

import dev.persefonia.communication.domain.contact.ContactMessage;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import java.util.Optional;

public interface ContactMessageRepository {
    void save(ContactMessage message);

    Optional<ContactMessage> findById(ContactMessageId id);
}
