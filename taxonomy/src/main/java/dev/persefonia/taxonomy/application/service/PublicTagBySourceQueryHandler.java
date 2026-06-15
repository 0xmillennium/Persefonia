package dev.persefonia.taxonomy.application.service;

import dev.persefonia.taxonomy.application.query.PublicTagBySourceQuery;
import dev.persefonia.taxonomy.application.query.PublicTagLookupResult;
import dev.persefonia.taxonomy.application.query.PublicTagView;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.port.TagRepository;
import java.util.Objects;

public final class PublicTagBySourceQueryHandler {
    private final TagRepository tags;

    public PublicTagBySourceQueryHandler(TagRepository tags) {
        this.tags = Objects.requireNonNull(tags, "tags");
    }

    public PublicTagLookupResult lookup(PublicTagBySourceQuery query) {
        Objects.requireNonNull(query, "query");
        return tags.findById(TagId.from(query.tagId()))
                .filter(tag -> tag.slug().value().equals(query.expectedSlug()))
                .<PublicTagLookupResult>map(tag -> new PublicTagLookupResult.Found(view(tag)))
                .orElseGet(PublicTagLookupResult.NotFound::new);
    }

    private static PublicTagView view(Tag tag) {
        return new PublicTagView(
                tag.id().value(),
                tag.name().value(),
                tag.slug().value(),
                tag.description().value().orElse(""),
                tag.status().name());
    }
}
