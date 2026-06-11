package dev.persefonia.app.contentpublishing.persistence;

import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

interface SpringDataContentRevisionRows extends CrudRepository<ContentRevisionPersistenceEntity, UUID> {
}
