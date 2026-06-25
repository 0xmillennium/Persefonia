package dev.persefonia.communication.application.query;

import dev.persefonia.communication.domain.contact.ContactMessageId;
import java.util.Optional;

public interface ContactMessageAdminQueryService {
    ContactMessageAdminListPage list(ContactMessageAdminListRequest request);

    Optional<ContactMessageAdminDetail> findDetail(ContactMessageId id);
}
