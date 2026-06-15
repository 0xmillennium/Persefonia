package dev.persefonia.app.taxonomy.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(schema = "taxonomy", value = "tags")
record TagPersistenceEntity(
        @Id UUID id,
        String name,
        @Column("normalized_name") String normalizedName,
        String slug,
        String description,
        String status,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt,
        @Version Long version) {
}
