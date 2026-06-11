package dev.persefonia.app.webadmin.content;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdminContentFormMapperTest {
    private final dev.persefonia.webadmin.content.AdminContentFormMapper mapper =
            new dev.persefonia.webadmin.content.AdminContentFormMapper();
    private final ContentCommandActor owner = new ContentCommandActor(AdminIdentityRef.newId(), true, true);

    @Test
    void mapsCreateAndUpdateCommandsWithBlankOptionalValuesCleared() {
        var form = form();
        var create = mapper.toCreate(owner, form, Instant.EPOCH);
        var update = mapper.toUpdate(owner, ContentId.newId(), form, Instant.EPOCH);

        assertThat(create.type().name()).isEqualTo("ARTICLE");
        assertThat(create.language().name()).isEqualTo("EN");
        assertThat(update.slug().specified()).isTrue();
        assertThat(update.slug().value()).isNull();
        assertThat(update.metadata().value().canonicalPath()).isEmpty();
    }

    @Test
    void validatorRejectsInvalidImageAssetUuid() {
        var form = form();
        form.setOgImageAssetId("not-a-uuid");

        assertThat(new dev.persefonia.webadmin.content.AdminContentFormValidator().validate(form, false))
                .anySatisfy(error -> {
                    assertThat(error.field()).isEqualTo("ogImageAssetId");
                    assertThat(error.message()).contains("UUID");
                });
    }

    private static dev.persefonia.webadmin.content.AdminContentForm form() {
        var form = new dev.persefonia.webadmin.content.AdminContentForm();
        form.setType("ARTICLE");
        form.setLanguage("EN");
        form.setVisibility("PRIVATE");
        return form;
    }
}
