package dev.persefonia.app.contentpublishing.persistence;

import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

interface SpringDataContentItemRows extends CrudRepository<ContentItemPersistenceEntity, UUID> {
}
