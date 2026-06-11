package dev.persefonia.webadmin.content;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.application.command.ContentFieldUpdate;
import dev.persefonia.contentpublishing.application.command.CreateContentDraftCommand;
import dev.persefonia.contentpublishing.application.command.UpdateContentDraftCommand;
import dev.persefonia.contentpublishing.application.query.AdminContentEditResult;
import dev.persefonia.contentpublishing.domain.content.AssetId;
import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.OpenGraphDescription;
import dev.persefonia.contentpublishing.domain.content.OpenGraphTitle;
import dev.persefonia.contentpublishing.domain.content.SeoDescription;
import dev.persefonia.contentpublishing.domain.content.SeoTitle;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public final class AdminContentFormMapper {
    public CreateContentDraftCommand toCreate(
            ContentCommandActor actor, AdminContentForm form, Instant requestedAt) {
        return new CreateContentDraftCommand(
                actor,
                ContentType.valueOf(form.getType()),
                ContentVisibility.valueOf(form.getVisibility()),
                ContentLanguage.valueOf(form.getLanguage()),
                requestedAt);
    }

    public UpdateContentDraftCommand toUpdate(
            ContentCommandActor actor, ContentId contentId, AdminContentForm form, Instant requestedAt) {
        return new UpdateContentDraftCommand(
                actor,
                contentId,
                optional(form.getSlug(), Slug::of),
                optional(form.getTitle(), Title::of),
                optional(form.getSummary(), Summary::of),
                optional(form.getMarkdownSource(), MarkdownSource::of),
                ContentFieldUpdate.set(metadata(form)),
                ContentFieldUpdate.set(ContentVisibility.valueOf(form.getVisibility())),
                requestedAt);
    }

    public AdminContentForm from(AdminContentEditResult result) {
        AdminContentForm form = new AdminContentForm();
        form.setType(result.type().name());
        form.setLanguage(result.language().name());
        form.setVisibility(result.visibility().name());
        form.setSlug(result.slug().orElse(""));
        form.setTitle(result.title().orElse(""));
        form.setSummary(result.summary().orElse(""));
        form.setMarkdownSource(result.markdownSource().orElse(""));
        form.setMetaTitle(result.metaTitle().orElse(""));
        form.setMetaDescription(result.metaDescription().orElse(""));
        form.setCanonicalPath(result.canonicalPath().orElse(""));
        form.setOgTitle(result.ogTitle().orElse(""));
        form.setOgDescription(result.ogDescription().orElse(""));
        form.setOgImageAssetId(result.ogImageAssetId().orElse(""));
        return form;
    }

    private static ContentMetadata metadata(AdminContentForm form) {
        return ContentMetadata.of(
                nullable(form.getMetaTitle(), SeoTitle::of),
                nullable(form.getMetaDescription(), SeoDescription::of),
                nullable(form.getCanonicalPath(), CanonicalPath::of),
                nullable(form.getOgTitle(), OpenGraphTitle::of),
                nullable(form.getOgDescription(), OpenGraphDescription::of),
                nullable(form.getOgImageAssetId(), value -> AssetId.from(UUID.fromString(value))));
    }

    private static <T> ContentFieldUpdate<T> optional(String value, Function<String, T> factory) {
        return value.isBlank() ? ContentFieldUpdate.clear() : ContentFieldUpdate.set(factory.apply(value));
    }

    private static <T> T nullable(String value, Function<String, T> factory) {
        return value.isBlank() ? null : factory.apply(value);
    }
}
