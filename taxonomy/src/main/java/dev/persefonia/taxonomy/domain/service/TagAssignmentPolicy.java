package dev.persefonia.taxonomy.domain.service;

import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagStatus;
import java.util.Objects;

public final class TagAssignmentPolicy {
    public boolean isAssignable(Tag tag) {
        return Objects.requireNonNull(tag, "tag").status() == TagStatus.ACTIVE;
    }
}
