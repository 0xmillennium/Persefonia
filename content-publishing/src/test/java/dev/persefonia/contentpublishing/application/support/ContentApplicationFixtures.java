package dev.persefonia.contentpublishing.application.support;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.application.command.ContentFieldUpdate;
import dev.persefonia.contentpublishing.application.command.UpdateContentDraftCommand;
import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import java.time.Instant;

public final class ContentApplicationFixtures {
    public static final Instant NOW = Instant.parse("2026-06-12T12:00:00Z");
    public static final ContentCommandActor OWNER = new ContentCommandActor(AdminIdentityRef.newId(), true, true);
    public static final ContentCommandActor EDITOR = new ContentCommandActor(AdminIdentityRef.newId(), true, false);

    private ContentApplicationFixtures() {
    }

    public static UpdateContentDraftCommand titleAndRouteUpdate(ContentItem item) {
        return new UpdateContentDraftCommand(
                OWNER,
                item.id(),
                ContentFieldUpdate.set(Slug.of("updated-route")),
                ContentFieldUpdate.set(Title.of("Updated title")),
                ContentFieldUpdate.unchanged(),
                ContentFieldUpdate.unchanged(),
                ContentFieldUpdate.unchanged(),
                ContentFieldUpdate.set(ContentVisibility.UNLISTED),
                NOW);
    }

    public static ContentItem completeDraft() {
        return ContentItemTestFixtures.completeDraft();
    }
}
