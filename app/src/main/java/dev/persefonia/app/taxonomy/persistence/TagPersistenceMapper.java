package dev.persefonia.app.taxonomy.persistence;

import dev.persefonia.taxonomy.domain.model.NormalizedTagName;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagDescription;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagName;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import dev.persefonia.taxonomy.domain.model.TagStatus;

final class TagPersistenceMapper {
    TagPersistenceEntity toEntity(Tag tag, Long jdbcVersion) {
        return new TagPersistenceEntity(
                tag.id().value(),
                tag.name().value(),
                tag.normalizedName().value(),
                tag.slug().value(),
                tag.description().value().orElse(null),
                tag.status().name(),
                tag.createdAt(),
                tag.updatedAt(),
                jdbcVersion);
    }

    Tag toDomain(TagPersistenceEntity entity) {
        return Tag.rehydrate(
                TagId.from(entity.id()),
                TagName.of(entity.name()),
                NormalizedTagName.ofCanonical(entity.normalizedName()),
                TagSlug.ofCanonical(entity.slug()),
                TagDescription.ofNullable(entity.description()),
                TagStatus.valueOf(entity.status()),
                entity.createdAt(),
                entity.updatedAt(),
                entity.version() == null ? 0 : entity.version());
    }
}
