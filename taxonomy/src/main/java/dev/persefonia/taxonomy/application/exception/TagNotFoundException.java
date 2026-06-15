package dev.persefonia.taxonomy.application.exception;

import dev.persefonia.taxonomy.domain.model.TagId;

public final class TagNotFoundException extends TaxonomyApplicationException {
    public TagNotFoundException(TagId id) {
        super("Tag not found: " + id.value());
    }
}
