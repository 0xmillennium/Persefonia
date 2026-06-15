package dev.persefonia.taxonomy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.taxonomy.domain.model.NormalizedTagName;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagDescription;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagName;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import dev.persefonia.taxonomy.domain.service.TagAssignmentPolicy;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TagAssignmentPolicyTest {
    @Test
    void onlyActiveTagsAreAssignable() {
        Tag tag = Tag.create(
                TagId.newId(), TagName.of("Java"), NormalizedTagName.ofCanonical("java"),
                TagSlug.ofCanonical("java"), TagDescription.empty(), Instant.EPOCH);
        TagAssignmentPolicy policy = new TagAssignmentPolicy();

        assertThat(policy.isAssignable(tag)).isTrue();
        tag.archive(Instant.EPOCH.plusSeconds(1));
        assertThat(policy.isAssignable(tag)).isFalse();
    }
}
