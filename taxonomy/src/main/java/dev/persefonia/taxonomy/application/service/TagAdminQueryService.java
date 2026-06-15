package dev.persefonia.taxonomy.application.service;

import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandActor;
import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandAuthorizationPolicy;
import dev.persefonia.taxonomy.application.exception.TagNotFoundException;
import dev.persefonia.taxonomy.application.query.TagEditView;
import dev.persefonia.taxonomy.application.query.TagListItem;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.port.TagRepository;
import java.util.List;
import java.util.Objects;

public final class TagAdminQueryService {
    private final TagRepository tags;
    private final TaxonomyCommandAuthorizationPolicy authorization;

    public TagAdminQueryService(TagRepository tags, TaxonomyCommandAuthorizationPolicy authorization) {
        this.tags = Objects.requireNonNull(tags, "tags");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public List<TagListItem> list(TaxonomyCommandActor actor) {
        authorization.requireOwner(actor, "taxonomy.tag.admin-list");
        return tags.findAllForAdmin().stream().map(TagAdminQueryService::listItem).toList();
    }

    public TagEditView edit(TaxonomyCommandActor actor, TagId id) {
        authorization.requireOwner(actor, "taxonomy.tag.admin-edit");
        return editView(tags.findById(id).orElseThrow(() -> new TagNotFoundException(id)));
    }

    private static TagListItem listItem(Tag tag) {
        return new TagListItem(tag.id(), tag.name().value(), tag.slug().value(), tag.status(), tag.createdAt(), tag.updatedAt());
    }

    private static TagEditView editView(Tag tag) {
        return new TagEditView(
                tag.id(), tag.name().value(), tag.slug().value(), tag.description().value().orElse(""),
                tag.status(), tag.updatedAt());
    }
}
