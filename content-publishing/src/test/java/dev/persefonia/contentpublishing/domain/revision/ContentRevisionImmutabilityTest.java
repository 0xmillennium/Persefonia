package dev.persefonia.contentpublishing.domain.revision;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import dev.persefonia.contentpublishing.domain.support.ContentRevisionTestFixtures;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ContentRevisionImmutabilityTest {
    @Test
    void contentRevisionExposesNoMutatorMethods() {
        assertThat(Arrays.stream(ContentRevision.class.getMethods())
                .filter(method -> method.getParameterCount() > 0)
                .map(Method::getName)
                .filter(name -> name.startsWith("set") || name.startsWith("change") || name.startsWith("clear"))
                .toList())
                .isEmpty();
    }

    @Test
    void snapshotMetadataAndChangeNoteAreImmutableValues() {
        ContentRevision revision = ContentRevisionTestFixtures.publishRevision();

        assertThat(revision.metadata().canonicalPath()).isPresent();
        assertThat(revision.changeNote()).contains(ChangeNote.of("Initial publish"));
    }

    @Test
    void revisionIsSeparateFromContentItemStateAfterCreation() {
        ContentItem item = ContentItemTestFixtures.completeDraft();
        ContentRevision revision = ContentRevisionTestFixtures.publishRevision();

        item.changeTitle(dev.persefonia.contentpublishing.domain.content.Title.of("Changed after revision"),
                ContentItemTestFixtures.EDITED_AT.plusSeconds(50));

        assertThat(revision.title()).isEqualTo(ContentRevisionTestFixtures.title());
        assertThat(item.title()).contains(dev.persefonia.contentpublishing.domain.content.Title.of("Changed after revision"));
    }
}
